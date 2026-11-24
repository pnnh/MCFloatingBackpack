package pnnh.mcfloatingbackpack.client.panel.combined;

/**
 * 066: the "all" tab's search result in the order it is shown: the cells that match, as flat cell numbers, following the display order (the
 * default one, or the sorted one). The main map of the panel is that list packed from the top left. Pure.
 */
public final class AllSearchMap {
    private AllSearchMap() {
    }

    /**
     * @param positions the display positions that matched (ascending)
     * @param order display position -> flat cell
     * @return the flat cells of the matches, in display order
     */
    public static int[] toFlat(int[] positions, int[] order) {
        int[] flat = new int[positions.length];
        for (int i = 0; i < positions.length; i++) {
            flat[i] = order[positions[i]];
        }
        return flat;
    }
}
