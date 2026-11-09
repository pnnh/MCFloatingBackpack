package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.netty.buffer.Unpooled;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Filter;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Group;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Page;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;

/**
 * 031: what can be done with the snapshots, for whoever asks: the commands (030) and the window (031) both call these, so
 * the two cannot disagree about who sees what or what a restore gives. Nothing here writes a message: results come back as
 * data (a {@link ResultCode} or a value) and each caller says it in its own way. Server main thread only.
 *
 * <p>Every method that reads the files writes what is waiting first ({@link SnapshotService#flushAndWait}), so what is
 * read is the latest.
 */
public final class SnapshotOps {
    /** The player (or console) asking: whether they have permission level 2 and who they are ({@code null} for the console). */
    public record Requester(boolean op, UUID self) {
        public static Requester of(ServerPlayer player) {
            return new Requester(player.hasPermissions(SnapshotAccess.OP_LEVEL), player.getUUID());
        }
    }

    /** A preview of one snapshot: its items, or why there is none. */
    public record PreviewResult(ResultCode failure, int capacity, int slots, List<ItemStack> items) {
        public static PreviewResult failed(ResultCode code) {
            return new PreviewResult(code, 0, 0, List.of());
        }

        public boolean ok() {
            return failure == null;
        }
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> ITEMS_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(SnapshotRequestRules.MAX_PREVIEW_SLOTS));

    private SnapshotOps() {
    }

    // ---------------------------------------------------------------- looking

    /** The backpacks the requester may see (everybody's for permission level 2, their own otherwise). */
    public static List<Group> visibleGroups(SnapshotService service, Requester who) {
        service.flushAndWait();
        return SnapshotAccess.visible(service.store().groups(), who.op(), who.self());
    }

    public static Optional<Group> find(List<Group> visible, UUID backpack) {
        return visible.stream().filter(group -> group.uuid().equals(backpack)).findFirst();
    }

    /**
     * One page of the list as the window asks for it: {@code scope} (anyone without permission level 2 gets their own
     * whatever they ask), and for permission level 2 an owner's name and an inactive-days filter.
     *
     * @param visible what {@link #visibleGroups} returned
     * @param ownerName a player name (any case), or empty for no filter
     * @param inactiveDays -1 for no filter
     */
    public static Page page(
            List<Group> visible,
            Requester who,
            Scope scope,
            String ownerName,
            int inactiveDays,
            int pageNumber,
            int pageSize,
            long now) {
        Scope effective = SnapshotAccess.effectiveScope(who.op(), scope);
        boolean filter = SnapshotAccess.mayFilter(who.op());
        UUID owner = effective == Scope.MINE ? who.self() : null;
        List<Group> chosen = new ArrayList<>();
        if (effective == Scope.MINE && owner == null) {
            // the console has no backpacks of its own
            return SnapshotListing.page(chosen, pageNumber, pageSize);
        }
        for (Group group : SnapshotListing.select(visible, new Filter(owner, filter ? inactiveDays : -1), now)) {
            if (filter && !ownerName.isEmpty() && !group.newest().ownerName().equalsIgnoreCase(ownerName)) {
                continue;
            }
            chosen.add(group);
        }
        return SnapshotListing.page(SnapshotListing.newestFirst(chosen), pageNumber, pageSize);
    }

    /** One backpack's versions, newest first; empty when it is not one the requester may see (looks like it does not exist). */
    public static List<SnapshotMeta> versions(SnapshotService service, Requester who, UUID backpack) {
        Optional<Group> group = find(visibleGroups(service, who), backpack);
        return group.isEmpty() ? List.of() : service.store().versions(backpack);
    }

