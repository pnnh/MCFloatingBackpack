package pnnh.mcfloatingbackpack.client.panel.trash;

import java.util.HashSet;
import java.util.Set;

/**
 * 021: remembers which mouse buttons went down <em>outside</em> the trash can panel, so that when the left button
 * comes up on the panel the release can tell a drop (pressed elsewhere, dragged here) from a plain click (pressed here).
 * Pure bookkeeping with no Minecraft types, so it can be tested.
 */
public final class TrashDropTracker {
    private static final int LEFT = 0;

    private final Set<Integer> pressedOutside = new HashSet<>();

    /** A button went down; {@code onTrash} says whether it went down on the trash can panel. */
    public void press(int button, boolean onTrash) {
        if (onTrash) {
            pressedOutside.remove(button);
        } else {
            pressedOutside.add(button);
        }
    }

    /**
     * A button came up. The record of its press is always forgotten.
     *
     * @return true if this is a drop: the left button, pressed outside the panel, released on it
     */
    public boolean release(int button, boolean onTrash) {
        boolean wasOutside = pressedOutside.remove(button);
        return wasOutside && onTrash && button == LEFT;
    }

    public void clear() {
        pressedOutside.clear();
    }
}
