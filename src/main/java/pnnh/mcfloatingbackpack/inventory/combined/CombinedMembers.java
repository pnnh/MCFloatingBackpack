package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 065: which floating backpacks a combined backpack connects. Pure and generic in the reference type {@code R} (the game's is
 * {@code BackpackRef}), so the rules are tested with plain values.
 *
 * <p>The candidates come in a fixed order (what is worn, then the off hand, then inventory slots 0-35); each goes into exactly one place:
 * a backpack saved by a newer version of the mod is never connected (it must not be rewritten), one of a higher tier than the combined
 * backpack is left out, and after {@link #MAX_MEMBERS} members the rest are left out. The three kinds of "not connected" are counted so
 * the screen can say why.
 */
public final class CombinedMembers {
    /** The most backpacks one combined backpack connects. Fixed, not a setting. */
    public static final int MAX_MEMBERS = 16;

    private CombinedMembers() {
    }

    /** Where a backpack is, for the tab's tooltip: the kind, the worn source's number (0 otherwise) and the slot within it. */
    public record Place(Kind kind, int sourceId, int slot) {
        public enum Kind {
            WORN,
            OFF_HAND,
            INVENTORY
        }
    }

    /**
     * One backpack the player carries.
     *
     * @param identity the backpack's identity, or {@code null} if it has none yet
     * @param customName the name given in an anvil, or the empty string
     * @param protectedNewer saved by a newer version of the mod (040)
     */
    public record Candidate<R>(R ref, Place place, UUID identity, int tier, String customName, boolean protectedNewer) {
    }

    /** A connected backpack: one tab. */
    public record Member<R>(R ref, Place place, UUID identity, int tier, String customName) {
    }

    /** The connected backpacks in tab order, and how many were left out for each reason. Two results are equal when nothing the screen shows differs. */
    public record Result<R>(List<Member<R>> members, int skippedTier, int skippedNewer, int skippedOverCap) {
        public Result {
            members = List.copyOf(members);
        }

        public int skippedTotal() {
            return skippedTier + skippedNewer + skippedOverCap;
        }
    }

    public static <R> Result<R> compute(List<Candidate<R>> candidates, int combinedTier) {
        List<Member<R>> members = new ArrayList<>();
        int skippedTier = 0;
        int skippedNewer = 0;
        int skippedOverCap = 0;
        for (Candidate<R> candidate : candidates) {
            if (candidate.protectedNewer()) {
                skippedNewer++;
            } else if (candidate.tier() > combinedTier) {
                skippedTier++;
            } else if (members.size() >= MAX_MEMBERS) {
                skippedOverCap++;
            } else {
                members.add(new Member<>(candidate.ref(), candidate.place(), candidate.identity(), candidate.tier(), candidate.customName()));
            }
        }
        return new Result<>(members, skippedTier, skippedNewer, skippedOverCap);
    }
}
