package pnnh.mcfloatingbackpack.jukebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.jukebox.JukeboxPlayer.Command;
import pnnh.mcfloatingbackpack.jukebox.JukeboxPlayer.Kind;

class JukeboxPlayerTest {
    private static final List<String> ABC = List.of("a", "b", "c");

    private static JukeboxPlayer player() {
        return new JukeboxPlayer(new Random(53));
    }

    private static void assertStart(Command command, int index, String key) {
        assertEquals(Kind.START, command.kind());
        assertEquals(index, command.index());
        assertEquals(key, command.key());
    }

    // ---- in order ----

    @Test
    void playingBeginsWithTheFirstTrack() {
        JukeboxPlayer p = player();
        assertStart(p.play(ABC), 0, "a");
        assertTrue(p.playing());
    }

    @Test
    void nextGoesOnAndTheLastIsFollowedByTheFirst() {
        JukeboxPlayer p = player();
        p.play(ABC);
        assertStart(p.next(ABC), 1, "b");
        assertStart(p.next(ABC), 2, "c");
        assertStart(p.next(ABC), 0, "a");
    }

    @Test
    void previousGoesBackAndTheFirstIsPrecededByTheLast() {
        JukeboxPlayer p = player();
        p.play(ABC);
        assertStart(p.previous(ABC), 2, "c");
        assertStart(p.previous(ABC), 1, "b");
    }

    @Test
    void aTrackThatHasEndedIsFollowedByTheNext() {
        JukeboxPlayer p = player();
        p.play(ABC);
        assertStart(p.finished(ABC), 1, "b");
    }

    @Test
    void stoppingKeepsTheTrackAndPlayingAgainStartsItFromItsStart() {
        JukeboxPlayer p = player();
        p.play(ABC);
        p.next(ABC);
        assertEquals(Kind.STOP, p.stop().kind());
        assertFalse(p.playing());
        assertEquals("b", p.currentKey());
        assertStart(p.play(ABC), 1, "b");
        assertTrue(p.playing());
    }

    @Test
    void rewindingStartsTheCurrentTrackAgainAlsoWhilePlaying() {
        JukeboxPlayer p = player();
        p.play(ABC);
        p.next(ABC);
        assertStart(p.rewind(ABC), 1, "b");
        p.stop();
        assertStart(p.rewind(ABC), 1, "b");
    }

    // ---- repeat one ----

    @Test
    void withRepeatOneTheEndedTrackPlaysAgainButNextStillGoesOn() {
        JukeboxPlayer p = player();
        p.setLoopOne(true);
        p.play(ABC);
        assertStart(p.finished(ABC), 0, "a");
        assertStart(p.finished(ABC), 0, "a");
        assertStart(p.next(ABC), 1, "b");
        assertStart(p.finished(ABC), 1, "b");
        p.setLoopOne(false);
        assertStart(p.finished(ABC), 2, "c");
    }

    // ---- shuffle ----

