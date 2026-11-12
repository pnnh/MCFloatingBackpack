package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionRowsCodecs;
import pnnh.mcfloatingbackpack.inventory.extension.RowSet;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklistCodecs;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.inventory.trash.TrashContents;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.tank.FluidStore;
import pnnh.mcfloatingbackpack.tank.FluidStoreCodecs;

/**
 * Immutable snapshot stored on a floating-backpack ItemStack as a data component.
 * The main list length is the tier capacity (at most {@link BackpackInventory#MAX_TIER_CAPACITY}); the three tool
 * areas (016) are kept apart in {@link ToolAreaContents}, so nothing that walks the main slots can reach them.
 * The tags (018) belong to item categories, not to stacks, so they are kept apart as well, and so is what the trash
 * can (021) holds: it belongs to the backpack, not to the trash can item. The same goes for the pickup blacklist (024).
 *
 * <p>035: the rows of the category extensions are kept apart too, in {@code rows}: the main list stays exactly as long as the
 * tier capacity, so everything that reads the capacity from it (upgrade, snapshots) is untouched. The visible rows' cells
 * join the main area only in the working copy ({@link BackpackInventory}).
 *
 * <p>048: what the backpack holds of each liquid ({@code fluids}) is kept apart too; it belongs to the backpack, not to a tank item. On disk
 * it is an optional field (an older backpack has none and reads as empty); on the wire it comes after the rows.
 *
 * <p>The wire codec is written by hand: it had used up the six fields a {@code StreamCodec.composite} can take. The first six
 * fields go out in the order they always did, the rows and then the liquids (048) last.
 */
