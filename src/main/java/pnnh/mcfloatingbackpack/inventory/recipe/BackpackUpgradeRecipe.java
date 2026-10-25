package pnnh.mcfloatingbackpack.inventory.recipe;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.item.crafting.SmithingRecipeInput;
import net.minecraft.world.level.Level;

import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Smithing upgrade: empty template slot, one addition item, contents copied forward.
 */
public class BackpackUpgradeRecipe implements SmithingRecipe {
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public BackpackUpgradeRecipe(Ingredient base, Ingredient addition, ItemStack result) {
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    public static final MapCodec<BackpackUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
            Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
    ).apply(instance, BackpackUpgradeRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
            BackpackUpgradeRecipe::toNetwork, BackpackUpgradeRecipe::fromNetwork);

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return input.template().isEmpty() && base.test(input.base()) && addition.test(input.addition());
    }

    @Override
    public ItemStack assemble(SmithingRecipeInput input, HolderLookup.Provider registries) {
        ItemStack upgraded = input.base().transmuteCopy(result.getItem(), result.getCount());
        BackpackTier tier = FloatingBackpackItem.tierOf(upgraded);
        int capacity = tier == null ? BackpackTier.LEATHER.capacity() : tier.capacity();
        BackpackContents current = upgraded.getOrDefault(
                ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty(capacity));
        upgraded.set(
                ModDataComponents.BACKPACK_CONTENTS.get(),
                BackpackContents.fit(current.items(), current.cursor(), capacity));
        return upgraded;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return stack.isEmpty();
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return base.test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return addition.test(stack);
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.BACKPACK_UPGRADE.get();
    }

    @Override
    public boolean isIncomplete() {
        return base.hasNoItems() || addition.hasNoItems();
    }

    private static BackpackUpgradeRecipe fromNetwork(RegistryFriendlyByteBuf buffer) {
        Ingredient baseIngredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        Ingredient additionIngredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        ItemStack resultStack = ItemStack.STREAM_CODEC.decode(buffer);
        return new BackpackUpgradeRecipe(baseIngredient, additionIngredient, resultStack);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, BackpackUpgradeRecipe recipe) {
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.base);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.addition);
        ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
    }
}
