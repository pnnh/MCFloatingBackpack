package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.List;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.extension.RowView;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;

/**
 * 066: what the server knows about a player's "all" tab: the members of this epoch (the table the flat cell numbers were made from), the
 * in-memory cursor, and for each member what the client was last sent. In memory only. A new epoch is a new object: the members, and so the
 * numbering, never change inside one.
 */
public final class AllState {
    /** One backpack of the epoch. {@code identity} is never null: it was given one when the epoch began. */
    public static final class Member {
        public final BackpackRef ref;
        public final UUID identity;
        public final int tier;
        /** The number of cells of its main area (the tier capacity and the visible rows' cells). */
        public final int mainSize;
        /** What the client was last sent: the contents object of the stack (for the cheap "nothing was saved" test), and what it shows. */
        public Object lastContents;
        public List<ItemStack> lastMain = List.of();
        public RowView lastRows;
        public ItemTags lastTags;

        public Member(BackpackRef ref, UUID identity, int tier, int mainSize) {
            this.ref = ref;
            this.identity = identity;
            this.tier = tier;
            this.mainSize = mainSize;
        }
    }

    private final int epoch;
    private final List<Member> members;
    private final AllAddressing addressing;
    private ItemStack cursor = ItemStack.EMPTY;

    public AllState(int epoch, List<Member> members) {
        this.epoch = epoch;
        this.members = List.copyOf(members);
        int[] sizes = new int[members.size()];
        for (int i = 0; i < sizes.length; i++) {
            sizes[i] = members.get(i).mainSize;
        }
        this.addressing = AllAddressing.of(sizes);
    }

    public int epoch() {
        return epoch;
    }

    public List<Member> members() {
        return members;
    }

    public AllAddressing addressing() {
        return addressing;
    }

    /** The cursor held on the "all" tab; never saved anywhere. */
    public ItemStack cursor() {
        return cursor;
    }

    public void setCursor(ItemStack cursor) {
        this.cursor = cursor == null || cursor.isEmpty() ? ItemStack.EMPTY : cursor;
    }

    public int indexOf(BackpackRef ref) {
        for (int i = 0; i < members.size(); i++) {
            if (members.get(i).ref.equals(ref)) {
                return i;
            }
        }
        return -1;
    }
}
