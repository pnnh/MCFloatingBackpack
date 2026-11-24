package pnnh.mcfloatingbackpack.inventory.nesting;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * 064: what a player last took out of a floating backpack's panel that would not be let in again (a backpack, or an item with one
 * inside, left in a backpack by an older version). Putting that same item back into that same backpack does not add any nesting,
 * so it is let through; anything else is not. One record per player, never saved; only the server's main thread uses it.
 *
 * @param <P> the player key
 * @param <K> the backpack (a reference to the stack that holds it)
 * @param <S> the item stack
 */
public final class NestedOrigins<P, K, S> {
    private record Origin<K, S>(K backpack, S stack) {
    }

    private final Map<P, Origin<K, S>> byPlayer = new HashMap<>();
    private final BiPredicate<S, S> same;

    public NestedOrigins(BiPredicate<S, S> same) {
        this.same = same;
    }

    /** The player has just taken {@code stack} out of {@code backpack}; an earlier record is replaced. */
    public void remember(P player, K backpack, S stack) {
        byPlayer.put(player, new Origin<>(backpack, stack));
    }

    /** Whether {@code stack} is what the player took out of exactly this {@code backpack}. Does not use the record up. */
    public boolean matches(P player, K backpack, S stack) {
        Origin<K, S> origin = byPlayer.get(player);
        return origin != null && origin.backpack().equals(backpack) && same.test(origin.stack(), stack);
    }

    public void forget(P player) {
        byPlayer.remove(player);
    }

    public void clear() {
        byPlayer.clear();
    }

    public int size() {
        return byPlayer.size();
    }
}
