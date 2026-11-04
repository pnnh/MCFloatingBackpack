package pnnh.mcfloatingbackpack.inventory;

/**
 * The four slot areas of the floating panel (016): the main backpack area and the three tool areas around
 * it. The byte is what goes on the wire. Kept free of Minecraft types so it can be unit-tested.
 */
public enum PanelArea {
    MAIN,
    /** Input tool area: one column left of the main area. */
    LEFT,
    /** Output tool area: one column right of the main area. */
    RIGHT,
    /** Function tool area: one row below, spanning the whole panel width. */
    BOTTOM;

    public byte toByte() {
        return (byte) ordinal();
    }

    /** @return null for anything that is not a known area */
    public static PanelArea fromByte(byte value) {
        PanelArea[] all = values();
        return value >= 0 && value < all.length ? all[value] : null;
    }

    public boolean isTool() {
        return this != MAIN;
    }
}
