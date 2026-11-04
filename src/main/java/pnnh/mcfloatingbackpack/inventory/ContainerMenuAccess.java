package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** {@link OpenContainerDelivery.MenuAccess} over the menu a {@link ServerPlayer} currently has open. */
public final class ContainerMenuAccess implements OpenContainerDelivery.MenuAccess<ItemStack> {
    private final ServerPlayer player;
    private final AbstractContainerMenu menu;

    public ContainerMenuAccess(ServerPlayer player) {
        this.player = player;
        this.menu = player.containerMenu;
    }

    /**
     * True when the player has a menu open that has slots beyond their own inventory. The player's
     * own inventory screen (and no screen at all) is {@code inventoryMenu}, which is not a container.
     */
    public static boolean hasOpenContainer(ServerPlayer player) {
        return player.containerMenu != player.inventoryMenu
                && OpenContainerDelivery.hasOpenContainer(new ContainerMenuAccess(player).slots(ItemStack.EMPTY));
    }

    public AbstractContainerMenu menu() {
        return menu;
    }

    @Override
    public List<OpenContainerDelivery.SlotView> slots(ItemStack source) {
        List<OpenContainerDelivery.SlotView> views = new ArrayList<>(menu.slots.size());
        for (Slot slot : menu.slots) {
            boolean ownInventory = slot.container == player.getInventory();
            int containerSlot = ownInventory ? slot.getContainerSlot() : -1;
            views.add(new OpenContainerDelivery.SlotView(
                    slot.index,
                    ownInventory,
                    containerSlot,
                    slot.hasItem(),
                    ownInventory && slot.mayPickup(player),
                    ownInventory && !source.isEmpty() && slot.mayPlace(source),
                    ownInventory && containerSlot == player.getInventory().selected));
        }
        return views;
    }

    @Override
    public ItemStack get(int menuIndex) {
        return menu.getSlot(menuIndex).getItem();
    }

    @Override
    public void set(int menuIndex, ItemStack stack) {
        menu.getSlot(menuIndex).setByPlayer(stack);
    }

    /** A slot that belongs to the opened container (not to the player's own inventory). */
    public boolean isExternalSlot(int menuIndex) {
        return menuIndex >= 0 && menuIndex < menu.slots.size() && menu.getSlot(menuIndex).container != player.getInventory();
    }

    /** Real-slot adapter for {@link ContainerToBackpack}: vanilla's own take/insert helpers, so mods' slots behave. */
    public ContainerToBackpack.SlotAccess<ItemStack> slotAccess(int menuIndex) {
        Slot slot = menu.getSlot(menuIndex);
        return new ContainerToBackpack.SlotAccess<>() {
            @Override
            public ItemStack get() {
                return slot.getItem();
            }

            @Override
            public boolean canTakeAndPlaceBack() {
                return slot.allowModification(player);
            }

            @Override
            public ItemStack take(int count) {
                return slot.safeTake(count, count, player);
            }

            @Override
            public ItemStack insert(ItemStack stack) {
                return slot.safeInsert(stack);
            }

            @Override
            public void set(ItemStack stack) {
                slot.setByPlayer(stack);
            }
        };
    }

    /** Where a stack goes when the slot it came from refuses it back: player inventory 0-35, then the ground. */
    public ContainerToBackpack.Fallback<ItemStack> fallback() {
        int[] order = new int[QuickTransferRequest.INVENTORY_SLOTS];
        for (int i = 0; i < order.length; i++) {
            order[i] = i;
        }
        return new ContainerToBackpack.Fallback<>() {
            @Override
            public ItemStack toInventory(ItemStack stack) {
                return QuickTransferLogic.moveInto(
                        ItemStackTransferOps.INSTANCE,
                        stack,
                        order,
                        player.getInventory()::getItem,
                        (s, i) -> player.getInventory().setItem(i, s));
            }

            @Override
            public void drop(ItemStack stack) {
                player.drop(stack, false);
            }
        };
    }

    @Override
    public ItemStack quickMove(int menuIndex) {
        return menu.quickMoveStack(player, menuIndex);
    }
}
