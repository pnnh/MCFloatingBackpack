package pnnh.mcfloatingbackpack.inventory.combined;

/**
 * 066: a list of cells that can be read and written by number, and asked whether a stack may go into one. The cursor gestures (drag over cells,
 * gather) work on this instead of on one backpack, so the same code serves a backpack's main area and the "all" tab's flat grid of several
 * backpacks. Pure: the stack type is a parameter.
 */
public interface SlotAccess<T> {
    int size();

    T get(int index);

    void set(int index, T stack);

    boolean canPlace(int index, T stack);
}
