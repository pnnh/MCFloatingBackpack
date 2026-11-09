package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 031: the title bar's right end: the close button, the snapshot button, and where the number must stop. */
class TitleButtonsLayoutTest {
    @Test
    void theCloseButtonIsWhereItAlwaysWas() {
        // FloatingPanelState.closeButtonLeft(): posX + width - 3 - 10
        assertEquals(100 + 300 - 3 - 10, TitleButtonsLayout.of(100, 300).closeLeft());
    }

    @Test
    void theSnapshotButtonSitsLeftOfTheCloseButtonWithoutTouchingIt() {
        TitleButtonsLayout layout = TitleButtonsLayout.of(0, 300);
        assertTrue(layout.hasSnapshot());
        assertEquals(layout.closeLeft() - 2 - 10, layout.snapshotLeft());
        assertTrue(layout.snapshotLeft() + TitleButtonsLayout.BUTTON_SIZE < layout.closeLeft());
    }

    @Test
    void theNumberStopsBeforeTheSnapshotButtonWhenThereIsOne() {
        TitleButtonsLayout layout = TitleButtonsLayout.of(0, 300);
        assertEquals(layout.snapshotLeft(), layout.textRightLimit());
        int textStart = TitleIdLayout.of(0, layout.textRightLimit(), 24).x();
        assertTrue(layout.textRightLimit() - TitleIdLayout.MARGIN - textStart >= TitleButtonsLayout.MIN_TEXT);
    }

    @Test
    void aNarrowBarLeavesTheSnapshotButtonOutAndTheNumberKeepsItsRoom() {
        TitleButtonsLayout narrow = TitleButtonsLayout.of(0, 70);
        assertFalse(narrow.hasSnapshot());
        assertEquals(narrow.closeLeft(), narrow.textRightLimit());
    }

    @Test
    void theButtonIsShownExactlyWhenTheNumberStillHasItsRoom() {
        boolean seenShown = false;
        boolean seenHidden = false;
        for (int width = 40; width <= 400; width++) {
            TitleButtonsLayout layout = TitleButtonsLayout.of(0, width);
            int textStart = TitleIdLayout.ICON_OFFSET + TitleIdLayout.ICON_SIZE + TitleIdLayout.GAP;
            if (layout.hasSnapshot()) {
                seenShown = true;
                assertTrue(layout.snapshotLeft() - TitleIdLayout.MARGIN - textStart >= TitleButtonsLayout.MIN_TEXT, "width " + width);
                assertTrue(layout.snapshotLeft() > textStart, "width " + width);
            } else {
                seenHidden = true;
            }
        }
        assertTrue(seenShown && seenHidden);
    }

    @Test
    void theLayoutFollowsThePanelAcrossTheScreen() {
        TitleButtonsLayout a = TitleButtonsLayout.of(0, 200);
        TitleButtonsLayout b = TitleButtonsLayout.of(50, 200);
        assertEquals(a.closeLeft() + 50, b.closeLeft());
        assertEquals(a.snapshotLeft() + 50, b.snapshotLeft());
        assertEquals(a.textRightLimit() + 50, b.textRightLimit());
    }
}
