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

/**
 * Immutable snapshot stored on a floating-backpack ItemStack as a data component.
 * List length is the tier capacity (at most {@link BackpackInventory#MAX_SIZE}).
 */
public record BackpackContents(List<ItemStack> items, ItemStack cursor) {
    public static final Codec<BackpackContents> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ItemStack.OPTIONAL_CODEC
                            .listOf()
                            .fieldOf("items")
                            .forGetter(BackpackContents::items),
                    ItemStack.OPTIONAL_CODEC
                            .optionalFieldOf("cursor", ItemStack.EMPTY)
                            .forGetter(c -> c.cursor().isEmpty() ? ItemStack.EMPTY : c.cursor()))
            .apply(instance, BackpackContents::normalize));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackContents> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.MAX_SIZE)),
                    BackpackContents::items,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackContents::cursor,
                    BackpackContents::normalize);

    public static BackpackContents empty(int capacity) {
        return fit(List.of(), ItemStack.EMPTY, capacity);
    }

    /** Copies stacks and clamps the list to {@link BackpackInventory#MAX_SIZE} without padding. */
    public static BackpackContents normalize(List<ItemStack> items, ItemStack cursor) {
        int size = items == null ? 0 : Math.min(items.size(), BackpackInventory.MAX_SIZE);
        NonNullList<ItemStack> slots = NonNullList.create();
        for (int i = 0; i < size; i++) {
            ItemStack stack = items.get(i);
            slots.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        ItemStack cursorCopy = cursor == null || cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy();
        return new BackpackContents(slots, cursorCopy);
    }

    public static BackpackContents fit(List<ItemStack> items, ItemStack cursor, int capacity) {
        int size = Math.max(0, Math.min(BackpackInventory.MAX_SIZE, capacity));
        NonNullList<ItemStack> slots = NonNullList.withSize(size, ItemStack.EMPTY);
        if (items != null) {
            for (int i = 0; i < Math.min(items.size(), size); i++) {
                ItemStack stack = items.get(i);
                slots.set(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }
        ItemStack cursorCopy = cursor == null || cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy();
        return new BackpackContents(slots, cursorCopy);
    }

    public BackpackContents fit(int capacity) {
        return fit(items, cursor, capacity);
    }

    public BackpackContents copy() {
        return normalize(items, cursor);
    }
}
