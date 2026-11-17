package pnnh.mcfloatingbackpack.tool.furnace;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;

/**
 * 055: the three slots of a mobile furnace as a {@link Container}, over the engine's {@link FurnaceCore} (so the menu and the engine work on the
 * same stacks). It does what a vanilla furnace block entity does to its slots: setting the input to another kind of item starts its cooking over
 * ({@code AbstractFurnaceBlockEntity.setItem}); the output takes nothing; the fuel slot takes fuel, or an empty bucket. Every change calls
 * {@code onChanged}, which is what writes the state back to the item.
 */
public final class FurnaceStateContainer implements Container, StackedContentsCompatible {
    private final FurnaceCore<ItemStack> core;
    private final McFurnaceRules rules;
    private final Runnable onChanged;

    public FurnaceStateContainer(FurnaceCore<ItemStack> core, McFurnaceRules rules, Runnable onChanged) {
        this.core = core;
        this.rules = rules;
        this.onChanged = onChanged;
    }

    @Override
    public int getContainerSize() {
        return 3;
    }

    @Override
    public boolean isEmpty() {
        return core.input.isEmpty() && core.fuel.isEmpty() && core.output.isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        return switch (slot) {
            case 0 -> core.input;
            case 1 -> core.fuel;
            case 2 -> core.output;
            default -> ItemStack.EMPTY;
        };
    }

    private void put(int slot, ItemStack stack) {
        switch (slot) {
            case 0 -> core.input = stack;
            case 1 -> core.fuel = stack;
            case 2 -> core.output = stack;
            default -> {
            }
        }
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        ItemStack stack = getItem(slot);
        if (stack.isEmpty() || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = stack.split(amount);
        if (stack.isEmpty()) {
            put(slot, ItemStack.EMPTY);
        }
        if (!taken.isEmpty()) {
            setChanged();
        }
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = getItem(slot);
        put(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        ItemStack old = getItem(slot);
        boolean same = !stack.isEmpty() && ItemStack.isSameItemSameComponents(old, stack);
        put(slot, stack);
        stack.limitSize(getMaxStackSize(stack));
        if (slot == 0 && !same) {
            FurnaceRules.Recipe<ItemStack> recipe = rules.recipeFor(core.input);
            core.cookTotal = recipe == null ? FurnaceEngine.DEFAULT_COOK_TIME : recipe.cookTime();
            core.cookProgress = 0;
        }
        setChanged();
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 2) {
            return false;
        }
        if (slot != 1) {
            return true;
        }
        return stack.getBurnTime(RecipeType.SMELTING) > 0 || stack.is(Items.BUCKET) && !core.fuel.is(Items.BUCKET);
    }

    @Override
    public void setChanged() {
        onChanged.run();
    }

    /** The menu decides whether the player may use it; see {@code MobileFurnaceMenu#stillValid}. */
    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void clearContent() {
        core.input = ItemStack.EMPTY;
        core.fuel = ItemStack.EMPTY;
        core.output = ItemStack.EMPTY;
    }

    @Override
    public void fillStackedContents(StackedContents helper) {
        helper.accountStack(core.input);
        helper.accountStack(core.fuel);
        helper.accountStack(core.output);
    }
}
