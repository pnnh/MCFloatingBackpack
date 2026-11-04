package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.PanelArea;

class PanelLayoutTest {
    private FloatingPanelState state;

    /** Wide enough that column fit never binds, so these pre-007 cases behave exactly as before. */
    private static final int WIDE_SCREEN = 1000;

    @BeforeEach
    void setUp() {
        state = FloatingPanelState.get();
        state.setVisible(true);
        state.setPos(FloatingPanelState.DEFAULT_POS_X, FloatingPanelState.DEFAULT_POS_Y);
        state.setScrollRow(0);
        state.endDrag();
        state.endResize();
        state.clearSizePreference();
        resetCounts();
    }

    /** The state is a singleton shared with other tests: leave it unrestricted (no counts reported). */
    @AfterEach
    void tearDown() {
        resetCounts();
    }

    private void resetCounts() {
        state.setMainCount(0);
        for (PanelArea area : new PanelArea[] {PanelArea.LEFT, PanelArea.RIGHT, PanelArea.BOTTOM}) {
            state.setToolCount(area, 0);
        }
    }

    /** An iron backpack (017): 54 main slots, 6 + 6 in the side columns, 11 in the bottom row; laid out at 9 x 6. */
    private void useIron() {
        state.setMainCount(54);
        state.setToolCount(PanelArea.LEFT, 6);
        state.setToolCount(PanelArea.RIGHT, 6);
        state.setToolCount(PanelArea.BOTTOM, 11);
        state.setTotalRows(6);
        state.layout(WIDE_SCREEN, 400);
        state.setTotalRows(12); // the real view reports 54 / cols rows; keep it out of the way of the row limit
    }

