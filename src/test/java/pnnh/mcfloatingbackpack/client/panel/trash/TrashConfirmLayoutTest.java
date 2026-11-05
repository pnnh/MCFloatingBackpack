package pnnh.mcfloatingbackpack.client.panel.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmLayout.Hit;

/** 022: the mask over the whole floating panel, the dialog in its middle and the two buttons. */
class TrashConfirmLayoutTest {
    private static final int LINE = 9;
    /** The smallest panel: three columns and three rows of the main area plus the tool areas. */
    private static final int MIN_W = PanelLayout.width(3);
    private static final int MIN_H = PanelLayout.height(3);

    private static TrashConfirmLayout layout(int x, int y, int w, int h, int lines) {
        return TrashConfirmLayout.of(x, y, w, h, lines, LINE);
    }

    private static boolean inside(Rect inner, Rect outer) {
        return inner.x() >= outer.x()
                && inner.y() >= outer.y()
                && inner.x() + inner.w() <= outer.x() + outer.w()
                && inner.y() + inner.h() <= outer.y() + outer.h();
    }

    private static boolean apart(Rect a, Rect b) {
        return a.x() + a.w() <= b.x() || b.x() + b.w() <= a.x() || a.y() + a.h() <= b.y() || b.y() + b.h() <= a.y();
    }

    @Test
    void theMaskIsTheWholePanel() {
        assertEquals(new Rect(20, 30, 200, 120), layout(20, 30, 200, 120, 2).mask());
    }

    @Test
    void theDialogIsCenteredAndInsideTheMask() {
        TrashConfirmLayout layout = layout(20, 30, 200, 120, 2);
        Rect dialog = layout.dialog();
        assertTrue(inside(dialog, layout.mask()));
        assertEquals(120, dialog.w());
        assertEquals(20 + (200 - dialog.w()) / 2, dialog.x());
        assertEquals(30 + (120 - dialog.h()) / 2, dialog.y());
    }

    @Test
    void theButtonsSitInsideTheDialogSideBySideAtItsBottom() {
        TrashConfirmLayout layout = layout(20, 30, 200, 120, 2);
        Rect dialog = layout.dialog();
        Rect yes = layout.confirmButton();
        Rect no = layout.cancelButton();
        assertTrue(inside(yes, dialog));
        assertTrue(inside(no, dialog));
        assertTrue(apart(yes, no));
        assertEquals(yes.y(), no.y());
        assertEquals(yes.w(), no.w());
        assertEquals(TrashConfirmLayout.BUTTON_H, yes.h());
        assertTrue(yes.x() < no.x());
        assertTrue(yes.y() > dialog.y() + 2 * LINE);
    }

    @Test
    void theSmallestPanelStillHoldsThreeLinesAndTheButtons() {
        for (int lines = 1; lines <= 3; lines++) {
            TrashConfirmLayout layout = layout(0, 0, MIN_W, MIN_H, lines);
            Rect dialog = layout.dialog();
            assertTrue(inside(dialog, layout.mask()), "lines " + lines);
            assertTrue(inside(layout.confirmButton(), dialog), "lines " + lines);
            assertTrue(inside(layout.cancelButton(), dialog), "lines " + lines);
            assertTrue(apart(layout.confirmButton(), layout.cancelButton()), "lines " + lines);
            assertTrue(layout.confirmButton().w() >= 30, "buttons stay wide enough for their label");
        }
    }

    @Test
    void theDialogLeavesAMarginOnANarrowPanel() {
        TrashConfirmLayout layout = layout(0, 0, MIN_W, MIN_H, 2);
        assertEquals(MIN_W - 2 * TrashConfirmLayout.SIDE_MARGIN, layout.dialog().w());
    }

    @Test
    void hitTellsTheButtonsTheDialogTheMaskAndTheOutside() {
        TrashConfirmLayout layout = layout(20, 30, 200, 120, 2);
        Rect yes = layout.confirmButton();
        Rect no = layout.cancelButton();
        Rect dialog = layout.dialog();
        assertEquals(Hit.CONFIRM, layout.hit(yes.x() + 1, yes.y() + 1));
        assertEquals(Hit.CANCEL, layout.hit(no.x() + no.w() - 1, no.y() + no.h() - 1));
        assertEquals(Hit.DIALOG, layout.hit(dialog.x() + 2, dialog.y() + 2));
        assertEquals(Hit.MASK, layout.hit(20 + 1, 30 + 1));
        assertEquals(Hit.OUTSIDE, layout.hit(19, 30));
        assertEquals(Hit.OUTSIDE, layout.hit(20 + 200, 30 + 10));
        assertEquals(Hit.OUTSIDE, layout.hit(-5, -5));
    }

    @Test
    void movesWithThePanel() {
        Rect a = layout(20, 30, 200, 120, 2).dialog();
        Rect b = layout(50, 10, 200, 120, 2).dialog();
        assertEquals(30, b.x() - a.x());
        assertEquals(-20, b.y() - a.y());
        assertEquals(a.w(), b.w());
        assertEquals(a.h(), b.h());
    }
}
