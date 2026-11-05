package pnnh.mcfloatingbackpack.compat;

import java.util.Locale;

/**
 * 020: how far the mod goes in working with one other mod (constitution VII).
 *
 * <ul>
 *   <li>{@link #FULL}: the mod-specific adaptations (code written for that mod). The default.
 *   <li>{@link #GENERIC}: none of the mod-specific code runs. Whether the panel shows on the mod's screens and
 *       exchanges items with its menus is not decided here: it depends on how the mod implements its containers, as
 *       for any mod the panel has no special code for.
 *   <li>{@link #OFF}: the panel is not shown on that mod's container screens (either the panel or that UI).
 * </ul>
 */
public enum CompatLevel {
    FULL,
    GENERIC,
    OFF;

    /** The level named by the text (case and surrounding spaces ignored), or null if it is not one. */
    public static CompatLevel parse(String text) {
        if (text == null) {
            return null;
        }
        String name = text.trim().toUpperCase(Locale.ROOT);
        for (CompatLevel level : values()) {
            if (level.name().equals(name)) {
                return level;
            }
        }
        return null;
    }
}
