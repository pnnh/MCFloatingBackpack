package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.List;

/**
 * 066: the flat grid of the "all" tab as one {@link SlotAccess}: cell {@code n} is where {@link AllAddressing} says, in the member's own
 * {@link SlotAccess}. Writing marks the member as touched (the caller saves only the touched ones). Pure.
 */
public final class AllSlotAccess<T> implements SlotAccess<T> {
    private final AllAddressing addressing;
    private final List<SlotAccess<T>> members;
    private final boolean[] touched;

    public AllSlotAccess(AllAddressing addressing, List<SlotAccess<T>> members) {
        if (addressing.members() != members.size()) {
            throw new IllegalArgumentException("members " + members.size() + " != addressing " + addressing.members());
        }
        this.addressing = addressing;
        this.members = members;
        this.touched = new boolean[members.size()];
    }

    @Override
    public int size() {
        return addressing.total();
    }

    @Override
    public T get(int index) {
        AllAddressing.Cell cell = addressing.locate(index);
        return members.get(cell.member()).get(cell.local());
    }

    @Override
    public void set(int index, T stack) {
        AllAddressing.Cell cell = addressing.locate(index);
        members.get(cell.member()).set(cell.local(), stack);
        touched[cell.member()] = true;
    }

    @Override
    public boolean canPlace(int index, T stack) {
        AllAddressing.Cell cell = addressing.locate(index);
        return members.get(cell.member()).canPlace(cell.local(), stack);
    }

    /** Whether member {@code member} was written to. */
    public boolean touched(int member) {
        return touched[member];
    }
}
