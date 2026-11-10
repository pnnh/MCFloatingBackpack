package pnnh.mcfloatingbackpack.inventory.revision;

/**
 * 040: what to do with a backpack that may have been saved by a newer version. Only an explicit open (the player opening the backpack,
 * not any other path that reads it) can confirm, and only within the window the first refusal opened: otherwise an auto pickup in the
 * next minute would confirm the protection away without anyone asking. Pure logic with no Minecraft types.
 */
public final class RevisionGuard {
    /** What to do. */
    public enum Decision {
        /** The backpack is not protected: go on. */
        ALLOW,
        /** Protected, an explicit open, no window yet: refuse, open the window, tell the player. */
        REFUSE_FIRST,
        /** Protected, an explicit open inside the window: let it through and stamp the backpack as this version's. */
        CONFIRMED,
        /** Protected, any other path: refuse quietly (it does not confirm). */
        REFUSE
    }

    private RevisionGuard() {
    }

    public static Decision decide(int stored, int current, boolean explicitOpen, boolean windowOpen) {
        if (stored <= current) {
            return Decision.ALLOW;
        }
        if (!explicitOpen) {
            return Decision.REFUSE;
        }
        return windowOpen ? Decision.CONFIRMED : Decision.REFUSE_FIRST;
    }
}
