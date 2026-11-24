package pnnh.mcfloatingbackpack.inventory;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.worn.BackpackSearch;
import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;
import pnnh.mcfloatingbackpack.network.BackpackNetwork;

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
        CURIOS(true),
        /** 034: worn in the back slot of Accessories. Added at the end, so no ordinal before it changes. */
        ACCESSORIES(true),
        /** 061: worn in the dedicated "floating backpack" slot of Curios. Added at the end, so no ordinal before it changes. */
        CURIOS_DEDICATED(true),
        /** 061: worn in the charm slot of Curios. */
        CURIOS_CHARM(true),
        /** 061: worn in the dedicated "floating backpack" slot of Accessories. */
        ACCESSORIES_DEDICATED(true),
        /** 061: worn in the charm slot of Accessories. */
        ACCESSORIES_CHARM(true);

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

    /**
     * 065: the same backpack as one reference, whichever way it is named. The main hand is the selected inventory slot, so a reference to
     * {@code MAIN_HAND} and one to {@code INVENTORY(selected)} are the same backpack; the combined backpack's member table and its session
     * only use the second kind.
     */
    public BackpackRef normalized(int selectedSlot) {
        return source == Source.MAIN_HAND ? new BackpackRef(Source.INVENTORY, selectedSlot) : this;
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
        // 035: saving reconciles the rows with the extension packs placed; if the visible rows are not the ones the working copy
        // was loaded with, the numbering of the main area has changed and the client needs the whole picture again.
        BackpackContents saved = stack.get(ModDataComponents.BACKPACK_CONTENTS.get());
        boolean layoutChanged = saved != null && !saved.rows().visibleCategories().equals(inventory.visibleRowCategories());
        // 030: tell the snapshot service the backpack changed (a snapshot of it is written a little later).
        BackpackGate.afterSave(player, stack);
        setStack(player, stack);
        if (layoutChanged) {
            BackpackNetwork.sendFullSyncIfServer(player, this);
        }
    }
}
