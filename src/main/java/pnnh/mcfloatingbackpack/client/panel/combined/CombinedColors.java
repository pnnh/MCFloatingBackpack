package pnnh.mcfloatingbackpack.client.panel.combined;

/**
 * 065: the colour of each tab's swatch. Taken by the tab's position in the tab strip, so the first tab is always the first colour; the
 * "all" tab of the next feature (066) draws its cells in the same colours. Bright enough to read on the dark panel. Pure.
 */
public final class CombinedColors {
    private static final int[] PALETTE = {
        0xE6504A, 0xF58231, 0xF0C419, 0xB2D732, 0x3CB44B, 0x2EC4B6, 0x46C8F0, 0x4363D8,
        0x7A5CF0, 0xB65CE0, 0xF032E6, 0xFF8FB1, 0xA9744F, 0xA0A0A0, 0x7DE8B0, 0xF2F2F2
    };

    private CombinedColors() {
    }

    public static int count() {
        return PALETTE.length;
    }

    /** 066: the same colour as a faint wash for a cell of the "all" grid (under the items, the search frame and the selection). */
    public static int wash(int position) {
        return 0x40000000 | (argb(position) & 0x00FFFFFF);
    }

    /** The colour as opaque ARGB for the tab at {@code position} (0-based); positions past the palette wrap round. */
    public static int argb(int position) {
        int index = Math.floorMod(position, PALETTE.length);
        return 0xFF000000 | PALETTE[index];
    }
}
