package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * The stacks of the three tool areas (016) next to the main slots in {@link BackpackContents}. A list that
 * is missing (a backpack saved before 016) reads as empty and is padded to its capacity by
 * {@link BackpackContents#fit}.
 */
public record ToolAreaContents(List<ItemStack> left, List<ItemStack> right, List<ItemStack> bottom) {
    /** Upper bound for one list on the wire and on disk; far above any tier's 12 / 12 / 11. */
    public static final int MAX_TOOL_SLOTS = 64;

    public static final ToolAreaContents EMPTY = new ToolAreaContents(List.of(), List.of(), List.of());

    public static final Codec<ToolAreaContents> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("left", List.of()).forGetter(ToolAreaContents::left),
                    ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("right", List.of()).forGetter(ToolAreaContents::right),
                    ItemStack.OPTIONAL_CODEC.listOf().optionalFieldOf("bottom", List.of()).forGetter(ToolAreaContents::bottom))
            .apply(instance, ToolAreaContents::normalize));

    /** Each list is capped at {@link #MAX_TOOL_SLOTS}; the vanilla list codec throws a DecoderException beyond that. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ToolAreaContents> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOOL_SLOTS)),
                    ToolAreaContents::left,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOOL_SLOTS)),
                    ToolAreaContents::right,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(MAX_TOOL_SLOTS)),
                    ToolAreaContents::bottom,
                    ToolAreaContents::normalize);

    /** Copies the stacks (empty ones become {@link ItemStack#EMPTY}) and clamps each list to {@link #MAX_TOOL_SLOTS}. */
    public static ToolAreaContents normalize(List<ItemStack> left, List<ItemStack> right, List<ItemStack> bottom) {
        return new ToolAreaContents(copy(left), copy(right), copy(bottom));
    }

    /** Every area filled with empty slots up to its capacity for a backpack of {@code mainCapacity} slots. */
    public static ToolAreaContents empty(int mainCapacity) {
        return EMPTY.fit(mainCapacity);
    }

    public List<ItemStack> list(PanelArea area) {
        return switch (area) {
            case LEFT -> left;
            case RIGHT -> right;
            case BOTTOM -> bottom;
            case MAIN -> List.of();
        };
    }

    /** Each list resized to its area's capacity; items are never dropped (see {@link ToolAreaCapacity#fit}). */
    public ToolAreaContents fit(int mainCapacity) {
        return normalize(
                fitList(left, PanelArea.LEFT, mainCapacity),
                fitList(right, PanelArea.RIGHT, mainCapacity),
                fitList(bottom, PanelArea.BOTTOM, mainCapacity));
    }

    private static List<ItemStack> fitList(List<ItemStack> list, PanelArea area, int mainCapacity) {
        return ToolAreaCapacity.fit(list, area, mainCapacity, ItemStack.EMPTY, ItemStack::isEmpty);
    }

    private static List<ItemStack> copy(List<ItemStack> list) {
        int size = list == null ? 0 : Math.min(list.size(), MAX_TOOL_SLOTS);
        List<ItemStack> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            ItemStack stack = list.get(i);
            out.add(stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return out;
    }
}
