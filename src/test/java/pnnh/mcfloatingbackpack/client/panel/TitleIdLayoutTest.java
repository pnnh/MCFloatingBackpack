package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 026: where the backpack's number goes in the panel's title bar, to the right of the tier icon. */
class TitleIdLayoutTest {
    @Test
    void startsRightOfTheIcon() {
        TitleIdLayout layout = TitleIdLayout.of(100, 300, 20);
        assertEquals(100 + 4 + 16 + 3, layout.x());
    }

    @Test
    void theRoomEndsTwoPixelsBeforeTheCloseButton() {
        TitleIdLayout layout = TitleIdLayout.of(100, 300, 20);
        assertEquals(300 - layout.x() - 2, layout.maxWidth());
        assertFalse(layout.truncated());
    }

    @Test
    void aTextWiderThanTheRoomIsTruncated() {
        TitleIdLayout layout = TitleIdLayout.of(100, 140, 40);
        assertEquals(140 - 123 - 2, layout.maxWidth());
        assertTrue(layout.truncated());
        assertFalse(TitleIdLayout.of(100, 140, layout.maxWidth()).truncated());
    }

    @Test
    void aVeryNarrowPanelLeavesNoRoom() {
        TitleIdLayout layout = TitleIdLayout.of(100, 110, 20);
        assertEquals(0, layout.maxWidth());
        assertTrue(layout.truncated());
    }

    @Test
    void movesWithThePanel() {
        TitleIdLayout a = TitleIdLayout.of(0, 200, 20);
        TitleIdLayout b = TitleIdLayout.of(50, 250, 20);
        assertEquals(a.x() + 50, b.x());
        assertEquals(a.maxWidth(), b.maxWidth());
    }
}
