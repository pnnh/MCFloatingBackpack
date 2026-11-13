package pnnh.mcfloatingbackpack.client.panel.jukebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelLayout.Button;
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelLayout.Toggle;

class JukeboxPanelLayoutTest {
    private final JukeboxPanelLayout layout = JukeboxPanelLayout.of(100, 40, 200);

    private static boolean inside(Rect outer, Rect inner) {
        return inner.x() >= outer.x() && inner.y() >= outer.y() && inner.x() + inner.w() <= outer.x() + outer.w()
                && inner.y() + inner.h() <= outer.y() + outer.h();
    }

    private static boolean overlap(Rect a, Rect b) {
        return a.x() < b.x() + b.w() && b.x() < a.x() + a.w() && a.y() < b.y() + b.h() && b.y() < a.y() + a.h();
    }

    @Test
    void thePageHangsOffTheRightEdgeAlignedWithTheTop() {
        Rect frame = layout.rect();
        assertEquals(100 + 200 + 1, frame.x(), "one pixel in from the panel's edge, like the trash can page");
        assertEquals(40, frame.y());
        assertEquals(JukeboxPanelLayout.WIDTH, frame.w());
        assertEquals(JukeboxPanelLayout.HEIGHT, frame.h());
    }

    @Test
    void everythingIsInsideTheFrameAndNothingOverlaps() {
        Rect frame = layout.rect();
        assertTrue(inside(frame, layout.titleRect()));
        assertTrue(inside(frame, layout.nameRect()));
        assertTrue(inside(frame, layout.countRect()));
        java.util.List<Rect> all = new java.util.ArrayList<>(java.util.List.of(layout.titleRect(), layout.nameRect(), layout.countRect()));
        for (Button b : Button.values()) {
            assertTrue(inside(frame, layout.buttonRect(b)), b.name());
            all.add(layout.buttonRect(b));
        }
        for (Toggle t : Toggle.values()) {
            assertTrue(inside(frame, layout.toggleRect(t)), t.name());
            all.add(layout.toggleRect(t));
        }
        for (int i = 0; i < all.size(); i++) {
            for (int j = i + 1; j < all.size(); j++) {
                assertFalse(overlap(all.get(i), all.get(j)), i + " and " + j + " overlap");
            }
        }
    }

    @Test
    void theButtonsAreInTheOrderOfTheEnumLeftToRightInOneRow() {
        int lastX = -1;
        for (Button b : Button.values()) {
            Rect r = layout.buttonRect(b);
            assertTrue(r.x() > lastX, b.name());
            assertEquals(layout.buttonRect(Button.PREVIOUS).y(), r.y());
            lastX = r.x();
        }
    }

    @Test
    void theButtonRowIsCentred() {
        Rect first = layout.buttonRect(Button.PREVIOUS);
        Rect last = layout.buttonRect(Button.NEXT);
        Rect frame = layout.rect();
        assertEquals(first.x() - frame.x(), frame.x() + frame.w() - (last.x() + last.w()), 1);
    }

    @Test
    void aPointInEachButtonOrSwitchFindsIt() {
        for (Button b : Button.values()) {
            Rect r = layout.buttonRect(b);
            assertEquals(b, layout.buttonAt(r.x() + r.w() / 2.0, r.y() + r.h() / 2.0));
            assertNull(layout.toggleAt(r.x() + r.w() / 2.0, r.y() + r.h() / 2.0));
        }
        for (Toggle t : Toggle.values()) {
            Rect r = layout.toggleRect(t);
            assertEquals(t, layout.toggleAt(r.x() + r.w() / 2.0, r.y() + r.h() / 2.0));
            assertNull(layout.buttonAt(r.x() + r.w() / 2.0, r.y() + r.h() / 2.0));
        }
    }

    @Test
    void thePointsOutsideTheControlsFindNothing() {
        assertNull(layout.buttonAt(layout.rect().x() + 1, layout.rect().y() + 1), "the title bar");
        assertNull(layout.toggleAt(layout.rect().x() - 5, layout.rect().y() + JukeboxPanelLayout.TOGGLE_Y + 2), "left of the page");
        assertFalse(layout.contains(layout.rect().x() - 1, layout.rect().y() + 5));
        assertTrue(layout.contains(layout.rect().x(), layout.rect().y()));
        assertFalse(layout.contains(layout.rect().x() + JukeboxPanelLayout.WIDTH, layout.rect().y()));
    }
}
