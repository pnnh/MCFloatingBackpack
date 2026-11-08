package pnnh.mcfloatingbackpack.inventory.pickup;

/**
 * 024: what the auto pickup does with one item the player is about to pick up.
 *
 * <pre>
 * tool not active      -> VANILLA  (not ours: vanilla picks it up)
 * the item is blacklisted -> BLOCK    (stays on the ground, in no inventory)
 * otherwise            -> TAKE     (into the backpack; what does not fit falls back to vanilla)
 * </pre>
 *
 * Other backpack mods are not asked about: this runs after them (low priority) and stands aside whenever the pickup event
 * was already settled, see {@link AutoPickupEvents}. Pure logic.
 */
public final class AutoPickupDecision {
    public enum Outcome {
        VANILLA,
        BLOCK,
        TAKE
    }

    private AutoPickupDecision() {
    }

    public static Outcome decide(boolean toolActive, boolean blacklisted) {
        if (!toolActive) {
            return Outcome.VANILLA;
        }
        return blacklisted ? Outcome.BLOCK : Outcome.TAKE;
    }
}