    private void useLeather() {
        state.setMainCount(36);
        state.setToolCount(PanelArea.LEFT, 4);
        state.setToolCount(PanelArea.RIGHT, 4);
        state.setToolCount(PanelArea.BOTTOM, 11);
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);
        state.setTotalRows(12);
    }

    private void drag(int dCols, int dRows) {
        state.beginResize(0, 0);
        state.updateResize(dCols * PanelSlots.SLOT_SIZE, dRows * PanelSlots.SLOT_SIZE);
    }

    @Test
    void layoutCapsVisibleRowsToTheScreen() {
        state.setTotalRows(12);
        // 40 bottom margin on a 240-tall GUI → room for fewer than 12 rows.
        state.layout(WIDE_SCREEN, 240);

        assertTrue(state.getVisibleRows() < 12);
        assertEquals(PanelSlots.panelWidth(state.getVisibleCols()), state.getWidth());
        assertEquals(PanelSlots.panelHeight(state.getVisibleRows()), state.getHeight());
        assertTrue(
                state.getPosY() + state.getHeight()
                        <= 240 - FloatingPanelState.BOTTOM_HUD_MARGIN + 1);
    }

    @Test
    void visibleRowsDoNotGrowWhenDraggedTowardTheTop() {
        // Regression test: the panel must not opportunistically claim more screen height just
        // because dragging it up leaves more empty space below it.
        state.setTotalRows(12);
        state.setPos(FloatingPanelState.DEFAULT_POS_X, 150);
        state.layout(WIDE_SCREEN, 400);
        int rowsNearBottom = state.getVisibleRows();

        state.setPos(FloatingPanelState.DEFAULT_POS_X, 0);
        state.layout(WIDE_SCREEN, 400);
        int rowsNearTop = state.getVisibleRows();

        assertEquals(rowsNearBottom, rowsNearTop);
    }

    @Test
    void layoutFitsAllRowsWhenTallEnough() {
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(4, state.getVisibleRows());
        assertEquals(PanelSlots.panelWidth(PanelSlots.COLS), state.getWidth());
    }

    @Test
    void scrollByClampsToMax() {
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 240);
        int max = state.getTotalRows() - state.getVisibleRows();

        state.scrollBy(100);
        assertEquals(max, state.getScrollRow());
        state.scrollBy(-100);
        assertEquals(0, state.getScrollRow());
    }

    @Test
    void defaultLayoutUsesAllNineColumns() {
        state.setTotalRows(4);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS, state.getVisibleCols());
        assertFalse(state.isSizePreferenceSet());
    }

    @Test
    void resizingColumnsLeavesRowsUnchanged() {
        state.setTotalRows(6);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(-4 * PanelSlots.SLOT_SIZE, 0);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS - 4, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());
        assertTrue(state.isSizePreferenceSet());
    }

    @Test
    void resizingRowsLeavesColumnsUnchanged() {
        state.setTotalRows(6);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(0, -3 * PanelSlots.SLOT_SIZE);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS, state.getVisibleCols());
        assertEquals(3, state.getVisibleRows());
    }

    @Test
    void resizeCannotGoBelowMinimumInEitherDimension() {
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(-50 * PanelSlots.SLOT_SIZE, -50 * PanelSlots.SLOT_SIZE);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(FloatingPanelState.MIN_VISIBLE_CELLS, state.getVisibleCols());
        assertEquals(FloatingPanelState.MIN_VISIBLE_CELLS, state.getVisibleRows());
    }

    @Test
    void resizeCannotExceedScreenFitInEitherDimension() {
        state.setTotalRows(20);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(50 * PanelSlots.SLOT_SIZE, 50 * PanelSlots.SLOT_SIZE);
        state.layout(WIDE_SCREEN, 400);

        assertEquals(PanelSlots.COLS, state.getVisibleCols());
        assertTrue(state.getVisibleRows() < 20);
    }

    @Test
    void beginResizeClearsDragging() {
        state.beginDrag(0, 0);
        assertTrue(state.isDragging());

        state.beginResize(0, 0);

        assertTrue(state.isResizing());
        assertFalse(state.isDragging());
    }

    @Test
    void resizedSizeSurvivesAnAutoFitLayoutOnAnotherScreen() {
        // Once the player has set a preference, later layout() calls must respect it (clamped),
        // not silently fall back to auto-fit.
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);
        state.beginResize(0, 0);
        state.updateResize(-2 * PanelSlots.SLOT_SIZE, -2 * PanelSlots.SLOT_SIZE);
        state.endResize();
        int cols = state.getVisibleCols();
        int rows = state.getVisibleRows();

        state.layout(WIDE_SCREEN, 400);

        assertEquals(cols, state.getVisibleCols());
        assertEquals(rows, state.getVisibleRows());
    }

    @Test
    void thePanelIsNeverSmallerThanFiveColumnsByFourRows() {
        // 016: main area minimum 3x3, plus one column on each side and one row at the bottom.
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);

        state.beginResize(0, 0);
        state.updateResize(-50 * PanelSlots.SLOT_SIZE, -50 * PanelSlots.SLOT_SIZE);
        state.endResize();
        state.layout(WIDE_SCREEN, 400);

        assertEquals(5 * PanelSlots.SLOT_SIZE + 2 * PanelSlots.PAD_X, state.getWidth());
        assertEquals(4 * PanelSlots.SLOT_SIZE + PanelSlots.PAD_TOP + PanelSlots.PAD_BOTTOM, state.getHeight());
    }

    @Test
    void resizingOnlyChangesTheMainAreaWhileTheToolAreasKeepTheirThickness() {
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);
        int[] counts = {108, 12, 12, 11};
        int sideBefore = state.layoutModel(counts).rect(pnnh.mcfloatingbackpack.inventory.PanelArea.LEFT).w();
        int bottomBefore = state.layoutModel(counts).rect(pnnh.mcfloatingbackpack.inventory.PanelArea.BOTTOM).h();

        state.beginResize(0, 0);
        state.updateResize(-4 * PanelSlots.SLOT_SIZE, -2 * PanelSlots.SLOT_SIZE);
        state.endResize();
        state.layout(WIDE_SCREEN, 400);

        PanelLayout after = state.layoutModel(counts);
        assertEquals(sideBefore, after.rect(pnnh.mcfloatingbackpack.inventory.PanelArea.LEFT).w());
        assertEquals(sideBefore, after.rect(pnnh.mcfloatingbackpack.inventory.PanelArea.RIGHT).w());
        assertEquals(bottomBefore, after.rect(pnnh.mcfloatingbackpack.inventory.PanelArea.BOTTOM).h());
        assertEquals(PanelSlots.COLS - 4, state.getVisibleCols());
        assertEquals(state.getVisibleCols() + 2, after.visibleCount(pnnh.mcfloatingbackpack.inventory.PanelArea.BOTTOM));
    }

    @Test
    void theSideColumnsAndTheBottomRowComeOffTheScreenBudget() {
        // A GUI exactly 11 cells + padding wide fits 9 main columns; one pixel less fits only 8.
        int exact = 2 * PanelSlots.PAD_X + 11 * PanelSlots.SLOT_SIZE;
        state.setTotalRows(4);
        state.layout(exact, 1000);
        assertEquals(9, state.getVisibleCols());
        state.clearSizePreference();
        state.layout(exact - 1, 1000);
        assertEquals(8, state.getVisibleCols());
    }

    // ---- 017: only sizes without filler cells ----

    @Test
    void dragTargetsWithAnInvalidColumnCountAreIgnored() {
        useIron();
        assertEquals(9, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());

        state.beginResize(0, 0);
        state.updateResize(-1 * PanelSlots.SLOT_SIZE, 0); // 8 columns: 54 / 8 leaves a half row
        assertEquals(9, state.getVisibleCols());
        state.updateResize(-2 * PanelSlots.SLOT_SIZE, 0); // 7
        assertEquals(9, state.getVisibleCols());
        state.updateResize(-3 * PanelSlots.SLOT_SIZE, 0); // 6: valid
        assertEquals(6, state.getVisibleCols());
        state.updateResize(-4 * PanelSlots.SLOT_SIZE, 0); // 5: not valid, stays at the last valid size
        assertEquals(6, state.getVisibleCols());
        state.updateResize(-5 * PanelSlots.SLOT_SIZE, 0); // 4
        assertEquals(6, state.getVisibleCols());
        state.updateResize(-6 * PanelSlots.SLOT_SIZE, 0); // 3: valid
        assertEquals(3, state.getVisibleCols());
        state.updateResize(-3 * PanelSlots.SLOT_SIZE, 0); // back to 6
        assertEquals(6, state.getVisibleCols());
    }

    @Test
    void dragTargetsWithTooManyRowsAreIgnored() {
        useIron();
        state.beginResize(0, 0);
        state.updateResize(0, 1 * PanelSlots.SLOT_SIZE); // 7 rows: the side columns only hold 6 slots
        assertEquals(6, state.getVisibleRows());
        state.updateResize(0, 5 * PanelSlots.SLOT_SIZE);
        assertEquals(6, state.getVisibleRows());
        state.updateResize(0, -2 * PanelSlots.SLOT_SIZE); // 4 rows: valid
        assertEquals(4, state.getVisibleRows());
        state.updateResize(0, 0); // back to 6
        assertEquals(6, state.getVisibleRows());
    }

    @Test
    void columnsAndRowsAreJudgedSeparately() {
        useIron();
        drag(-1, -1); // 8 columns (invalid) and 5 rows (valid)
        assertEquals(9, state.getVisibleCols());
        assertEquals(5, state.getVisibleRows());
    }

    @Test
    void theRowLimitFollowsTheSideColumnsEvenWhenTheMainAreaHasMoreRows() {
        useLeather(); // 36 slots, 4 in the side columns
        drag(-6, 3); // 3 columns would be 12 rows tall, but the side columns hold only 4
        assertEquals(3, state.getVisibleCols());
        assertEquals(4, state.getVisibleRows());
    }

    @Test
    void theSmallestSizeIsStillThreeByThree() {
        useIron();
        drag(-50, -50);
        state.endResize();
        state.layout(WIDE_SCREEN, 400);
        assertEquals(3, state.getVisibleCols());
        assertEquals(3, state.getVisibleRows());
        assertEquals(PanelSlots.panelWidth(3), state.getWidth());
        assertEquals(PanelSlots.panelHeight(3), state.getHeight());
    }

    @Test
    void withoutReportedCountsEverySizeStillWorks() {
        // Before the first sync nothing is restricted (and the old tests above rely on that).
        state.setTotalRows(12);
        state.layout(WIDE_SCREEN, 400);
        drag(-2, 0); // 7 columns
        assertEquals(7, state.getVisibleCols());
    }

    private void report(int main, int sides, int bottom) {
        state.setMainCount(main);
        state.setToolCount(PanelArea.LEFT, sides);
        state.setToolCount(PanelArea.RIGHT, sides);
        state.setToolCount(PanelArea.BOTTOM, bottom);
    }

    @Test
    void aSavedSizeThatWouldShowFillerCellsIsMovedToAValidOne() {
        report(54, 6, 11);
        state.setTotalRows(12);
        state.setSizePreference(8, 7); // rows, cols: what an older config file may hold
        state.layout(WIDE_SCREEN, 400);
        assertEquals(6, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());
        assertTrue(PanelShape.isValid(state.counts(), state.getVisibleCols(), state.getVisibleRows()));
    }

    @Test
    void autoFitTakesTheLargestValidSizeThatFitsTheScreen() {
        report(54, 6, 11);
        state.setTotalRows(6);
        // Width for exactly 8 main columns (+ 2 side columns): 8 is not valid for 54 slots, 6 is.
        state.layout(2 * PanelSlots.PAD_X + 10 * PanelSlots.SLOT_SIZE, 1000);
        assertEquals(6, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());
        state.clearSizePreference();
        state.layout(WIDE_SCREEN, 1000);
        assertEquals(9, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());
    }

    @Test
    void aTinyScreenStillGetsTheThreeByThreeMainArea() {
        report(54, 6, 11);
        state.setTotalRows(6);
        state.layout(50, 50);
        assertEquals(3, state.getVisibleCols());
        assertEquals(3, state.getVisibleRows());
    }

    @Test
    void switchingToAnotherBackpackMovesTheSizeToAValidOneForIt() {
        report(54, 6, 11);
        state.setTotalRows(12);
        state.setSizePreference(6, 6);
        state.layout(WIDE_SCREEN, 400);
        assertEquals(6, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());

        report(90, 10, 11); // diamond: 6 columns x 6 rows is valid there too
        state.layout(WIDE_SCREEN, 400);
        assertEquals(6, state.getVisibleCols());
        assertEquals(6, state.getVisibleRows());

        report(36, 4, 11); // leather: the side columns only hold 4 slots
        state.layout(WIDE_SCREEN, 400);
        assertEquals(6, state.getVisibleCols());
        assertEquals(4, state.getVisibleRows());
    }

    @Test
    void anUpgradeThatChangesTheCountsMovesAnInvalidSizeToAValidOne() {
        report(36, 4, 11);
        state.setTotalRows(12);
        state.setSizePreference(4, 4); // 4 columns divide 36
        state.layout(WIDE_SCREEN, 400);
        assertEquals(4, state.getVisibleCols());

        report(54, 6, 11); // iron: 54 / 4 leaves a half row
        state.layout(WIDE_SCREEN, 400);
        assertEquals(3, state.getVisibleCols());
        assertEquals(4, state.getVisibleRows());
    }

    @Test
    void layoutIsIdempotentOnAValidSize() {
        report(72, 8, 11);
        state.setTotalRows(12);
        state.setSizePreference(5, 8);
        state.layout(WIDE_SCREEN, 400);
        int cols = state.getVisibleCols();
        int rows = state.getVisibleRows();
        assertEquals(8, cols);
        assertEquals(5, rows);
        for (int i = 0; i < 5; i++) {
            state.layout(WIDE_SCREEN, 400);
        }
        assertEquals(cols, state.getVisibleCols());
        assertEquals(rows, state.getVisibleRows());
    }

    @Test
    void withoutValidShapesAPreferenceIsKeptAsIs() {
        report(37, 5, 11); // 37 slots: no column count divides them, nothing is restricted
        state.setTotalRows(12);
        state.setSizePreference(8, 7);
        state.layout(WIDE_SCREEN, 400);
        assertEquals(7, state.getVisibleCols());
        assertEquals(8, state.getVisibleRows());
    }
}
