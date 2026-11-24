package pnnh.mcfloatingbackpack.inventory.combined;

/**
 * 066: the "all" tab's flat cell numbers. The members' main areas are laid one after the other: member 0's cells are {@code 0..size0-1}, member
 * 1's the next {@code size1}, and so on (prefix sums). The number does not depend on how many columns the panel shows: where a member's block
 * starts on the screen is the client's business ({@code AllGrid}). The server reads a request's cell number only through this, together with the
 * epoch that says which member table the number was made from. Pure.
 */
public final class AllAddressing {
    public static final int MAX_MEMBERS = CombinedMembers.MAX_MEMBERS;

    /** Where a flat cell is: the member (in table order) and the cell inside that member's main area. */
    public record Cell(int member, int local) {
    }

    private final int[] starts;

    private AllAddressing(int[] sizes) {
        this.starts = new int[sizes.length + 1];
        for (int i = 0; i < sizes.length; i++) {
            starts[i + 1] = starts[i] + Math.max(0, sizes[i]);
        }
    }

    /** @param sizes the number of main-area cells of each member, in table order */
    public static AllAddressing of(int[] sizes) {
        return new AllAddressing(sizes);
    }

    public int members() {
        return starts.length - 1;
    }

    public int total() {
        return starts[starts.length - 1];
    }

    public int size(int member) {
        return starts[member + 1] - starts[member];
    }

    public int start(int member) {
        return starts[member];
    }

    /** The member and cell a flat number names, or null if it is outside the grid. */
    public Cell locate(int flat) {
        if (flat < 0 || flat >= total()) {
            return null;
        }
        int low = 0;
        int high = members() - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (starts[mid] <= flat) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        // skip members with no cells (a member with size 0 shares its start with the next)
        int member = low;
        while (member < members() && size(member) == 0) {
            member++;
        }
        return new Cell(member, flat - starts[member]);
    }

    /** The flat number of a member's cell, or -1 if there is no such cell. */
    public int flatOf(int member, int local) {
        if (member < 0 || member >= members() || local < 0 || local >= size(member)) {
            return -1;
        }
        return starts[member] + local;
    }
}
