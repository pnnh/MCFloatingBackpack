package pnnh.mcfloatingbackpack.inventory.creative;

/**
 * 027: whether the server takes a cursor report from a client. In the creative inventory screen the cursor exists only on the
 * client, so the client tells the server what it holds; only a player in creative mode may do that (they can conjure any item
 * anyway) and the stack has to be one the game could hold. The first rule that applies wins. Pure logic.
 */
public final class CreativeCursorRules {
    public enum Verdict {
        ACCEPT,
        /** The player is not in creative mode. */
        NOT_CREATIVE,
        /** Another menu (a chest, a furnace...) is open, so there is no creative inventory screen. */
        OTHER_MENU,
        /** More than the item stacks to, or less than nothing. */
        BAD_COUNT,
        /** The item is switched off by a feature flag in this world. */
        DISABLED_ITEM
    }

    private CreativeCursorRules() {
    }

    /**
     * @param creative the player is in creative mode
     * @param inventoryMenuOpen the player's own inventory menu is the open one
     * @param empty the reported stack is empty (an empty cursor is always fine)
     * @param count the reported stack's count
     * @param maxStack how much of that item fit in one stack
     * @param itemEnabled the item is enabled in this world
     */
    public static Verdict check(
            boolean creative, boolean inventoryMenuOpen, boolean empty, int count, int maxStack, boolean itemEnabled) {
        if (!creative) {
            return Verdict.NOT_CREATIVE;
        }
        if (!inventoryMenuOpen) {
            return Verdict.OTHER_MENU;
        }
        if (empty) {
            return Verdict.ACCEPT;
        }
        if (count < 0 || count > maxStack) {
            return Verdict.BAD_COUNT;
        }
        if (!itemEnabled) {
            return Verdict.DISABLED_ITEM;
        }
        return Verdict.ACCEPT;
    }
}
