package pnnh.mcfloatingbackpack.inventory;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Points at one floating-backpack stack on a player.
 * Resolve order: main hand → off hand → chest → inventory 0–35.
 */
public record BackpackRef(Source source, int invIndex) {
    public enum Source {
        MAIN_HAND,
        OFF_HAND,
        CHEST,
        INVENTORY
    }

    public static BackpackRef mainHand() {
        return new BackpackRef(Source.MAIN_HAND, -1);
    }

    public static BackpackRef resolve(Player player) {
        if (player == null) {
            return null;
        }
        if (FloatingBackpackItem.isBackpack(player.getMainHandItem())) {
            return new BackpackRef(Source.MAIN_HAND, -1);
        }
        if (FloatingBackpackItem.isBackpack(player.getOffhandItem())) {
            return new BackpackRef(Source.OFF_HAND, -1);
        }
        if (FloatingBackpackItem.isBackpack(player.getItemBySlot(EquipmentSlot.CHEST))) {
            return new BackpackRef(Source.CHEST, -1);
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            // Skip armor/offhand ranges that Inventory uses beyond 0–35 main storage on some mappings;
            // Player inventory items 0–35 are the standard main inventory + hotbar.
            if (i >= 36) {
                break;
            }
            if (FloatingBackpackItem.isBackpack(player.getInventory().getItem(i))) {
                return new BackpackRef(Source.INVENTORY, i);
            }
        }
        return null;
    }

    public ItemStack getStack(Player player) {
        return switch (source) {
            case MAIN_HAND -> player.getMainHandItem();
            case OFF_HAND -> player.getOffhandItem();
            case CHEST -> player.getItemBySlot(EquipmentSlot.CHEST);
            case INVENTORY -> {
                if (invIndex < 0 || invIndex >= 36) {
                    yield ItemStack.EMPTY;
                }
                yield player.getInventory().getItem(invIndex);
            }
        };
    }

    public void setStack(Player player, ItemStack stack) {
        switch (source) {
            case MAIN_HAND -> player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            case OFF_HAND -> player.setItemInHand(InteractionHand.OFF_HAND, stack);
            case CHEST -> player.setItemSlot(EquipmentSlot.CHEST, stack);
            case INVENTORY -> {
                if (invIndex >= 0 && invIndex < 36) {
                    player.getInventory().setItem(invIndex, stack);
                }
            }
        }
        player.getInventory().setChanged();
    }

    public boolean stillValid(Player player) {
        return FloatingBackpackItem.isBackpack(getStack(player));
    }

    public BackpackInventory loadInventory(Player player) {
        return BackpackInventory.fromItem(getStack(player));
    }

    public void saveInventory(Player player, BackpackInventory inventory) {
        ItemStack stack = getStack(player).copy();
        if (!FloatingBackpackItem.isBackpack(stack)) {
            return;
        }
        inventory.saveToItem(stack);
        setStack(player, stack);
    }
}
