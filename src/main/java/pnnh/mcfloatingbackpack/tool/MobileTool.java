package pnnh.mcfloatingbackpack.tool;

/**
 * 054: the mobile tools of the right area that open a vanilla screen when they are clicked (the mobile crafting table now, the mobile furnace
 * in 055). The byte is what goes on the wire in the "open tool" request; the server reads it with {@link #fromByte}, which gives
 * {@code null} for a value this version does not know (such a request is ignored, never an error: the client is not trusted). Pure logic with
 * no Minecraft types.
 */
public enum MobileTool {
    CRAFTING_TABLE((byte) 0);

    private final byte id;

    MobileTool(byte id) {
        this.id = id;
    }

    public byte id() {
        return id;
    }

    /** @return the tool with this byte, or null for any other value */
    public static MobileTool fromByte(byte value) {
        for (MobileTool tool : values()) {
            if (tool.id == value) {
                return tool;
            }
        }
        return null;
    }
}
