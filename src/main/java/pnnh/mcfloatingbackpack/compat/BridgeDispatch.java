package pnnh.mcfloatingbackpack.compat;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

/**
 * 058: asks the registered network storage bridges, one after another, whether one of them puts a stack into the open menu (the 012
 * delivery to an open container asks this first; when nothing answers the generic path of 012 runs). Written over abstract players,
 * menus and stacks like {@code OpenContainerDelivery}, so the tests drive it with fakes.
 *
 * <p>A bridge is only asked while its compatibility is on ({@link CompatRuntimeState#isActive}). Whatever goes wrong inside a bridge (a
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
    }

    /** Told once per mod and run when its bridge failed. */
    public interface FailureListener {
        void onFailure(String modId, Throwable cause);
    }

    private final Supplier<? extends Collection<? extends Bridge<P, M, S>>> bridges;
    private final Supplier<CompatRuntimeState> state;
    private final QuickTransferLogic.StackOps<S> ops;
    private final FailureListener listener;

    public BridgeDispatch(
            Supplier<? extends Collection<? extends Bridge<P, M, S>>> bridges,
            Supplier<CompatRuntimeState> state,
            QuickTransferLogic.StackOps<S> ops,
            FailureListener listener) {
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
            if (!current.isActive(bridge.modId())) {
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
}
