package pnnh.mcfloatingbackpack.inventory;

import net.minecraft.world.item.ItemStack;

/** {@link QuickTransferLogic.StackOps} over real item stacks. */
public final class ItemStackTransferOps implements QuickTransferLogic.StackOps<ItemStack> {
    public static final ItemStackTransferOps INSTANCE = new ItemStackTransferOps();

    private ItemStackTransferOps() {
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
    public int maxStack(ItemStack stack) {
        return stack.getMaxStackSize();
    }

    @Override
    public boolean sameKind(ItemStack a, ItemStack b) {
        return ItemStack.isSameItemSameComponents(a, b);
    }

    @Override
    public ItemStack withCount(ItemStack stack, int count) {
        return count <= 0 || stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(count);
    }
}