    /** The main-area items of one snapshot, if the requester may see it and it can be read and sent. */
    public static PreviewResult preview(
            MinecraftServer server, SnapshotService service, Requester who, UUID backpack, long time) {
        List<SnapshotMeta> versions = versions(service, who, backpack);
        SnapshotMeta meta = versions.stream().filter(v -> v.time() == time).findFirst().orElse(null);
        if (meta == null) {
            return PreviewResult.failed(ResultCode.NOT_FOUND);
        }
        BackpackContents contents = readContents(server, service, meta);
        if (contents == null) {
            return PreviewResult.failed(ResultCode.UNREADABLE);
        }
        List<ItemStack> items = contents.items().size() > SnapshotRequestRules.MAX_PREVIEW_SLOTS
                ? contents.items().subList(0, SnapshotRequestRules.MAX_PREVIEW_SLOTS)
                : contents.items();
        RegistryFriendlyByteBuf measure = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess());
        try {
            ITEMS_CODEC.encode(measure, items);
            if (measure.readableBytes() > SnapshotRequestRules.MAX_PREVIEW_BYTES) {
                return PreviewResult.failed(ResultCode.TOO_LARGE);
            }
        } catch (RuntimeException e) {
            MCFloatingBackpack.LOGGER.warn("Could not encode the preview of snapshot {}/{}: {}", backpack, time, e.toString());
            return PreviewResult.failed(ResultCode.UNREADABLE);
        } finally {
            measure.release();
        }
        int slots = (int) items.stream().filter(stack -> !stack.isEmpty()).count();
        return new PreviewResult(null, meta.capacity(), slots, new ArrayList<>(items));
    }

    // ---------------------------------------------------------------- changing

    /**
     * Gives {@code target} a new backpack with this snapshot's contents. The new backpack has no identity (it gets its own the
     * first time it is used, so it never shares the original's), and nothing that exists is changed.
     *
     * @return {@link ResultCode#RESTORED}, {@link ResultCode#NO_TIER} or {@link ResultCode#UNREADABLE}
     */
    public static ResultCode restore(MinecraftServer server, SnapshotService service, SnapshotMeta chosen, ServerPlayer target) {
        BackpackTier tier = BackpackTier.byCapacity(chosen.capacity());
        Item item = tier == null ? null : itemOf(tier);
        if (item == null) {
            return ResultCode.NO_TIER;
        }
        BackpackContents contents = readContents(server, service, chosen);
        if (contents == null) {
            return ResultCode.UNREADABLE;
        }
        ItemStack stack = new ItemStack(item);
        stack.set(ModDataComponents.BACKPACK_CONTENTS.get(), contents);
        target.getInventory().placeItemBackInInventory(stack);
        return ResultCode.RESTORED;
    }

    private static BackpackContents readContents(MinecraftServer server, SnapshotService service, SnapshotMeta meta) {
        Optional<Tag> tag = service.store().readContents(meta.uuid(), meta.time());
        return tag.flatMap(t -> BackpackContents.CODEC
                        .parse(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), t)
                        .resultOrPartial(error -> MCFloatingBackpack.LOGGER.warn(
                                "Could not decode the snapshot {}/{}: {}", meta.uuid(), meta.time(), error)))
                .orElse(null);
    }

    private static Item itemOf(BackpackTier tier) {
        return switch (tier) {
            case LEATHER -> ModItems.LEATHER_FLOATING_BACKPACK.get();
            case IRON -> ModItems.IRON_FLOATING_BACKPACK.get();
            case GOLD -> ModItems.GOLD_FLOATING_BACKPACK.get();
            case DIAMOND -> ModItems.DIAMOND_FLOATING_BACKPACK.get();
            case NETHERITE -> ModItems.NETHERITE_FLOATING_BACKPACK.get();
        };
    }

    /** Deletes one snapshot file; the backpack's next snapshot is written even if its contents are the same. */
    public static boolean deleteOne(SnapshotService service, UUID backpack, long time) {
        boolean deleted = service.store().delete(backpack, time);
        service.forget(backpack);
        return deleted;
    }

    /** Deletes all of a backpack's snapshots. @return how many files went */
    public static int deleteAll(SnapshotService service, UUID backpack) {
        int deleted = service.store().deleteAll(backpack);
        service.forget(backpack);
        return deleted;
    }

    /** The backpacks whose newest snapshot is older than {@code days} days (0 = all). The preview and the real thing use this. */
    public static List<Group> trimCandidates(SnapshotService service, int days) {
        service.flushAndWait();
        return SnapshotListing.trimCandidates(service.store().groups(), System.currentTimeMillis(), days);
    }

    /** How many backpacks have snapshots at all. */
    public static int backpackCount(SnapshotService service) {
        return service.store().groups().size();
    }

    /** Deletes the snapshots of these backpacks. @return how many files went */
    public static int trimDelete(SnapshotService service, List<Group> candidates) {
        int deleted = 0;
        for (Group group : candidates) {
            deleted += deleteAll(service, group.uuid());
        }
        return deleted;
    }
}
