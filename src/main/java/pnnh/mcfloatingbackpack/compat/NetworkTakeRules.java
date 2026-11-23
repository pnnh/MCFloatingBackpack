package pnnh.mcfloatingbackpack.compat;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

/**
 * 060: the rules of taking one stack out of a network terminal into the backpack, over abstract stacks so the tests drive them with fakes.
 * The server decides how much is asked for (one full stack of the item, or less when the client names a smaller amount), never more than that; whatever was taken ends up in exactly one of
 * three places: the backpack, back in the network, or (when neither takes it) the player's inventory or the ground, so nothing is lost
 * and nothing is made.
 */
public final class NetworkTakeRules {
    private NetworkTakeRules() {
    }

    /** How many of this item one request asks for: a full stack. */
    public static <S> int amountFor(QuickTransferLogic.StackOps<S> ops, S what) {
        return ops.isEmpty(what) ? 0 : Math.max(1, ops.maxStack(what));
    }

    /**
     * How many of this item one request asks for when the client names an amount (the Integrated Terminals "held" item, 060 fix): the amount,
     * but never more than a full stack; no amount (0 or less) means a full stack. The server clamps it, whatever the client says.
     */
    public static <S> int amountFor(QuickTransferLogic.StackOps<S> ops, S what, int requested) {
        int full = amountFor(ops, what);
        return requested <= 0 ? full : Math.min(requested, full);
    }

    /** Whether {@code result} is a legal answer to a request for {@code asked} of {@code what}: nothing, or the same item and not more. */
    public static <S> boolean legalTake(QuickTransferLogic.StackOps<S> ops, S what, S result, int asked) {
        if (result == null) {
            return false;
        }
        return ops.isEmpty(result) || (ops.sameKind(result, what) && ops.count(result) <= asked);
    }

    /** Where a taken stack went: counts of items, which add up to what was taken. */
    public record Split(int toBackpack, int backToNetwork, int fallback) {
        public int total() {
            return toBackpack + backToNetwork + fallback;
        }
    }

    /**
     * @param taken how many items were taken out of the network
     * @param leftAfterBackpack how many the backpack could not take (the backpack takes {@code taken} minus this)
     * @param leftAfterGiveBack how many of those the network did not take back (a failed or refused give back leaves all of them)
     * @return the split; {@code total() == taken} whatever the two answers are
     */
    public static Split distribute(int taken, int leftAfterBackpack, int leftAfterGiveBack) {
        int notInBackpack = Math.max(0, Math.min(taken, leftAfterBackpack));
        int notReturned = Math.max(0, Math.min(notInBackpack, leftAfterGiveBack));
        return new Split(taken - notInBackpack, notInBackpack - notReturned, notReturned);
    }
}
