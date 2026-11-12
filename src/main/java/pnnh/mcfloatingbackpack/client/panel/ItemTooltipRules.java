package pnnh.mcfloatingbackpack.client.panel;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * 045: whether the panel shows the item tooltip of the cell under the mouse. Like the vanilla container screens it only shows one
 * when the cursor holds nothing, and it stays out of the way of what the player is doing: no tooltip while a drag-distribute is
 * previewed or a question (the trash confirmation) waits for an answer, none over a main area that the tag panel or the pickup
 * blacklist page covers (the tool areas stay uncovered and still show theirs), and none for an empty cell. No Minecraft types.
 */
public final class ItemTooltipRules {
    private ItemTooltipRules() {
    }

    public static boolean shouldShow(
            PanelArea area,
            boolean mainCovered,
            boolean cursorHasItem,
            boolean dragPreview,
            boolean dialogPending,
            boolean cellHasItem) {
        if (!cellHasItem || cursorHasItem || dragPreview || dialogPending) {
            return false;
        }
        return !(area == PanelArea.MAIN && mainCovered);
    }
}
