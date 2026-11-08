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
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklistCodecs;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.inventory.trash.TrashContents;
import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * Immutable snapshot stored on a floating-backpack ItemStack as a data component.
 * The main list length is the tier capacity (at most {@link BackpackInventory#MAX_SIZE}); the three tool
 * areas (016) are kept apart in {@link ToolAreaContents}, so nothing that walks the main slots can reach them.
 * The tags (018) belong to item categories, not to stacks, so they are kept apart as well, and so is what the trash
 * can (021) holds: it belongs to the backpack, not to the trash can item. The same goes for the pickup blacklist (024).
 *
 * <p>The wire codec is a {@code StreamCodec.composite} of six fields, which is the most it can take: a further field means
 * writing the codec by hand (or folding a few small fields into one sub-record) first.
 */
public record BackpackContents(
        List<ItemStack> items,
        ItemStack cursor,
        ToolAreaContents tools,
        ItemTags tags,
        TrashContents trash,
        PickupBlacklist pickupBlacklist) {
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
                            .forGetter(BackpackContents::pickupBlacklist))
            .apply(instance, BackpackContents::normalize));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackContents> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.MAX_SIZE)),
                    BackpackContents::items,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackContents::cursor,
                    ToolAreaContents.STREAM_CODEC,
                    BackpackContents::tools,
                    ItemTags.STREAM_CODEC,
                    BackpackContents::tags,
                    TrashContents.STREAM_CODEC,
                    BackpackContents::trash,
                    PickupBlacklistCodecs.STREAM_CODEC,
                    BackpackContents::pickupBlacklist,
                    BackpackContents::normalize);

    public static BackpackContents empty(int capacity) {
        return fit(List.of(), ItemStack.EMPTY, ToolAreaContents.EMPTY, ItemTags.EMPTY, TrashContents.EMPTY, PickupBlacklist.EMPTY, capacity);
    }

    /** Copies stacks and clamps the list to {@link BackpackInventory#MAX_SIZE} without padding; no tool areas, no tags, no trash, no blacklist. */
    public static BackpackContents normalize(List<ItemStack> items, ItemStack cursor) {
        return normalize(items, cursor, ToolAreaContents.EMPTY, ItemTags.EMPTY, TrashContents.EMPTY, PickupBlacklist.EMPTY);
    }

    /** Copies stacks and clamps the lists without padding. */
    public static BackpackContents normalize(
            List<ItemStack> items,
            ItemStack cursor,
            ToolAreaContents tools,
            ItemTags tags,
            TrashContents trash,
            PickupBlacklist pickupBlacklist) {
        int size = items == null ? 0 : Math.min(items.size(), BackpackInventory.MAX_SIZE);
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
                pickupBlacklist == null ? PickupBlacklist.EMPTY : pickupBlacklist);
    }

    /** Main slots fitted to {@code capacity}, tool areas empty (padded to their capacities), no tags, no trash, no blacklist. */
    public static BackpackContents fit(List<ItemStack> items, ItemStack cursor, int capacity) {
        return fit(items, cursor, ToolAreaContents.EMPTY, ItemTags.EMPTY, TrashContents.EMPTY, PickupBlacklist.EMPTY, capacity);
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
            int capacity) {
        int size = Math.max(0, Math.min(BackpackInventory.MAX_SIZE, capacity));
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
                pickupBlacklist == null ? PickupBlacklist.EMPTY : pickupBlacklist);
    }

    public BackpackContents fit(int capacity) {
        return fit(items, cursor, tools, tags, trash, pickupBlacklist, capacity);
    }

    public BackpackContents copy() {
        return normalize(items, cursor, tools, tags, trash, pickupBlacklist);
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