public record BackpackContents(
        List<ItemStack> items,
        ItemStack cursor,
        ToolAreaContents tools,
        ItemTags tags,
        TrashContents trash,
        PickupBlacklist pickupBlacklist,
        RowSet<ItemStack> rows,
        FluidStore fluids,
        int dataRevision) {
    public static final Codec<BackpackContents> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ItemStack.OPTIONAL_CODEC
                            .listOf()
                            .fieldOf("items")
                            .forGetter(BackpackContents::items),
                    ItemStack.OPTIONAL_CODEC
                            .optionalFieldOf("cursor", ItemStack.EMPTY)
                            .forGetter(c -> c.cursor().isEmpty() ? ItemStack.EMPTY : c.cursor()),
                    ToolAreaContents.CODEC
                            .optionalFieldOf("tools", ToolAreaContents.EMPTY)
                            .forGetter(BackpackContents::tools),
                    ItemTags.CODEC
                            .optionalFieldOf("tags", ItemTags.EMPTY)
                            .forGetter(BackpackContents::tags),
                    TrashContents.CODEC
                            .optionalFieldOf("trash", TrashContents.EMPTY)
                            .forGetter(BackpackContents::trash),
                    PickupBlacklistCodecs.CODEC
                            .optionalFieldOf("pickup_blacklist", PickupBlacklist.EMPTY)
                            .forGetter(BackpackContents::pickupBlacklist),
                    ExtensionRowsCodecs.CODEC
                            .optionalFieldOf("extension_rows", RowSet.<ItemStack>empty())
                            .forGetter(BackpackContents::rows),
                    // 048: the liquids the backpack holds; an older version leaves the field out (reads as empty)
                    FluidStoreCodecs.CODEC
                            .optionalFieldOf("fluids", FluidStore.EMPTY)
                            .forGetter(BackpackContents::fluids),
                    // 040: the revision of the stored data this backpack was last saved with; an older version leaves the field out
                    // (it ignores what it does not know), so it reads as 0. It is on disk only: the wire codec below does not carry it.
                    Codec.INT
                            .optionalFieldOf("data_revision", 0)
                            .forGetter(BackpackContents::dataRevision))
            .apply(instance, BackpackContents::normalize));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> ITEMS_STREAM_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.MAX_TIER_CAPACITY));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackContents> STREAM_CODEC = StreamCodec.of(
            (buf, contents) -> {
                ITEMS_STREAM_CODEC.encode(buf, contents.items());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, contents.cursor());
                ToolAreaContents.STREAM_CODEC.encode(buf, contents.tools());
                ItemTags.STREAM_CODEC.encode(buf, contents.tags());
                TrashContents.STREAM_CODEC.encode(buf, contents.trash());
                PickupBlacklistCodecs.STREAM_CODEC.encode(buf, contents.pickupBlacklist());
                ExtensionRowsCodecs.STREAM_CODEC.encode(buf, contents.rows());
                FluidStoreCodecs.STREAM_CODEC.encode(buf, contents.fluids());
            },
            buf -> {
                List<ItemStack> items = ITEMS_STREAM_CODEC.decode(buf);
                ItemStack cursor = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                ToolAreaContents tools = ToolAreaContents.STREAM_CODEC.decode(buf);
                ItemTags tags = ItemTags.STREAM_CODEC.decode(buf);
                TrashContents trash = TrashContents.STREAM_CODEC.decode(buf);
                PickupBlacklist pickupBlacklist = PickupBlacklistCodecs.STREAM_CODEC.decode(buf);
                RowSet<ItemStack> rows = ExtensionRowsCodecs.STREAM_CODEC.decode(buf);
                FluidStore fluids = FluidStoreCodecs.STREAM_CODEC.decode(buf);
                return normalize(items, cursor, tools, tags, trash, pickupBlacklist, rows, fluids, 0);
            });

    public static BackpackContents empty(int capacity) {
        return fit(List.of(), ItemStack.EMPTY, ToolAreaContents.EMPTY, ItemTags.EMPTY, TrashContents.EMPTY, PickupBlacklist.EMPTY, RowSet.empty(), FluidStore.EMPTY, capacity);
    }

    /** Copies stacks and clamps the list to {@link BackpackInventory#MAX_TIER_CAPACITY} without padding; no tool areas, no tags, no trash, no blacklist, no rows. */
    public static BackpackContents normalize(List<ItemStack> items, ItemStack cursor) {
        return normalize(
                items, cursor, ToolAreaContents.EMPTY, ItemTags.EMPTY, TrashContents.EMPTY, PickupBlacklist.EMPTY, RowSet.empty());
    }

    /** Copies stacks and clamps the lists without padding; the data revision is 0 (no stamp): saving stamps it. */
    public static BackpackContents normalize(
            List<ItemStack> items,
            ItemStack cursor,
            ToolAreaContents tools,
            ItemTags tags,
            TrashContents trash,
            PickupBlacklist pickupBlacklist,
            RowSet<ItemStack> rows) {
        return normalize(items, cursor, tools, tags, trash, pickupBlacklist, rows, FluidStore.EMPTY, 0);
    }

    /** Copies stacks and clamps the lists without padding; a negative revision reads as 0. */
    public static BackpackContents normalize(
            List<ItemStack> items,
            ItemStack cursor,
            ToolAreaContents tools,
            ItemTags tags,
            TrashContents trash,
            PickupBlacklist pickupBlacklist,
            RowSet<ItemStack> rows,
            FluidStore fluids,
            int dataRevision) {
        int size = items == null ? 0 : Math.min(items.size(), BackpackInventory.MAX_TIER_CAPACITY);
        NonNullList<ItemStack> slots = NonNullList.create();
        for (int i = 0; i < size; i++) {
            ItemStack stack = items.get(i);
            slots.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        ItemStack cursorCopy = cursor == null || cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy();
        ToolAreaContents toolsCopy = tools == null
                ? ToolAreaContents.EMPTY
                : ToolAreaContents.normalize(tools.left(), tools.right(), tools.bottom());
        return new BackpackContents(
                slots,
                cursorCopy,
                toolsCopy,
                tags == null ? ItemTags.EMPTY : tags,
                trash == null ? TrashContents.EMPTY : new TrashContents(trash.groups()),
                pickupBlacklist == null ? PickupBlacklist.EMPTY : pickupBlacklist,
                rows == null ? RowSet.empty() : rows,
                fluids == null ? FluidStore.EMPTY : fluids,
                pnnh.mcfloatingbackpack.inventory.revision.DataRevision.readStored(dataRevision));
    }

    /** Main slots fitted to {@code capacity}, tool areas empty (padded to their capacities), no tags, no trash, no blacklist, no rows. */
    public static BackpackContents fit(List<ItemStack> items, ItemStack cursor, int capacity) {
        return fit(
                items, cursor, ToolAreaContents.EMPTY, ItemTags.EMPTY, TrashContents.EMPTY, PickupBlacklist.EMPTY, RowSet.empty(), FluidStore.EMPTY, capacity);
    }

    /**
     * Main slots cut or padded to {@code capacity}; each tool area resized to its own capacity without
     * dropping items ({@link ToolAreaCapacity#fit}).
     */
    public static BackpackContents fit(
            List<ItemStack> items,
            ItemStack cursor,
            ToolAreaContents tools,
            ItemTags tags,
            TrashContents trash,
            PickupBlacklist pickupBlacklist,
            RowSet<ItemStack> rows,
            FluidStore fluids,
            int capacity) {
        int size = Math.max(0, Math.min(BackpackInventory.MAX_TIER_CAPACITY, capacity));
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        if (items != null) {
            for (int i = 0; i < Math.min(items.size(), size); i++) {
                ItemStack stack = items.get(i);
                slots.set(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }
        ItemStack cursorCopy = cursor == null || cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy();
        ToolAreaContents fittedTools = (tools == null ? ToolAreaContents.EMPTY : tools).fit(size);
        return new BackpackContents(
                slots,
                cursorCopy,
                fittedTools,
                tags == null ? ItemTags.EMPTY : tags,
                trash == null ? TrashContents.EMPTY : new TrashContents(trash.groups()),
                pickupBlacklist == null ? PickupBlacklist.EMPTY : pickupBlacklist,
                rows == null ? RowSet.empty() : rows,
                fluids == null ? FluidStore.EMPTY : fluids,
                0);
    }

    /** The main list fitted to the tier capacity; the rows do not depend on the capacity and stay as they are; the revision stays too. */
    public BackpackContents fit(int capacity) {
        return fit(items, cursor, tools, tags, trash, pickupBlacklist, rows, fluids, capacity).withDataRevision(dataRevision);
    }

    public BackpackContents copy() {
        return normalize(items, cursor, tools, tags, trash, pickupBlacklist, rows, fluids, dataRevision);
    }

    /** 040: the same contents stamped with another data revision. */
    public BackpackContents withDataRevision(int revision) {
        return normalize(items, cursor, tools, tags, trash, pickupBlacklist, rows, fluids, revision);
    }

    /**
     * 024: whether the left (input) area holds an auto pickup, anywhere in its whole list. Reads the stored record, so the
     * server's pickup check does not have to build a working copy of the backpack for every item a player touches.
     */
    public boolean hasAutoPickup() {
        for (ItemStack stack : tools.left()) {
            if (stack.is(ModItems.AUTO_PICKUP.get())) {
                return true;
            }
        }
        return false;
    }
}
