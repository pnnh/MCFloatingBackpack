package pnnh.mcfloatingbackpack.client.panel.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.ToIntFunction;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Hit;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Kind;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout.Rect;

class TagPanelLayoutTest {
    private static final ToIntFunction<String> SIX = s -> s.length() * 6;
    /** A 6x4 cell main area: 108 x 72 pixels. */
    private static final int W = 108;
    private static final int H = 72;

    private static TagPanelLayout layout(List<String> tags, boolean editing, int dropdown, int scroll, int pending) {
        return TagPanelLayout.of(W, H, SIX, tags, "none", editing, dropdown, scroll, pending);
    }

    private static boolean inside(Rect r, int w, int h) {
        return r.x() >= 0 && r.y() >= 0 && r.x() + r.w() <= w && r.y() + r.h() <= h;
    }

    @Test
    void statusRowIsTheFirstRow() {
        Rect status = layout(List.of(), false, 0, 0, -1).statusRect();
        assertEquals(new Rect(0, 0, W, 18), status);
    }

    @Test
    void emptyTagListShowsTheNoneLabelAndThePlus() {
        TagPanelLayout l = layout(List.of(), false, 0, 0, -1);
        assertNotNull(l.noneRect());
        assertNotNull(l.plusRect());
        assertTrue(l.plusRect().x() > l.noneRect().x());
        assertNull(l.textBoxRect());
    }

    @Test
    void tagBlockWidthIsTheTextWidthPlusPadding() {
        TagPanelLayout l = layout(List.of("wood"), false, 0, 0, -1);
        assertEquals(4 * 6 + 8, l.tagRect(0).w());
        assertEquals(14, l.tagRect(0).h());
        assertEquals(18, l.tagRect(0).y() - 2, "first content row starts under the status row");
    }

    @Test
    void tagsFlowAndWrapToTheNextRow() {
        // "abcdefgh" = 48 + 8 = 56 wide; two of them (+ gap) do not fit in 108 - 2*2
        TagPanelLayout l = layout(List.of("abcdefgh", "abcdefgh", "abcdefgh"), false, 0, 0, -1);
        assertTrue(l.tagRect(1).y() > l.tagRect(0).y(), "the second tag wrapped");
        assertTrue(l.tagRect(2).y() > l.tagRect(1).y(), "the third tag wrapped");
        assertEquals(l.tagRect(0).x(), l.tagRect(1).x());
    }

    @Test
    void plusFollowsTheLastTagOrWraps() {
        TagPanelLayout one = layout(List.of("wood"), false, 0, 0, -1);
        assertEquals(one.tagRect(0).y(), one.plusRect().y());
        assertTrue(one.plusRect().x() > one.tagRect(0).x() + one.tagRect(0).w());
        TagPanelLayout wide = layout(List.of("abcdefgh", "abcdefgh"), false, 0, 0, -1);
        assertTrue(wide.plusRect().y() >= wide.tagRect(1).y());
    }

    @Test
    void editingReplacesThePlusWithAFullWidthTextBoxOnItsOwnRow() {
        TagPanelLayout l = layout(List.of("wood"), true, 3, 0, -1);
        assertNull(l.plusRect());
        Rect box = l.textBoxRect();
        assertNotNull(box);
        assertEquals(W - 4, box.w());
        assertTrue(box.y() > l.tagRect(0).y(), "the text box is on a row of its own");
        assertEquals(3, l.dropdownCount());
        for (int i = 0; i < 3; i++) {
            assertTrue(l.dropdownRect(i).y() >= box.y() + box.h());
        }
        assertTrue(l.dropdownRect(1).y() > l.dropdownRect(0).y());
    }

    @Test
    void theDropdownShowsAtMostFiveEntries() {
        assertEquals(5, layout(List.of(), true, 9, 0, -1).dropdownCount());
        assertEquals(0, layout(List.of(), false, 4, 0, -1).dropdownCount());
    }

    @Test
    void aPendingDeleteTagGetsAMarkAreaOnItsRight() {
        TagPanelLayout l = layout(List.of("wood", "build"), false, 0, 0, 0);
        Rect mark = l.deleteRect();
        assertNotNull(mark);
        assertEquals(l.tagRect(0).x() + l.tagRect(0).w(), mark.x());
        assertTrue(l.tagRect(1).x() >= mark.x() + mark.w());
        assertNull(layout(List.of("wood"), false, 0, 0, -1).deleteRect());
    }

    @Test
    void longContentScrollsAndScrollingMovesThingsUp() {
        List<String> many = java.util.Collections.nCopies(12, "abcdefgh");
        TagPanelLayout top = layout(many, false, 0, 0, -1);
        assertTrue(top.maxScroll() > 0);
        TagPanelLayout scrolled = layout(many, false, 0, 1, -1);
        assertEquals(top.tagRect(0).y() - 18, scrolled.tagRect(0).y());
        assertEquals(0, layout(List.of("wood"), false, 0, 0, -1).maxScroll());
    }

    @Test
    void hitTestFindsEachKindOfElement() {
        TagPanelLayout l = layout(List.of("wood", "build"), true, 2, 0, 0);
        Rect t0 = l.tagRect(0);
        assertEquals(new Hit(Kind.TAG, 0), l.hit(t0.x() + 1, t0.y() + 1));
        Rect mark = l.deleteRect();
        assertEquals(new Hit(Kind.DELETE, 0), l.hit(mark.x() + 1, mark.y() + 1));
        Rect box = l.textBoxRect();
        assertEquals(new Hit(Kind.TEXTBOX, 0), l.hit(box.x() + 3, box.y() + 3));
        Rect d1 = l.dropdownRect(1);
        assertEquals(new Hit(Kind.DROPDOWN, 1), l.hit(d1.x() + 3, d1.y() + 3));
        assertEquals(Kind.STATUS, l.hit(5, 5).kind());
        assertEquals(Kind.NONE, l.hit(W - 1, H - 1).kind());
        assertEquals(Kind.NONE, l.hit(-5, 20).kind());

        TagPanelLayout plus = layout(List.of("wood"), false, 0, 0, -1);
        Rect p = plus.plusRect();
        assertEquals(new Hit(Kind.PLUS, 0), plus.hit(p.x() + 2, p.y() + 2));
    }

    @Test
    void scrolledOutElementsCannotBeHit() {
        List<String> many = java.util.Collections.nCopies(12, "abcdefgh");
        TagPanelLayout l = layout(many, false, 0, 1, -1);
        // the first row has scrolled up under the status row
        assertEquals(Kind.STATUS, l.hit(10, 10).kind());
        Rect t0 = l.tagRect(0);
        assertTrue(t0.y() < 18);
        assertEquals(Kind.NONE, l.hit(t0.x() + 1, 18 + 40000).kind());
    }

    @Test
    void theSmallestMainAreaStillLaysOut() {
        TagPanelLayout l = TagPanelLayout.of(54, 54, SIX, List.of("wood"), "none", false, 0, 0, -1);
        assertTrue(inside(l.statusRect(), 54, 54));
        assertTrue(inside(l.plusRect(), 54, 54) || l.maxScroll() > 0);
        TagPanelLayout editing = TagPanelLayout.of(54, 54, SIX, List.of(), "none", true, 5, 0, -1);
        assertEquals(54 - 4, editing.textBoxRect().w());
        assertTrue(editing.maxScroll() > 0, "the drop-down does not fit, so the panel scrolls");
    }
}
