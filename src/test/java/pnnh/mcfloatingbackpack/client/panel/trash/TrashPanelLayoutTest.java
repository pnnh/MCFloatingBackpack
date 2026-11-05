package pnnh.mcfloatingbackpack.client.panel.trash;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;

/** 021: where the trash can panel sits and which part of it a point is on. */
class TrashPanelLayoutTest {
    private static TrashPanelLayout layoutAt(int panelX, int panelY, int panelWidth) {
        return TrashPanelLayout.of(panelX, panelY, panelWidth);
    }

    @Test
    void hangsOffTheRightEdgeWithTheTopsAligned() {
        TrashPanelLayout layout = layoutAt(20, 30, 200);
        assertEquals(new Rect(221, 30, 62, 72), layout.rect());
    }

    @Test
    void titleBarIsTheTopStrip() {
        TrashPanelLayout layout = layoutAt(20, 30, 200);
        assertEquals(new Rect(221, 30, 62, 14), layout.titleRect());
    }

    @Test
    void nineCellsLeftToRightTopToBottomInsideTheFrame() {
        TrashPanelLayout layout = layoutAt(0, 0, 100);
        Rect frame = layout.rect();
        for (int i = 0; i < 9; i++) {
            Rect cell = layout.cellRect(i);
            assertEquals(PanelLayout.SLOT, cell.w());
            assertEquals(PanelLayout.SLOT, cell.h());
            assertTrue(cell.x() >= frame.x() && cell.x() + cell.w() <= frame.x() + frame.w(), "cell " + i);
            assertTrue(cell.y() >= frame.y() + TrashPanelLayout.TITLE_H && cell.y() + cell.h() <= frame.y() + frame.h(), "cell " + i);
            for (int j = i + 1; j < 9; j++) {
                Rect other = layout.cellRect(j);
                boolean apart = cell.x() + cell.w() <= other.x() || other.x() + other.w() <= cell.x()
                        || cell.y() + cell.h() <= other.y() || other.y() + other.h() <= cell.y();
                assertTrue(apart, "cells " + i + " and " + j + " overlap");
            }
        }
        assertEquals(layout.cellRect(0).y(), layout.cellRect(2).y());
        assertEquals(layout.cellRect(0).x(), layout.cellRect(3).x());
        assertTrue(layout.cellRect(1).x() > layout.cellRect(0).x());
        assertTrue(layout.cellRect(3).y() > layout.cellRect(0).y());
    }

    @Test
    void cellAtFindsEachCellAndNothingElse() {
        TrashPanelLayout layout = layoutAt(10, 10, 150);
        for (int i = 0; i < 9; i++) {
            Rect cell = layout.cellRect(i);
            assertEquals(i, layout.cellAt(cell.x(), cell.y()));
            assertEquals(i, layout.cellAt(cell.x() + cell.w() - 1, cell.y() + cell.h() - 1));
            assertEquals(i, layout.cellAt(cell.x() + 5, cell.y() + 5));
        }
        Rect title = layout.titleRect();
        assertEquals(-1, layout.cellAt(title.x() + 3, title.y() + 3));
        Rect frame = layout.rect();
        assertEquals(-1, layout.cellAt(frame.x() + 1, frame.y() + frame.h() - 1));
        assertEquals(-1, layout.cellAt(frame.x() - 1, frame.y() + 30));
    }

    @Test
    void containsCoversTheWholeFrame() {
        TrashPanelLayout layout = layoutAt(20, 30, 200);
        Rect frame = layout.rect();
        assertTrue(layout.contains(frame.x(), frame.y()));
        assertTrue(layout.contains(frame.x() + frame.w() - 1, frame.y() + frame.h() - 1));
        assertTrue(layout.contains(frame.x() + 2, frame.y() + 2));
        assertFalse(layout.contains(frame.x() - 1, frame.y() + 10));
        assertFalse(layout.contains(frame.x() + frame.w(), frame.y() + 10));
        assertFalse(layout.contains(frame.x() + 10, frame.y() - 1));
        assertFalse(layout.contains(frame.x() + 10, frame.y() + frame.h()));
    }

    @Test
    void movesWithThePanel() {
        Rect a = layoutAt(20, 30, 200).rect();
        Rect b = layoutAt(50, 10, 200).rect();
        assertEquals(30, b.x() - a.x());
        assertEquals(-20, b.y() - a.y());
        assertEquals(a.w(), b.w());
        assertEquals(a.h(), b.h());
    }

    @Test
    void neverOverlapsThePanelItHangsOn() {
        for (int width : new int[] {100, 150, 200, 322}) {
            TrashPanelLayout layout = layoutAt(40, 40, width);
            assertFalse(layout.contains(40 + width - 1, 50), "width " + width);
            assertTrue(layout.rect().x() >= 40 + width);
        }
    }
}
