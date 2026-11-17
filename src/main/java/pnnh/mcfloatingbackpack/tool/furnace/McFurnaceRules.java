package pnnh.mcfloatingbackpack.tool.furnace;

import java.util.Optional;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * 055: {@link FurnaceRules} over real item stacks: the recipes are the vanilla furnace's ({@link RecipeType#SMELTING}, so a recipe a datapack or
 * another mod adds counts), the fuel is what a furnace takes (the stack's burn time for smelting, which includes the fuel data map and the
 * burn-time event), the rest is what a vanilla furnace does.
 */
public final class McFurnaceRules implements FurnaceRules<ItemStack> {
    private final Level level;
    private final RecipeManager.CachedCheck<SingleRecipeInput, SmeltingRecipe> check = RecipeManager.createCheck(RecipeType.SMELTING);

    public McFurnaceRules(Level level) {
        this.level = level;
    }

    @Override
    public Recipe<ItemStack> recipeFor(ItemStack input) {
        if (input.isEmpty()) {
            return null;
        }
        SingleRecipeInput single = new SingleRecipeInput(input);
        Optional<RecipeHolder<SmeltingRecipe>> holder = check.getRecipeFor(single, level);
        if (holder.isEmpty()) {
            return null;
        }
        SmeltingRecipe recipe = holder.get().value();
        return new Recipe<>(
                holder.get().id().toString(), recipe.getCookingTime(), recipe.assemble(single, level.registryAccess()), recipe.getExperience());
    }

    @Override
    public int burnTime(ItemStack fuel) {
        return fuel.isEmpty() ? 0 : fuel.getBurnTime(RecipeType.SMELTING);
    }

    @Override
    public ItemStack remainder(ItemStack fuel) {
        return fuel.hasCraftingRemainingItem() ? fuel.getCraftingRemainingItem() : ItemStack.EMPTY;
    }

    @Override
    public ItemStack afterSmelt(ItemStack input, ItemStack fuel) {
        if (input.is(Blocks.WET_SPONGE.asItem()) && !fuel.isEmpty() && fuel.is(Items.BUCKET)) {
            return new ItemStack(Items.WATER_BUCKET);
        }
        return fuel;
    }

    @Override
    public ItemStack empty() {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean isEmpty(ItemStack stack) {
        return stack.isEmpty();
    }

    @Override
    public int count(ItemStack stack) {
        return stack.getCount();
    }

    @Override
    public ItemStack withCount(ItemStack stack, int count) {
        return count <= 0 || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(count);
    }

    @Override
    public boolean same(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    @Override
    public int maxStack(ItemStack stack) {
        return stack.getMaxStackSize();
    }
}
