package pnnh.mcfloatingbackpack.compat;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

/**
 * 058: asks the registered network storage bridges, one after another, whether one of them puts a stack into the open menu (the 012
 * delivery to an open container asks this first; when nothing answers the generic path of 012 runs). Written over abstract players,
 * menus and stacks like {@code OpenContainerDelivery}, so the tests drive it with fakes.
 *
 * <p>A bridge is only asked while its compatibility is on ({@link CompatRuntimeState#isActive}: the version check, the server config's
 * allowUntestedVersions, no failure in this run) <em>and</em> the server's level for its mod lets mod-specific code run
 * ({@code levelAllows}, 059: FULL only). A bridge skipped because of the level has not failed: nothing is reported and the state is not
 * touched, so it is asked again as soon as the level allows. Whatever goes wrong inside a bridge (a
 * {@link LinkageError} such as a missing method after the other mod changed, any {@link RuntimeException}, a remainder that is not part
 * of what was given) switches that compatibility off for the rest of the run, reports it once, and leaves the stack where it was: the
 * caller then takes the generic path. The bridge gets a copy of the stack, never the original.
 */
public final class BridgeDispatch<P, M, S> {
    /** A third-party mod's way of putting stacks into its own item network. Third-party types appear only in implementations. */
    public interface Bridge<P, M, S> {
        String modId();

        /** Whether this bridge takes this menu (and its current page). Changes nothing. */
        boolean handles(M menu);

        /**
         * Puts the stack (a copy) into the network.
         *
         * @return what did not fit, empty when all of it went in; never anything but a part of {@code stack}
         */
        S insert(P player, M menu, S stack);

        /** 060: whether this bridge takes stacks out of this menu's network (the page is one it supports). Changes nothing. */
        default boolean handlesTake(M menu) {
            return false;
        }

        /**
         * 060: takes up to {@code what}'s count of its item (a full stack unless the client named less) out of the network; {@code handle} is
         * whatever the mod's own client code said about the entry (an opaque number).
         *
         * @return what was taken, empty if nothing; never anything but the same item and never more than the count asked for
         */
        default S take(P player, M menu, S what, long handle) {
            throw new UnsupportedOperationException("this bridge does not take");
        }

        /** 060: puts what the backpack could not hold back into the network; returns what the network did not take back. */
        default S giveBack(P player, M menu, S stack, long handle) {
            throw new UnsupportedOperationException("this bridge does not take");
        }
    }

    /** Told once per mod and run when its bridge failed. */
    public interface FailureListener {
        void onFailure(String modId, Throwable cause);
    }

    private final Supplier<? extends Collection<? extends Bridge<P, M, S>>> bridges;
    private final Supplier<CompatRuntimeState> state;
    private final QuickTransferLogic.StackOps<S> ops;
    private final FailureListener listener;
    private final Predicate<String> levelAllows;

    public BridgeDispatch(
            Supplier<? extends Collection<? extends Bridge<P, M, S>>> bridges,
            Supplier<CompatRuntimeState> state,
            QuickTransferLogic.StackOps<S> ops,
            FailureListener listener,
            Predicate<String> levelAllows) {
        this.levelAllows = levelAllows;
        this.bridges = bridges;
        this.state = state;
        this.ops = ops;
        this.listener = listener;
    }

    /**
     * @return empty when no bridge took it (the caller goes on as before); otherwise what is left of the stack, which the caller keeps
     */
    public Optional<S> tryInsert(P player, M menu, S stack) {
        CompatRuntimeState current = state.get();
        if (current == null || ops.isEmpty(stack)) {
            return Optional.empty();
        }
        for (Bridge<P, M, S> bridge : bridges.get()) {
            if (!current.isActive(bridge.modId()) || !levelAllows.test(bridge.modId())) {
                continue;
            }
            try {
                if (!bridge.handles(menu)) {
                    continue;
                }
                S rest = bridge.insert(player, menu, ops.withCount(stack, ops.count(stack)));
                if (!validRemainder(stack, rest)) {
                    throw new IllegalStateException("the bridge of " + bridge.modId() + " returned a remainder that is not part of the stack it was given");
                }
                return Optional.of(rest);
            } catch (LinkageError | RuntimeException failure) {
                if (current.fail(bridge.modId())) {
                    listener.onFailure(bridge.modId(), failure);
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private boolean validRemainder(S given, S rest) {
        if (rest == null) {
            return false;
        }
        if (ops.isEmpty(rest)) {
            return true;
        }
        return ops.sameKind(rest, given) && ops.count(rest) <= ops.count(given);
    }

    /**
     * 060: asks the bridges, in order, to take a stack out of the open menu's network (the first that handles it answers).
     *
     * @return empty when no bridge handled it (nothing happened); otherwise what was taken (possibly empty: the bridge tried and found
     *     nothing)
     */
    public Optional<S> tryTake(P player, M menu, S what, long handle) {
        CompatRuntimeState current = state.get();
        if (current == null || ops.isEmpty(what)) {
            return Optional.empty();
        }
        for (Bridge<P, M, S> bridge : bridges.get()) {
            if (!current.isActive(bridge.modId()) || !levelAllows.test(bridge.modId())) {
                continue;
            }
            try {
                if (!bridge.handlesTake(menu)) {
                    continue;
                }
                // {what}'s count is the amount asked for (the caller clamped it to 1..a full stack)
                int asked = Math.max(1, Math.min(ops.count(what), NetworkTakeRules.amountFor(ops, what)));
                S taken = bridge.take(player, menu, ops.withCount(what, asked), handle);
                if (!NetworkTakeRules.legalTake(ops, what, taken, asked)) {
                    throw new IllegalStateException("the bridge of " + bridge.modId() + " returned something that is not a part of the stack asked for");
                }
                return Optional.of(taken);
            } catch (LinkageError | RuntimeException failure) {
                if (current.fail(bridge.modId())) {
                    listener.onFailure(bridge.modId(), failure);
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * 060: gives back to the network what the backpack could not hold.
     *
     * @return empty when no bridge handled it (all of it is still the caller's); otherwise what the network did not take back
     */
    public Optional<S> tryGiveBack(P player, M menu, S stack, long handle) {
        CompatRuntimeState current = state.get();
        if (current == null || ops.isEmpty(stack)) {
            return Optional.empty();
        }
        for (Bridge<P, M, S> bridge : bridges.get()) {
            if (!current.isActive(bridge.modId()) || !levelAllows.test(bridge.modId())) {
                continue;
            }
            try {
                if (!bridge.handlesTake(menu)) {
                    continue;
                }
                S rest = bridge.giveBack(player, menu, ops.withCount(stack, ops.count(stack)), handle);
                if (!NetworkTakeRules.legalTake(ops, stack, rest, ops.count(stack))) {
                    throw new IllegalStateException("the bridge of " + bridge.modId() + " returned something that is not a part of what it was given back");
                }
                return Optional.of(rest);
            } catch (LinkageError | RuntimeException failure) {
                if (current.fail(bridge.modId())) {
                    listener.onFailure(bridge.modId(), failure);
                }
                return Optional.empty();
            }
        }
        return Optional.empty();
    }
}
