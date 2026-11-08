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
 * Smithing upgrade: a template, one base backpack, one addition item; contents copied forward.
 *
 * <p>023: the template is an ingredient of its own ({@code template} in the JSON). It is optional: leaving it out gives
 * {@link Ingredient#EMPTY}, which {@link Ingredient#test} only accepts for an empty stack, so a recipe written for 006
 * (no template) still wants an empty template slot. The smithing table consumes one of each of the three inputs.
 */
public class BackpackUpgradeRecipe implements SmithingRecipe {
    private final Ingredient template;
    private final Ingredient base;
    private final Ingredient addition;
    private final ItemStack result;

    public BackpackUpgradeRecipe(Ingredient template, Ingredient base, Ingredient addition, ItemStack result) {
        this.template = template;
        this.base = base;
        this.addition = addition;
        this.result = result;
    }

    public Ingredient template() {
        return template;
    }

    public Ingredient base() {
        return base;
    }

    public Ingredient addition() {
        return addition;
    }

    public ItemStack result() {
        return result;
    }

    public static final MapCodec<BackpackUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC.optionalFieldOf("template", Ingredient.EMPTY).forGetter(recipe -> recipe.template),
            Ingredient.CODEC.fieldOf("base").forGetter(recipe -> recipe.base),
            Ingredient.CODEC.fieldOf("addition").forGetter(recipe -> recipe.addition),
            ItemStack.STRICT_CODEC.fieldOf("result").forGetter(recipe -> recipe.result)
    ).apply(instance, BackpackUpgradeRecipe::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackUpgradeRecipe> STREAM_CODEC = StreamCodec.of(
            BackpackUpgradeRecipe::toNetwork, BackpackUpgradeRecipe::fromNetwork);

    @Override
    public boolean matches(SmithingRecipeInput input, Level level) {
        return template.test(input.template()) && base.test(input.base()) && addition.test(input.addition());
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
                BackpackContents.fit(current.items(), current.cursor(), current.tools(), current.tags(), current.trash(), capacity));
        return upgraded;
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result;
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return template.test(stack);
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
        Ingredient templateIngredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        Ingredient baseIngredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        Ingredient additionIngredient = Ingredient.CONTENTS_STREAM_CODEC.decode(buffer);
        ItemStack resultStack = ItemStack.STREAM_CODEC.decode(buffer);
        return new BackpackUpgradeRecipe(templateIngredient, baseIngredient, additionIngredient, resultStack);
    }

    private static void toNetwork(RegistryFriendlyByteBuf buffer, BackpackUpgradeRecipe recipe) {
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.template);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.base);
        Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, recipe.addition);
        ItemStack.STREAM_CODEC.encode(buffer, recipe.result);
    }
}
