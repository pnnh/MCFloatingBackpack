package pnnh.mcfloatingbackpack.jukebox;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * 053: what the mobile jukebox's player is doing, with no sound in it: which track is current, whether it is playing, the two switches
 * (repeat one, shuffle) and, for shuffle, the round (every track once, then a new round) and the tracks played, for "previous". Every
 * operation is given the playlist as it is <em>now</em> (a list of keys, one for each disc, in order) and says what to do: start a track,
 * stop, or nothing. The playlist can change between two calls (a disc is taken out, a row hidden): the current track is found again by its
 * key, then by its place, and when the list is empty everything stops. There is no pause: stopping and playing again begins the track at its
 * start, and so does rewinding. Pure logic; the random numbers come from the {@link Random} it is built with, so tests can fix them.
 */
public final class JukeboxPlayer {
    public enum Kind { START, STOP, NONE }

    /** What to do: {@code START} the track at {@code index} (its key is {@code key}), {@code STOP}, or {@code NONE}. */
    public record Command(Kind kind, int index, String key) {
        static final Command STOP = new Command(Kind.STOP, -1, null);
        static final Command NONE = new Command(Kind.NONE, -1, null);

        static Command start(int index, String key) {
            return new Command(Kind.START, index, key);
        }
    }

    private static final int HISTORY_LIMIT = 500;

    private final Random random;
    private boolean playing;
    private boolean loopOne;
    private boolean shuffle;
    /** The current track: its key and where it was last seen in the list. {@code key == null}: none yet. */
    private String key;
    private int index = -1;
    /** The current track's disc was taken out and {@link #index} now names the track that moved into its place (so "next" is that one). */
    private boolean lost;
    /** Shuffle: the places of this round's tracks still to play (the current one is not in it). */
    private final Deque<Integer> queue = new ArrayDeque<>();
    /** The list the queue was made for: when the list is not the same, the queue is made again. */
    private List<String> queueFor = List.of();
    /** Shuffle: the places played before the current one, most recent last. */
    private final Deque<Integer> history = new ArrayDeque<>();

    public JukeboxPlayer(Random random) {
        this.random = random;
    }

    public boolean playing() {
        return playing;
    }

    public boolean loopOne() {
        return loopOne;
    }

    public boolean shuffle() {
        return shuffle;
    }

    public String currentKey() {
        return key;
    }

    /** The current track's place in {@code list} (0-based), or -1 if there is none or the list is empty. */
    public int currentIndex(List<String> list) {
        return resolve(list);
    }

    // ---- operations ----

    /** Play, from the start of the current track (the first one, or a random one when shuffling, if there is none). */
    public Command play(List<String> list) {
        if (list.isEmpty()) {
            return stopAll();
        }
        int at = resolve(list);
        if (at < 0) {
            at = shuffle ? random.nextInt(list.size()) : 0;
        }
        return startAt(list, at);
    }

    /** Stop. The current track is kept, and playing again starts it from its start. */
    public Command stop() {
        playing = false;
        return Command.STOP;
    }

    /** Rewind: the current track again from its start (the same as {@link #play} also while it is playing). */
    public Command rewind(List<String> list) {
        return play(list);
    }

    public Command next(List<String> list) {
        if (list.isEmpty()) {
            return stopAll();
        }
        int at = resolve(list);
        if (at < 0) {
            return play(list);
        }
        if (shuffle) {
            pushHistory(at);
            return startAt(list, takeFromQueue(list, at));
        }
        if (lost) {
            return startAt(list, at);
        }
        return startAt(list, (at + 1) % list.size());
    }

    public Command previous(List<String> list) {
        if (list.isEmpty()) {
            return stopAll();
        }
        int at = resolve(list);
        if (at < 0) {
            return play(list);
        }
        if (shuffle) {
            Integer back = null;
            while (!history.isEmpty() && back == null) {
                int candidate = history.pollLast();
                if (candidate >= 0 && candidate < list.size()) {
                    back = candidate;
                }
            }
            if (back == null) {
                return startAt(list, at);
            }
            ensureQueue(list, at);
            queue.remove(back);
            queue.addFirst(at); // going on from here comes back to the track left
            return startAt(list, back);
        }
        return startAt(list, (at - 1 + list.size()) % list.size());
    }

    /** The current track has played to its end. */
    public Command finished(List<String> list) {
        if (list.isEmpty()) {
            return stopAll();
        }
        if (loopOne) {
            int at = resolve(list);
            return at < 0 ? next(list) : startAt(list, at);
        }
        return next(list);
    }

    public void setLoopOne(boolean on) {
        loopOne = on;
    }

    public void setShuffle(boolean on) {
        shuffle = on;
        queue.clear();
        queueFor = List.of();
        history.clear();
    }

    /** Back to the start: nothing current, stopped, both switches off. */
    public void reset() {
        playing = false;
        loopOne = false;
        shuffle = false;
        key = null;
        index = -1;
        lost = false;
        queue.clear();
        queueFor = List.of();
        history.clear();
    }

    // ---- inside ----

    private Command stopAll() {
        playing = false;
        key = null;
        index = -1;
        lost = false;
        queue.clear();
        queueFor = List.of();
        history.clear();
        return Command.STOP;
    }

    private Command startAt(List<String> list, int at) {
        index = at;
        key = list.get(at);
        lost = false;
        playing = true;
        if (shuffle) {
            ensureQueue(list, at);
            queue.remove(at);
        }
        return Command.start(at, key);
    }

    /** Where the current track is in {@code list}: where it was if the key still matches, else its first place by key, else clamped. */
    private int resolve(List<String> list) {
        if (key == null || list.isEmpty()) {
            return -1;
        }
        if (index >= 0 && index < list.size() && list.get(index).equals(key)) {
            return index;
        }
        int found = list.indexOf(key);
        if (found >= 0) {
            index = found;
            return found;
        }
        // the disc was taken out: the current track becomes whatever lies at its place now (the one that followed it moved there), and
        // when it was the last one "next" must wrap round, as it would have
        boolean wasLast = index >= list.size();
        index = Math.min(Math.max(index, 0), list.size() - 1);
        key = list.get(index);
        lost = !wasLast;
        return index;
    }

    private void pushHistory(int at) {
        history.addLast(at);
        while (history.size() > HISTORY_LIMIT) {
            history.pollFirst();
        }
    }

    /** Makes the queue for {@code list} (all places but {@code current}, shuffled) unless it was made for this very list. */
    private void ensureQueue(List<String> list, int current) {
        if (!queueFor.equals(list)) {
            queue.clear();
            List<Integer> places = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                if (i != current) {
                    places.add(i);
                }
            }
            Collections.shuffle(places, random);
            queue.addAll(places);
            queueFor = List.copyOf(list);
        }
    }

    /** The next place of the round; when the round is over a new one is shuffled, and it does not begin with the track that just ended. */
    private int takeFromQueue(List<String> list, int current) {
        ensureQueue(list, current);
        if (queue.isEmpty()) {
            List<Integer> places = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                places.add(i);
            }
            Collections.shuffle(places, random);
            if (places.size() > 1 && places.get(0) == current) {
                Collections.swap(places, 0, 1);
            }
            queue.addAll(places);
        }
        return queue.pollFirst();
    }
}
