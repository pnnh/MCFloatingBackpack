package pnnh.mcfloatingbackpack.client.panel;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * 046: which cells are drawn as grey cells. While a search is on the main area shows the results packed at the front (042); every
 * other cell of it has no slot behind it and is drawn grey, so the area still looks like the whole grid. Outside a search those
 * cells (beyond the capacity, the empty tail of the last row) stay plain panel background, and the tool areas' are never grey. Nothing
 * here makes a cell clickable or not: a cell without a slot cannot be hit at all. No Minecraft types.
 */
public final class GreyCellRules {
    private GreyCellRules() {
    }

    /**
     * @param searching   whether a query is on ({@code SearchState.isFiltering()})
     * @param mainCovered whether the tag panel or the pickup page covers the main area (nothing of it is drawn then)
     * @param index       the cell's slot, negative when there is none
     */
    public static boolean greyed(boolean searching, boolean mainCovered, PanelArea area, int index) {
        return searching && !mainCovered && area == PanelArea.MAIN && index < 0;
    }
}
