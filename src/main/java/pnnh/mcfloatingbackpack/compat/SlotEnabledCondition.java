package pnnh.mcfloatingbackpack.compat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 061: the data condition {@code mcfloatingbackpack:slot_enabled} ({@code {"type": ..., "mod": "curios"}}). The slot and entity files this mod
 * gives Curios and Accessories carry it, and both mods' loaders ask it with {@code ICondition.conditionsMatched} before they read a file;
 * it is true when the start-up switch for that mod is on ({@link WornSlotsConfig}). It uses nothing of the accessory mods and needs no
 * tags, so it is always registered, whether the mods are there or not.
 */
public record SlotEnabledCondition(String mod) implements ICondition {
    public static final MapCodec<SlotEnabledCondition> CODEC = RecordCodecBuilder.mapCodec(
            builder -> builder.group(Codec.STRING.fieldOf("mod").forGetter(SlotEnabledCondition::mod)).apply(builder, SlotEnabledCondition::new));

    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.CONDITION_SERIALIZERS, MCFloatingBackpack.MOD_ID);

    static {
        CONDITIONS.register("slot_enabled", () -> CODEC);
    }

    /** Registers the condition type; called from the mod's constructor. */
    public static void register(IEventBus modEventBus) {
        CONDITIONS.register(modEventBus);
    }

    @Override
    public boolean test(IContext context) {
        return WornSlotsConfig.enabled(mod);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "floating_backpack_slot_enabled(\"" + mod + "\")";
    }
}
