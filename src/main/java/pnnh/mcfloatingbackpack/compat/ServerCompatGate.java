package pnnh.mcfloatingbackpack.compat;

/**
 * 020: the one question the server asks before it touches a menu that belongs to another mod: "may the floating backpack
 * do this with that mod's menu?". For now the answer is always yes, so nothing changes; it exists so every server entry
 * that reaches into such a menu already goes through one place, and a later server-side setting only has to change
 * {@link #allows}. A client-side setting is not a security boundary (constitution II and VII), which is why the decision
 * lives here. Pure logic with no Minecraft types.
 */
public final class ServerCompatGate {
    /** What the backpack wants to do with the open menu. */
    public enum Action {
        /** Put stacks from the backpack into the open container. */
        DELIVER_TO_CONTAINER,
        /** Take stacks out of the open container into the backpack. */
        TAKE_FROM_CONTAINER,
        /** Merge the panel's own cursor stack into the menu's carried stack. */
        MERGE_CURSOR,
        /** Click or drag with the menu's carried stack as the cursor. */
        USE_MENU_CURSOR
    }

    private ServerCompatGate() {
    }

    /** @param ownerModId the mod the menu belongs to (see {@link MenuOwner}); vanilla is {@code minecraft} */
    public static boolean allows(Action action, String ownerModId) {
        return true;
    }
}