    /** Plays {@code count} tracks of {@code n} in shuffle mode; the places in the order they were played. */
    private static List<Integer> shuffled(JukeboxPlayer p, int n, int count) {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add("t" + i);
        }
        p.setShuffle(true);
        List<Integer> played = new ArrayList<>();
        played.add(p.play(list).index());
        for (int i = 1; i < count; i++) {
            played.add(p.finished(list).index());
        }
        return played;
    }

    @Test
    void everyRoundOfAShuffleHasEveryTrackExactlyOnce() {
        for (int seed = 0; seed < 1000; seed++) {
            int n = 2 + seed % 9;
            JukeboxPlayer p = new JukeboxPlayer(new Random(seed));
            List<Integer> played = shuffled(p, n, n * 5);
            for (int round = 0; round < 5; round++) {
                assertEquals(n, new HashSet<>(played.subList(round * n, round * n + n)).size(), "seed " + seed + " round " + round);
            }
        }
    }

    @Test
    void aNewRoundDoesNotBeginWithTheTrackThatEndedTheLastOne() {
        for (int seed = 0; seed < 1000; seed++) {
            int n = 2 + seed % 6;
            JukeboxPlayer p = new JukeboxPlayer(new Random(seed));
            List<Integer> played = shuffled(p, n, n * 6);
            for (int round = 1; round < 6; round++) {
                assertNotEquals(played.get(round * n - 1), played.get(round * n), "seed " + seed + " round " + round);
            }
        }
    }

    @Test
    void aSingleTrackShufflesToItself() {
        JukeboxPlayer p = player();
        p.setShuffle(true);
        assertStart(p.play(List.of("only")), 0, "only");
        assertStart(p.finished(List.of("only")), 0, "only");
        assertStart(p.next(List.of("only")), 0, "only");
    }

    @Test
    void previousInAShuffleGoesBackToTheTrackBeforeAndNextComesBackToTheOneLeft() {
        JukeboxPlayer p = new JukeboxPlayer(new Random(7));
        List<String> list = List.of("a", "b", "c", "d", "e");
        p.setShuffle(true);
        int first = p.play(list).index();
        int second = p.next(list).index();
        int third = p.next(list).index();
        assertEquals(second, p.previous(list).index());
        assertEquals(first, p.previous(list).index());
        assertEquals(second, p.next(list).index(), "going on from the first goes to the second again");
        assertEquals(third, p.next(list).index());
    }

    @Test
    void previousWithNoHistoryInAShuffleReplaysTheCurrentTrack() {
        JukeboxPlayer p = new JukeboxPlayer(new Random(3));
        p.setShuffle(true);
        int first = p.play(ABC).index();
        assertEquals(first, p.previous(ABC).index());
    }

    @Test
    void switchingShuffleOffGoesBackToTheOrderFromTheCurrentTrack() {
        JukeboxPlayer p = new JukeboxPlayer(new Random(11));
        p.setShuffle(true);
        int at = p.play(ABC).index();
        p.setShuffle(false);
        assertEquals((at + 1) % 3, p.next(ABC).index());
    }

    // ---- the list changes ----

    @Test
    void anEmptyListStopsEverything() {
        JukeboxPlayer p = player();
        p.play(ABC);
        assertEquals(Kind.STOP, p.next(List.of()).kind());
        assertFalse(p.playing());
        assertNull(p.currentKey());
        assertEquals(Kind.STOP, p.play(List.of()).kind());
        assertEquals(Kind.STOP, p.previous(List.of()).kind());
        assertEquals(Kind.STOP, p.finished(List.of()).kind());
    }

    @Test
    void theCurrentTrackIsFoundAgainByItsKeyWhenTheListMovesAround() {
        JukeboxPlayer p = player();
        p.play(ABC);
        p.next(ABC); // b
        List<String> changed = List.of("x", "a", "b", "c");
        assertEquals(2, p.currentIndex(changed));
        assertStart(p.next(changed), 3, "c");
    }

    @Test
    void whenTheCurrentDiscIsTakenOutTheOneThatMovedIntoItsPlaceIsNext() {
        JukeboxPlayer p = player();
        p.play(ABC);
        p.next(ABC); // b, at 1
        assertStart(p.next(List.of("a", "c", "d")), 1, "c");
    }

    @Test
    void whenTheLastTrackIsTakenOutNextWrapsRound() {
        JukeboxPlayer p = player();
        p.play(ABC);
        p.next(ABC);
        p.next(ABC); // c, at 2
        assertStart(p.next(List.of("a", "b")), 0, "a");
    }

    @Test
    void twoDiscsOfTheSameSongAreTwoPlacesAndTheCurrentPlaceIsKept() {
        JukeboxPlayer p = player();
        List<String> list = List.of("a", "a", "b");
        p.play(list);
        assertStart(p.next(list), 1, "a");
        assertStart(p.next(list), 2, "b");
        assertStart(p.next(list), 0, "a");
    }

    @Test
    void aShuffleKeepsGoingWhenTheListGrows() {
        JukeboxPlayer p = new JukeboxPlayer(new Random(5));
        p.setShuffle(true);
        p.play(ABC);
        List<String> more = List.of("a", "b", "c", "d", "e");
        HashSet<Integer> seen = new HashSet<>();
        seen.add(p.currentIndex(more));
        for (int i = 0; i < 4; i++) {
            seen.add(p.next(more).index());
        }
        assertEquals(5, seen.size(), "after the list grew the next round has every track once");
    }

    @Test
    void resetPutsEverythingBack() {
        JukeboxPlayer p = player();
        p.setLoopOne(true);
        p.setShuffle(true);
        p.play(ABC);
        p.reset();
        assertFalse(p.playing());
        assertFalse(p.loopOne());
        assertFalse(p.shuffle());
        assertNull(p.currentKey());
        assertStart(p.play(ABC), 0, "a");
    }

    @Test
    void nothingPlayedYetNextAndPreviousBeginAtTheStart() {
        JukeboxPlayer p = player();
        assertStart(p.next(ABC), 0, "a");
        JukeboxPlayer q = player();
        assertStart(q.previous(ABC), 0, "a");
    }
}
