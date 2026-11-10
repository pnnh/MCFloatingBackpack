package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Which backpack (by identity) each player's panel was opened on. The panel finds its backpack again for every request by where
 * it is (the main hand, the chest, an inventory slot), so when the stack at that place is replaced by another backpack (the
 * creative inventory does that, so does changing the selected hotbar slot) the player would go on clicking on the picture of the
 * first backpack while the server changes the second one. Comparing identities tells the two apart. Generic in the player and
 * the identity so it can be tested with plain values. Pure logic.
 */
public final class OpenSessions<P, B> {
    public enum Verdict {
        /** No panel was opened on a backpack with an identity: nothing to compare. */
        NO_SESSION,
        /** The backpack there now is the one the panel was opened on. */
        SAME,
        /** It is another backpack (or none that can be told). */
        STALE
    }

    private final Map<P, B> opened = new HashMap<>();

    /** The panel was opened on this backpack; {@code null} (no identity yet) means there is nothing to compare. */
    public void opened(P player, B backpack) {
        if (backpack == null) {
            opened.remove(player);
        } else {
            opened.put(player, backpack);
        }
    }

    public void closed(P player) {
        opened.remove(player);
    }

    public Verdict check(P player, B current) {
        B open = opened.get(player);
        if (open == null) {
            return Verdict.NO_SESSION;
        }
        return Objects.equals(open, current) ? Verdict.SAME : Verdict.STALE;
    }

    public boolean has(P player) {
        return opened.containsKey(player);
    }
}
