package pnnh.mcfloatingbackpack.inventory.creative;

import java.util.function.BiPredicate;
import java.util.function.UnaryOperator;

/**
 * 027: what one side knows of the other side's cursor, so that a value is sent only when it differs from what the other side
 * already has and is never echoed back. Each side keeps one: the client's holds what the server has been told the client's
 * cursor is; the server's holds what the client has been told. Pure logic, generic so the tests need no Minecraft types.
 */
public final class CursorMirror<T> {
    private final BiPredicate<T, T> same;
    private final UnaryOperator<T> copy;
    private final T empty;
    private T known;

    public CursorMirror(BiPredicate<T, T> same, UnaryOperator<T> copy, T empty) {
        this.same = same;
        this.copy = copy;
        this.empty = empty;
        this.known = empty;
    }

    /** The cursor the other side is known to have. */
    public T known() {
        return known;
    }

    /** Whether {@code current} differs from what the other side has, so it has to be sent. */
    public boolean shouldSend(T current) {
        return !same.test(known, current);
    }

    /** Call after sending {@code current}. */
    public void sent(T current) {
        known = copy.apply(current);
    }

    /** Call after receiving the other side's cursor: it has that value, so it need not be told. */
    public void received(T value) {
        known = copy.apply(value);
    }

    /** The screen closed, the player left, the mode changed: start again from an empty cursor. */
    public void reset() {
        known = empty;
    }
}
