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
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.SIZE)),
                    BackpackContents::items,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackContents::cursor,
                    BackpackContents::normalize);

    public static BackpackContents empty() {
        return new BackpackContents(
                NonNullList.withSize(BackpackInventory.SIZE, ItemStack.EMPTY), ItemStack.EMPTY);
    }

    public static BackpackContents normalize(List<ItemStack> items, ItemStack cursor) {
        NonNullList<ItemStack> slots = NonNullList.withSize(BackpackInventory.SIZE, ItemStack.EMPTY);
        if (items != null) {
            for (int i = 0; i < Math.min(items.size(), BackpackInventory.SIZE); i++) {
                ItemStack stack = items.get(i);
                slots.set(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
        }
        ItemStack cursorCopy =
                cursor == null || cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy();
        return new BackpackContents(slots, cursorCopy);
    }

    public BackpackContents copy() {
        List<ItemStack> copied = new ArrayList<>(BackpackInventory.SIZE);
        for (int i = 0; i < BackpackInventory.SIZE; i++) {
            ItemStack stack = i < items.size() ? items.get(i) : ItemStack.EMPTY;
            copied.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return new BackpackContents(
                copied, cursor == null || cursor.isEmpty() ? ItemStack.EMPTY : cursor.copy());
    }
}
