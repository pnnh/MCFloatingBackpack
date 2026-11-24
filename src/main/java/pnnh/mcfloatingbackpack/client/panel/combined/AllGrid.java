package pnnh.mcfloatingbackpack.client.panel.combined;

/**
 * 066: how the "all" tab's flat cells are laid out on the screen: one after the other over the panel's columns, the members' cells joined end to
 * end, so that a backpack starts where the one before it stopped and the only cells that belong to nobody are the tail of the last row. The
 * map is the main map the panel layout already understands (display position -> flat cell number). Pure.
 */
public final class AllGrid {
    public static final int FILLER = -1;

    private AllGrid() {
    }

    /**
     * @param sizes the number of main-area cells of each member, in table order
     * @return display position -> flat cell number; the cells in order, nothing in between
     */
    public static int[] displayMap(int[] sizes) {
        int total = 0;
        for (int size : sizes) {
            total += Math.max(0, size);
        }
        int[] map = new int[total];
        for (int i = 0; i < total; i++) {
            map[i] = i;
        }
        return map;
    }
}
