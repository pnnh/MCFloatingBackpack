package pnnh.mcfloatingbackpack.inventory.recipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MCFloatingBackpack.MOD_ID);

    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<BackpackUpgradeRecipe>> BACKPACK_UPGRADE =
            SERIALIZERS.register("backpack_upgrade", () -> new RecipeSerializer<>() {
                @Override
                public com.mojang.serialization.MapCodec<BackpackUpgradeRecipe> codec() {
                    return BackpackUpgradeRecipe.CODEC;
                }

                @Override
                public net.minecraft.network.codec.StreamCodec<
                        net.minecraft.network.RegistryFriendlyByteBuf, BackpackUpgradeRecipe> streamCodec() {
                    return BackpackUpgradeRecipe.STREAM_CODEC;
                }
            });

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        SERIALIZERS.register(modEventBus);
    }
}
