package pnnh.mcfloatingbackpack.client.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenLayout.Layout;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenLayout.Rect;

/** 031: the window fits every GUI size a screen can give it. */
class SnapshotScreenLayoutTest {
    /** 1920x1080 and 1280x720 windows at GUI scales 1 to 4, and two small ones. */
    private static final int[][] SIZES = {
        {1920, 1080}, {960, 540}, {640, 360}, {480, 270}, {1280, 720}, {426, 240}, {320, 180}, {400, 240}, {300, 200}
    };

    private static boolean inside(Rect inner, Rect outer) {
        return inner.isEmpty() || (inner.x() >= outer.x() && inner.y() >= outer.y() && inner.right() <= outer.right() && inner.bottom() <= outer.bottom());
    }

    private static boolean apart(Rect a, Rect b) {
        return a.isEmpty() || b.isEmpty() || a.right() <= b.x() || b.right() <= a.x() || a.bottom() <= b.y() || b.bottom() <= a.y();
    }

    @Test
    void everythingIsInsideTheScreenAndTheWindowAtEverySize() {
        for (int[] size : SIZES) {
            for (boolean op : new boolean[] {false, true}) {
                Layout l = SnapshotScreenLayout.of(size[0], size[1], op);
                Rect screen = new Rect(0, 0, size[0], size[1]);
                String what = size[0] + "x" + size[1] + " op=" + op;
                assertTrue(inside(l.dialog(), screen), "dialog " + what);
                for (Rect part : new Rect[] {l.close(), l.opRow(), l.list(), l.pager(), l.detail(), l.versions(), l.preview(), l.actions(), l.status()}) {
                    assertTrue(inside(part, l.dialog()), "a part of the window leaves it at " + what + ": " + part);
                    assertTrue(inside(part, screen), "a part leaves the screen at " + what + ": " + part);
                }
            }
        }
    }

    @Test
    void theWindowIsAtMost400By260AndCentred() {
        Layout l = SnapshotScreenLayout.of(1920, 1080, true);
        assertEquals(400, l.dialog().w());
        assertEquals(260, l.dialog().h());
        assertEquals((1920 - 400) / 2, l.dialog().x());
        assertEquals((1080 - 260) / 2, l.dialog().y());
    }

    @Test
    void thePartsDoNotOverlap() {
        for (int[] size : SIZES) {
            for (boolean op : new boolean[] {false, true}) {
                Layout l = SnapshotScreenLayout.of(size[0], size[1], op);
                String what = size[0] + "x" + size[1] + " op=" + op;
                assertTrue(apart(l.list(), l.detail()), "list and detail " + what);
                assertTrue(apart(l.opRow(), l.list()), "op row and list " + what);
                assertTrue(apart(l.opRow(), l.detail()), "op row and detail " + what);
                assertTrue(apart(l.actions(), l.list()), "actions and list " + what);
                assertTrue(apart(l.actions(), l.detail()), "actions and detail " + what);
                assertTrue(apart(l.status(), l.list()), "status and list " + what);
                assertTrue(apart(l.status(), l.detail()), "status and detail " + what);
                assertTrue(apart(l.status(), l.actions()), "status and actions " + what);
                assertTrue(apart(l.versions(), l.preview()), "versions and preview " + what);
            }
        }
    }

    @Test
    void aPlayerWithoutPermissionHasNoRoomForOpControls() {
        Layout l = SnapshotScreenLayout.of(960, 540, false);
        assertTrue(l.opRow().isEmpty());
        assertTrue(l.actions().isEmpty());
        Layout op = SnapshotScreenLayout.of(960, 540, true);
        assertFalse(op.opRow().isEmpty());
        assertFalse(op.actions().isEmpty());
        assertTrue(op.list().h() < l.list().h(), "the op controls take room from the list");
    }

    @Test
    void thereIsAlwaysAtLeastOneListRowAndTheRowsFitWhenThereIsRoom() {
        for (int[] size : SIZES) {
            Layout l = SnapshotScreenLayout.of(size[0], size[1], true);
            assertTrue(l.listRows() >= 1, size[0] + "x" + size[1]);
        }
        Layout big = SnapshotScreenLayout.of(960, 540, false);
        assertTrue(big.listRows() * SnapshotScreenLayout.ROW_H <= big.list().h());
        assertTrue(big.listRows() <= 16);
    }

    @Test
    void thePreviewShowsAtLeastOneGridRowWhenTheGuiIsNotTiny() {
        for (int[] size : SIZES) {
            if (size[1] >= 240) {
                assertTrue(SnapshotScreenLayout.of(size[0], size[1], false).previewRows() >= 1, size[0] + "x" + size[1]);
            }
        }
        Layout l = SnapshotScreenLayout.of(480, 270, true);
        assertTrue(l.previewRows() >= 3, "480x270 with op controls: " + l.previewRows());
    }

    @Test
    void theGridIsNineCellsWideAndFitsItsArea() {
        for (int[] size : SIZES) {
            Layout l = SnapshotScreenLayout.of(size[0], size[1], false);
            assertTrue(SnapshotScreenLayout.GRID_COLS * l.slot() <= Math.max(l.preview().w(), SnapshotScreenLayout.GRID_COLS * SnapshotScreenLayout.MIN_SLOT));
            assertTrue(l.slot() >= SnapshotScreenLayout.MIN_SLOT && l.slot() <= SnapshotScreenLayout.MAX_SLOT);
        }
    }

    @Test
    void gridRowsAndScrolling() {
        assertEquals(12, SnapshotScreenLayout.gridRows(108));
        assertEquals(4, SnapshotScreenLayout.gridRows(36));
        assertEquals(0, SnapshotScreenLayout.gridRows(0));
        assertEquals(7, SnapshotScreenLayout.maxScroll(108, 5));
        assertEquals(0, SnapshotScreenLayout.maxScroll(36, 5));
        assertEquals(11, SnapshotScreenLayout.maxScroll(108, 0), "at least one row is taken to be visible");
    }
}
