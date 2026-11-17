package pnnh.mcfloatingbackpack.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.snapshot.VersionPaging.Page;

/** 057: a page of a backpack's versions, of one kind or all. */
class VersionPagingTest {
    private record V(int n, SnapshotKind kind) {
    }

    /** 3 timed, 4 change, 2 upgrade, mixed, newest first. */
    private static List<V> versions() {
        SnapshotKind[] kinds = {SnapshotKind.TIMED, SnapshotKind.CHANGE, SnapshotKind.TIMED, SnapshotKind.CHANGE, SnapshotKind.UPGRADE,
            SnapshotKind.CHANGE, SnapshotKind.TIMED, SnapshotKind.CHANGE, SnapshotKind.UPGRADE};
        List<V> list = new ArrayList<>();
        for (int i = 0; i < kinds.length; i++) {
            list.add(new V(i + 1, kinds[i]));
        }
        return list;
    }

    @Test
    void allTheVersionsComeInPagesOfTheSizeAskedFor() {
        Page<V> first = VersionPaging.page(versions(), (byte) 0, V::kind, 1, 4);
        assertEquals(4, first.items().size());
        assertEquals(1, first.page());
        assertEquals(3, first.pages());
        assertEquals(9, first.total());
        assertEquals(List.of(1, 2, 3, 4), first.items().stream().map(V::n).toList());
        assertEquals(List.of(9), VersionPaging.page(versions(), (byte) 0, V::kind, 3, 4).items().stream().map(V::n).toList());
    }

    @Test
    void aKindKeepsOnlyThatKindAndCountsOnlyThose() {
        Page<V> timed = VersionPaging.page(versions(), SnapshotKind.TIMED.code(), V::kind, 1, 5);
        assertEquals(List.of(1, 3, 7), timed.items().stream().map(V::n).toList());
        assertEquals(3, timed.total());
        assertEquals(1, timed.pages());
        assertEquals(List.of(5, 9), VersionPaging.page(versions(), SnapshotKind.UPGRADE.code(), V::kind, 1, 5).items().stream().map(V::n).toList());
        assertEquals(4, VersionPaging.page(versions(), SnapshotKind.CHANGE.code(), V::kind, 1, 2).total());
        assertEquals(2, VersionPaging.page(versions(), SnapshotKind.CHANGE.code(), V::kind, 1, 2).pages());
    }

    @Test
    void aPageThatIsGoneIsTheLastOneAndNothingIsStillOnePage() {
        Page<V> gone = VersionPaging.page(versions(), (byte) 0, V::kind, 50, 4);
        assertEquals(3, gone.page());
        assertEquals(List.of(9), gone.items().stream().map(V::n).toList());
        Page<V> none = VersionPaging.page(List.<V>of(), (byte) 0, V::kind, 1, 4);
        assertEquals(1, none.pages());
        assertEquals(1, none.page());
        assertEquals(0, none.total());
        assertEquals(List.of(), none.items());
    }

    @Test
    void aPageSizeBelowOneIsOne() {
        assertEquals(1, VersionPaging.page(versions(), (byte) 0, V::kind, 1, 0).items().size());
        assertEquals(9, VersionPaging.page(versions(), (byte) 0, V::kind, 1, -3).pages());
    }

    @Test
    void theKindButtonGoesAllTimedChangeUpgradeAndRound() {
        byte kind = 0;
        List<Byte> seen = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            kind = VersionPaging.nextKind(kind);
            seen.add(kind);
        }
        assertEquals(List.of((byte) 1, (byte) 2, (byte) 3, (byte) 0, (byte) 1), seen);
    }
}
