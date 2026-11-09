package pnnh.mcfloatingbackpack.inventory;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.worn.BackpackSearch;
import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Points at one floating-backpack stack on a player.
 * Resolve order (032, one place for it: {@link WornBackpacks}): main hand, off hand, the first backpack worn, inventory 0-35.
 *
 * <p>On the wire a reference is (the source's ordinal, {@code invIndex}). For a worn source {@code invIndex} is the slot within it
 * (0 for the chest); a source added later goes at the end of {@link Source}, so no ordinal ever changes.
 */
public record BackpackRef(Source source, int invIndex) {
    public enum Source {
        MAIN_HAND(false),
        OFF_HAND(false),
        /** Worn in the chest slot. */
        CHEST(true),
        INVENTORY(false),
        /** 033: worn in the back slot of Curios. Added at the end, so no ordinal before it changes. */
        CURIOS(true);

        private final boolean worn;

        Source(boolean worn) {
            this.worn = worn;
        }

        /** Whether this is a place a backpack is worn (its stack is found through a worn source, see {@link WornBackpacks}). */
        public boolean isWorn() {
            return worn;
        }
    }

    public static BackpackRef mainHand() {
        return new BackpackRef(Source.MAIN_HAND, -1);
    }

    public static BackpackRef resolve(Player player) {
        if (player == null) {
            return null;
        }
        BackpackSearch.Hit hit = WornBackpacks.resolvePlayer(player);
        if (hit == null) {
            return null;
        }
        return switch (hit.kind()) {
            case MAIN_HAND -> mainHand();
            case OFF_HAND -> new BackpackRef(Source.OFF_HAND, -1);
            case WORN -> new BackpackRef(Source.values()[hit.sourceId()], hit.slot());
            case INVENTORY -> new BackpackRef(Source.INVENTORY, hit.slot());
        };
    }

    public ItemStack getStack(Player player) {
        if (source.isWorn()) {
            return WornBackpacks.get(player, source.ordinal(), invIndex);
        }
        return switch (source) {
            case MAIN_HAND -> player.getMainHandItem();
            case OFF_HAND -> player.getOffhandItem();
            case INVENTORY -> {
                if (invIndex < 0 || invIndex >= 36) {
                    yield ItemStack.EMPTY;
                }
                yield player.getInventory().getItem(invIndex);
            }
            default -> ItemStack.EMPTY;
        };
    }

    public void setStack(Player player, ItemStack stack) {
        if (source.isWorn()) {
            WornBackpacks.set(player, source.ordinal(), invIndex, stack);
        } else {
            switch (source) {
                case MAIN_HAND -> player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                case OFF_HAND -> player.setItemInHand(InteractionHand.OFF_HAND, stack);
                case INVENTORY -> {
                    if (invIndex >= 0 && invIndex < 36) {
                        player.getInventory().setItem(invIndex, stack);
                    }
                }
                default -> {
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
        // 030: tell the snapshot service the backpack changed (a snapshot of it is written a little later).
        BackpackGate.afterSave(player, stack);
        setStack(player, stack);
    }
}
