package pnnh.mcfloatingbackpack.inventory.worn;

/**
 * 032: one place a player can wear a floating backpack: the chest slot, and (033, 034) the back slot of an accessories mod. The
 * sources are tried in {@link #order()}; the first backpack found is the one that counts as worn (it is drawn on the back, and the
 * panel key opens it unless a hand holds one). Pure logic with no Minecraft types: {@code P} is the player, {@code S} the item stack.
 *
 * <p>Implementations must never throw for a slot that is out of range, a mod that has gone, or a player without that kind of
 * inventory: they answer "nothing there". They must work for any player on the client too (the back layer draws other players).
 */
public interface WornSource<P, S> {
    /** This source's number on the wire (the {@code BackpackRef.Source} ordinal). Once published it never changes. */
    int id();

    /** Where this source stands in the search; the smaller comes first. Not tied to {@link #id()}, so a source can be put in the middle. */
    int order();

    /** Asked at every search, not when the source is registered, so a setting that is changed takes effect at once. Cheap. */
    boolean active();

    /** How many slots of this source may hold a backpack. */
    int slotCount(P player);

    /** The stack in a slot, or the empty stack for a slot that is out of range. Only the real stack, never a cosmetic one. */
    S get(P player, int slot);

    void set(P player, int slot, S stack);

    /** Whether the player has switched off drawing what is in this slot. The chest slot has no such switch. */
    default boolean hiddenRender(P player, int slot) {
        return false;
    }
}
