package pnnh.mcfloatingbackpack.snapshot;

import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.LostCells;

/**
 * 057: what players are told about the mod's version changing and about stacks that could not be read. The snapshot service, when it
 * starts, says whether the version changed ({@link Pending}) and gives this class the folder's state; a player who logs in and is not
 * yet in the state's list of those told gets one line and is put in the list (and the state written), so everybody is told at least once
 * for each change, also if they were away when it happened. The number of stacks left empty while a player's data was read (see
 * {@link LostCells}) is worked out when their data file has been read (the game may read it on another thread than the server's main one,
 * so the backpacks in the file are decoded once more, here, counting on this thread) and kept until they have logged in.
 * Everything else is for the main thread; {@link #onPlayerDataRead} may run on any thread.
 */
public final class SnapshotNotices {
    /** How the notice is worded: a newer version, an older one, a change that cannot be told either way, or snapshots being off. */
    public enum Direction { UPDATED, DOWNGRADED, CHANGED, DISABLED }

    /** A change of the mod's version that players have to be told about. */
    public record Pending(long changeId, String from, String to, int pinned, Direction direction) {
    }

    private static SnapshotStore store;
    private static SnapshotState state;
    private static Pending pending;
    private static final Map<UUID, Integer> LOST = new ConcurrentHashMap<>();

    private SnapshotNotices() {
    }

    /** The snapshot service has started: what it found. {@code pending} is null when there is nothing to tell. */
    static void begin(SnapshotStore folder, SnapshotState current, Pending found) {
        store = folder;
        state = current;
        pending = found;
        LOST.clear();
    }

    /** The server stops. */
    static void end() {
        store = null;
        state = null;
        pending = null;
        LOST.clear();
    }

    /** The words for a change from one version to another. */
    static Direction directionOf(String from, String to, boolean enabled) {
        if (!enabled) {
            return Direction.DISABLED;
        }
        Integer order = VersionOrder.compare(from, to);
        if (order == null || order == 0) {
            return Direction.CHANGED;
        }
        return order < 0 ? Direction.UPDATED : Direction.DOWNGRADED;
    }

    /**
     * A player's data has just been read, from {@code file} ({@code <uuid>.dat}): counts the stacks of the backpacks in their inventory
     * that cannot be read (an item that no longer exists) by decoding those backpacks again on this thread, where nothing else counts.
     * A file that cannot be read, or a player with no backpack, counts nothing.
     */
    public static void onPlayerDataRead(UUID player, File file, HolderLookup.Provider registries) {
        int lost = 0;
        try {
            if (file.isFile()) {
                CompoundTag root = NbtIo.readCompressed(file.toPath(), NbtAccounter.unlimitedHeap());
                var ops = registries.createSerializationContext(NbtOps.INSTANCE);
                LostCells.reset();
                for (Tag entry : root.getList("Inventory", Tag.TAG_COMPOUND)) {
                    Tag contents = ((CompoundTag) entry).getCompound("components").get("mcfloatingbackpack:backpack_contents");
                    if (contents != null) {
                        BackpackContents.CODEC.parse(ops, contents).resultOrPartial(error -> { });
                    }
                }
                lost = LostCells.take();
            }
        } catch (Exception e) {
            MCFloatingBackpack.LOGGER.debug("Could not count what could not be read of the backpacks in {}: {}", file, e.toString());
        }
        LOST.put(player, lost);
    }

    public static void onLogout(UUID player) {
        LOST.remove(player);
    }

    public static void onLogin(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (pending != null && state != null && store != null && !state.notified().contains(uuid)) {
            say(player, textOf(pending));
            state = state.withNotified(uuid);
            store.writeState(state);
        }
        Integer lost = LOST.remove(uuid);
        if (lost != null && lost > 0) {
            say(player, Component.translatable("message.mcfloatingbackpack.snapshot.lost", lost));
        }
    }

    static Component textOf(Pending notice) {
        String key = "message.mcfloatingbackpack.snapshot." + switch (notice.direction()) {
            case UPDATED -> "upgraded";
            case DOWNGRADED -> "downgraded";
            case CHANGED -> "changed";
            case DISABLED -> "disabled";
        };
        if (notice.direction() == Direction.DISABLED) {
            return Component.translatable(key, notice.from(), notice.to());
        }
        return Component.translatable(key, notice.from(), notice.to(), notice.pinned());
    }

    private static void say(ServerPlayer player, Component text) {
        player.sendSystemMessage(Component.translatable("message.mcfloatingbackpack.prefix")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(text.copy().withStyle(ChatFormatting.YELLOW)));
    }
}
