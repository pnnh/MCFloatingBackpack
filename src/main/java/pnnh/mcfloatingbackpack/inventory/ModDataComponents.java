package pnnh.mcfloatingbackpack.inventory;

import java.util.function.UnaryOperator;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

public final class ModDataComponents {
    public static final DeferredRegister.DataComponents COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, MCFloatingBackpack.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<BackpackContents>> BACKPACK_CONTENTS =
            COMPONENTS.registerComponentType(
                    "backpack_contents",
                    (UnaryOperator<DataComponentType.Builder<BackpackContents>>) builder -> builder
                            .persistent(BackpackContents.CODEC)
                            .networkSynchronized(BackpackContents.STREAM_CODEC));

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        COMPONENTS.register(modEventBus);
    }
}
