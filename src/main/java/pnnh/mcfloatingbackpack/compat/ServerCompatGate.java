package pnnh.mcfloatingbackpack.compat;

import java.util.function.Function;

/**
 * 020, 059: the one question the server asks before it touches a menu that belongs to another mod: "may the floating backpack do this
 * with that mod's menu?". The answer comes from the level the server goes by for that mod ({@link EffectiveLevel}): OFF says no, FULL and
 * GENERIC say yes (GENERIC only turns off the mod-specific code, not the generic exchange). Every server entry that reaches into such a
 * menu goes through here (see {@code ServerCompatDenials}), so a server setting only has to change what the level source says. A
 * client-side setting is not a security boundary (constitution II and VII), which is why the decision lives here. Until a level source
 * is set (the mod's constructor sets the server's), every mod is FULL. Pure logic with no Minecraft types.
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

    private static volatile Function<String, CompatLevel> levels = modId -> CompatLevel.FULL;

    private ServerCompatGate() {
    }

    /** Where the level of a mod comes from; the default says FULL for every mod. */
    public static void setLevelSource(Function<String, CompatLevel> source) {
        levels = source == null ? modId -> CompatLevel.FULL : source;
    }

    /** The answer for a level: only OFF refuses, whatever the action. */
    public static boolean allows(Action action, CompatLevel level) {
        return level != CompatLevel.OFF;
    }

    /** @param ownerModId the mod the menu belongs to (see {@link MenuOwner}); vanilla is {@code minecraft} */
    public static boolean allows(Action action, String ownerModId) {
        return allows(action, levels.apply(ownerModId));
    }
}
