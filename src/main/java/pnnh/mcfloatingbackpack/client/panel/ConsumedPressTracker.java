package pnnh.mcfloatingbackpack.client.panel;

import java.util.HashSet;
import java.util.Set;

/**
 * Remembers which mouse buttons had their <em>press</em> swallowed by the panel, so that only the
 * matching <em>release</em> is swallowed too.
 *
 * <p>Swallowing a release whose press the game saw leaves vanilla's key state stuck on "down"
 * ({@code KeyMapping.set(key, false)} never runs). For the right button that is visible: the game
 * then repeats "use item" every 4 ticks, and since using a backpack toggles the panel, the panel
 * flickers open/closed and the hand keeps swinging until something calls {@code
 * KeyMapping.releaseAll()} (e.g. opening the inventory).
 */
final class ConsumedPressTracker {
    private final Set<Integer> consumed = new HashSet<>();

    void press(int button) {
        consumed.add(button);
    }

    /** @return true if this button's press was swallowed by the panel, i.e. its release must be too */
    boolean release(int button) {
        return consumed.remove(button);
    }

    /** @return true if this button's press was swallowed and its release has not come yet; changes nothing */
    boolean has(int button) {
        return consumed.contains(button);
    }

    void clear() {
        consumed.clear();
    }
}
