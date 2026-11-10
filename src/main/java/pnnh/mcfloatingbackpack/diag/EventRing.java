package pnnh.mcfloatingbackpack.diag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The last few diagnostic events of one player, kept in memory whatever the log level is, so that when something goes wrong
 * (a backpack is gone) the log can say what happened just before. Oldest dropped first. Pure logic.
 */
public final class EventRing {
    private final int capacity;
    private final Deque<String> events = new ArrayDeque<>();

    public EventRing(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized void add(String event) {
        if (events.size() == capacity) {
            events.removeFirst();
        }
        events.addLast(event);
    }

    /** The events, oldest first. */
    public synchronized List<String> snapshot() {
        return new ArrayList<>(events);
    }

    public synchronized int size() {
        return events.size();
    }
}
