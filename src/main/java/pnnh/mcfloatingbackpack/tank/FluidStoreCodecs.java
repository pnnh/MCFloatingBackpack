package pnnh.mcfloatingbackpack.tank;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 048: how a {@link FluidStore} is stored and sent. On disk it is one object, a key for each liquid that is present
 * ({@code water}, {@code oil}, {@code lava}, {@code milk}, {@code honey}, millibuckets) and, with oil, {@code oil_fluid} (the fluid's id);
 * what is missing is 0 or empty, an unknown key is ignored, and a stored value is cleaned to the invariants of the record. On the wire
 * it is a count, then an ordinal and an amount for each liquid, then the oil fluid (at most {@link #MAX_FLUID_ID} characters). Kept
 * apart from the record so the record loads without Minecraft on the class path.
 */
public final class FluidStoreCodecs {
    /** Longest fluid id the wire takes. */
    public static final int MAX_FLUID_ID = 256;

    public static final Codec<FluidStore> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.INT.optionalFieldOf(FluidKind.WATER.id(), 0).forGetter(s -> s.amount(FluidKind.WATER)),
                    Codec.INT.optionalFieldOf(FluidKind.OIL.id(), 0).forGetter(s -> s.amount(FluidKind.OIL)),
                    Codec.INT.optionalFieldOf(FluidKind.LAVA.id(), 0).forGetter(s -> s.amount(FluidKind.LAVA)),
                    Codec.INT.optionalFieldOf(FluidKind.MILK.id(), 0).forGetter(s -> s.amount(FluidKind.MILK)),
                    Codec.INT.optionalFieldOf(FluidKind.HONEY.id(), 0).forGetter(s -> s.amount(FluidKind.HONEY)),
                    Codec.STRING.optionalFieldOf("oil_fluid", "").forGetter(FluidStore::lockedFluid))
            .apply(instance, (water, oil, lava, milk, honey, oilFluid) -> {
                Map<FluidKind, Integer> amounts = new EnumMap<>(FluidKind.class);
                amounts.put(FluidKind.WATER, water);
                amounts.put(FluidKind.OIL, oil);
                amounts.put(FluidKind.LAVA, lava);
                amounts.put(FluidKind.MILK, milk);
                amounts.put(FluidKind.HONEY, honey);
                return FluidStore.of(amounts, oilFluid);
            }));

    private static final StreamCodec<ByteBuf, String> FLUID_ID = ByteBufCodecs.stringUtf8(MAX_FLUID_ID);

    public static final StreamCodec<ByteBuf, FluidStore> STREAM_CODEC = StreamCodec.of(
            (buf, store) -> {
                int count = 0;
                for (FluidKind kind : FluidKind.values()) {
                    if (store.amount(kind) > 0) {
                        count++;
                    }
                }
                ByteBufCodecs.VAR_INT.encode(buf, count);
                for (FluidKind kind : FluidKind.values()) {
                    if (store.amount(kind) > 0) {
                        ByteBufCodecs.VAR_INT.encode(buf, kind.ordinal());
                        ByteBufCodecs.VAR_INT.encode(buf, store.amount(kind));
                    }
                }
                FLUID_ID.encode(buf, store.lockedFluid());
            },
            buf -> {
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                FluidKind[] kinds = FluidKind.values();
                if (count < 0 || count > kinds.length) {
                    throw new DecoderException("too many liquids: " + count);
                }
                Map<FluidKind, Integer> amounts = new EnumMap<>(FluidKind.class);
                for (int i = 0; i < count; i++) {
                    int ordinal = ByteBufCodecs.VAR_INT.decode(buf);
                    if (ordinal < 0 || ordinal >= kinds.length) {
                        throw new DecoderException("no such liquid: " + ordinal);
                    }
                    amounts.put(kinds[ordinal], ByteBufCodecs.VAR_INT.decode(buf));
                }
                return FluidStore.of(amounts, FLUID_ID.decode(buf));
            });

    private FluidStoreCodecs() {
    }
}
