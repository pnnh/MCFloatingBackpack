package pnnh.mcfloatingbackpack.tool.furnace;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 055: what a mobile furnace item keeps on itself (the data component {@code mobile_furnace_state}): the three slots, the four counters of a
 * vanilla furnace, how many of each recipe have been smelted without the experience taken yet, and {@code stamp}, the game time the state is
 * worked out up to. An item without it is an empty furnace. The state is the furnace's own, so it goes wherever the item goes (into another
 * backpack, onto the cursor, into the inventory). Not a live object: {@link FurnaceCore} is what the engine works on.
 */
public record FurnaceState(
        ItemStack input,
        ItemStack fuel,
        ItemStack output,
        int litTime,
        int litDuration,
        int cookProgress,
        int cookTotal,
        Map<String, Integer> recipesUsed,
        long stamp) {
    /** The stamp of a state that has none (read from nothing): no time is worked out from it. */
    public static final long NO_STAMP = -1L;

    public static final FurnaceState EMPTY = new FurnaceState(
            ItemStack.EMPTY, ItemStack.EMPTY, ItemStack.EMPTY, 0, 0, 0, 0, Map.of(), NO_STAMP);

    public static final Codec<FurnaceState> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("input", ItemStack.EMPTY).forGetter(FurnaceState::input),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("fuel", ItemStack.EMPTY).forGetter(FurnaceState::fuel),
                    ItemStack.OPTIONAL_CODEC.optionalFieldOf("output", ItemStack.EMPTY).forGetter(FurnaceState::output),
                    Codec.INT.optionalFieldOf("lit_time", 0).forGetter(FurnaceState::litTime),
                    Codec.INT.optionalFieldOf("lit_duration", 0).forGetter(FurnaceState::litDuration),
                    Codec.INT.optionalFieldOf("cook_progress", 0).forGetter(FurnaceState::cookProgress),
                    Codec.INT.optionalFieldOf("cook_total", 0).forGetter(FurnaceState::cookTotal),
                    Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("recipes_used", Map.of()).forGetter(FurnaceState::recipesUsed),
                    Codec.LONG.optionalFieldOf("stamp", NO_STAMP).forGetter(FurnaceState::stamp))
            .apply(instance, FurnaceState::normalize));

    private static final StreamCodec<RegistryFriendlyByteBuf, Map<String, Integer>> USED_CODEC =
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT);

    public static final StreamCodec<RegistryFriendlyByteBuf, FurnaceState> STREAM_CODEC = StreamCodec.of(
            (buf, state) -> {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, state.input());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, state.fuel());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, state.output());
                buf.writeVarInt(state.litTime());
                buf.writeVarInt(state.litDuration());
                buf.writeVarInt(state.cookProgress());
                buf.writeVarInt(state.cookTotal());
                USED_CODEC.encode(buf, new HashMap<>(state.recipesUsed()));
                buf.writeVarLong(state.stamp() + 1);
            },
            buf -> normalize(
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    ItemStack.OPTIONAL_STREAM_CODEC.decode(buf),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    USED_CODEC.decode(buf),
                    buf.readVarLong() - 1));

    /** Copies the stacks; a negative counter reads as 0, a recipe with no count is left out, a negative stamp is "no stamp". */
    public static FurnaceState normalize(
            ItemStack input,
            ItemStack fuel,
            ItemStack output,
            int litTime,
            int litDuration,
            int cookProgress,
            int cookTotal,
            Map<String, Integer> recipesUsed,
            long stamp) {
        Map<String, Integer> used = new LinkedHashMap<>();
        if (recipesUsed != null) {
            recipesUsed.forEach((id, count) -> {
                if (count != null && count > 0) {
                    used.put(id, count);
                }
            });
        }
        return new FurnaceState(
                copyOf(input),
                copyOf(fuel),
                copyOf(output),
                Math.max(0, litTime),
                Math.max(0, litDuration),
                Math.max(0, cookProgress),
                Math.max(0, cookTotal),
                Map.copyOf(used),
                stamp < 0 ? NO_STAMP : stamp);
    }

    private static ItemStack copyOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
    }

    /** The state the engine works on (the stacks are copies). */
    public FurnaceCore<ItemStack> toCore() {
        FurnaceCore<ItemStack> core = new FurnaceCore<>(copyOf(input), copyOf(fuel), copyOf(output));
        core.litTime = litTime;
        core.litDuration = litDuration;
        core.cookProgress = cookProgress;
        core.cookTotal = cookTotal;
        core.recipesUsed.putAll(recipesUsed);
        return core;
    }

    /** The state to keep on the item: what the engine has now, worked out up to {@code stamp}. */
    public static FurnaceState of(FurnaceCore<ItemStack> core, long stamp) {
        return normalize(
                core.input, core.fuel, core.output, core.litTime, core.litDuration, core.cookProgress, core.cookTotal, core.recipesUsed, stamp);
    }
}
