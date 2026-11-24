package pnnh.mcfloatingbackpack.inventory.combined;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.network.AllSync;
import pnnh.mcfloatingbackpack.network.BackpackNetwork;

/**
 * 066: one request for the "all" tab, from the check at its start to the saving at its end. {@link #begin} checks, before anything is touched,
 * that the player's session is on the "all" tab, that the request's epoch is the tab's, and that every member is still the backpack the epoch was
 * made with; if not it refuses (and the tab begins again with a new epoch). Members are decoded only when a request needs them and each only once;
 * {@link #finish} saves the ones that were changed and tells the client.
 *
 * <p>Server main thread only. The in-memory cursor lives in {@link AllState}; a request that needs it borrows it for one member with
 * {@link CursorLoan}, or for several with {@link #cursorHolder}.
 */
public final class AllContext {
    private final ServerPlayer player;
    private final AllState all;
    private final BackpackInventory[] inventories;
    private final boolean[] failed;
    private final boolean[] touched;

    private AllContext(ServerPlayer player, AllState all) {
        this.player = player;
        this.all = all;
        this.inventories = new BackpackInventory[all.members().size()];
        this.failed = new boolean[inventories.length];
        this.touched = new boolean[inventories.length];
    }

    /**
     * @return the context, or null if the request is refused (nothing was touched; the client has been, or will be, put right)
     */
    public static AllContext begin(ServerPlayer player, int requestEpoch) {
        CombinedSessions.Session session = CombinedSessions.get(player.getUUID());
        if (session == null) {
            // the client believes there is an "all" tab: there is no combined panel at all
            DiagLog.record(player.getUUID(), "all request ignored: no combined session; the panel is told to close");
            BackpackNetwork.sendSession(player, false, null);
            return null;
        }
        if (!session.inAll()) {
            // a request made for the "all" tab arrived after the session left it: it must never be read as one backpack's cell number
            DiagLog.record(player.getUUID(), "all request ignored: the session is on a single tab (epoch " + requestEpoch + ")");
            return null;
        }
        AllState all = session.all();
        int tier = WornCombined.tier(player);
        String why = null;
        if (!CombinedConfig.enabled() || tier < 0) {
            why = "the combined backpack is gone or switched off";
        } else if (!AllRequestRules.epochOk(true, all.epoch(), requestEpoch)) {
            why = "epoch " + requestEpoch + " is not " + all.epoch();
        } else {
            for (AllState.Member member : all.members()) {
                if (!CombinedSessions.memberHolds(player, member, member.ref.getStack(player), tier)) {
                    why = "member " + member.ref + " is not what the epoch was made with";
                    break;
                }
            }
        }
        if (why != null) {
            DiagLog.record(player.getUUID(), "all request refused: " + why + "; the all tab begins again");
            CombinedSessions.resyncAll(player);
            return null;
        }
        return new AllContext(player, all);
    }

    public AllState all() {
        return all;
    }

    public AllAddressing addressing() {
        return all.addressing();
    }

    public BackpackRef ref(int member) {
        return all.members().get(member).ref;
    }

    /** Member {@code member}'s inventory (decoded once), or null if it cannot be used (the caller then gives the request up with {@link #abandon}). */
    public BackpackInventory inventory(int member) {
        if (failed[member]) {
            return null;
        }
        if (inventories[member] == null) {
            AllState.Member state = all.members().get(member);
            Optional<BackpackInventory> inventory = BackpackGate.acquire(player, state.ref);
            if (inventory.isEmpty() || inventory.get().size() != state.mainSize) {
                failed[member] = true;
                return null;
            }
            inventories[member] = inventory.get();
        }
        return inventories[member];
    }

    public void touch(int member) {
        touched[member] = true;
    }

    /** The request cannot be carried out after all (a member is not what it was): nothing is saved, and the tab begins again. */
    public void abandon() {
        DiagLog.record(player.getUUID(), "all request given up: a member could not be used; the all tab begins again");
        CombinedSessions.resyncAll(player);
    }

    /** The whole flat grid as one list of cells: reading and writing go to the members, writing marks a member as changed. */
    public SlotAccess<ItemStack> slots() {
        List<SlotAccess<ItemStack>> members = new ArrayList<>();
        for (int i = 0; i < all.members().size(); i++) {
            members.add(new MemberSlots(i));
        }
        return new AllSlotAccess<>(all.addressing(), members);
    }

    private final class MemberSlots implements SlotAccess<ItemStack> {
        private final int member;

        MemberSlots(int member) {
            this.member = member;
        }

        @Override
        public int size() {
            return all.members().get(member).mainSize;
        }

        @Override
        public ItemStack get(int index) {
            BackpackInventory inventory = inventory(member);
            return inventory == null ? ItemStack.EMPTY : inventory.getStackInSlot(index);
        }

        @Override
        public void set(int index, ItemStack stack) {
            BackpackInventory inventory = inventory(member);
            if (inventory != null) {
                inventory.setStackInSlot(index, stack);
                touch(member);
            }
        }

        @Override
        public boolean canPlace(int index, ItemStack stack) {
            BackpackInventory inventory = inventory(member);
            return inventory != null && inventory.canPlaceMain(index, stack);
        }
    }

    /**
     * Puts a stack into the members' main areas, one member after the other (each by its own rule: the category rows that take it, then the
     * tier capacity's cells, then the universal rows). Returns what no member took.
     */
    public ItemStack insertIntoMain(ItemStack stack) {
        ItemStack left = stack;
        for (int member = 0; member < inventories.length && !left.isEmpty(); member++) {
            BackpackInventory inventory = inventory(member);
            if (inventory == null) {
                continue;
            }
            int before = left.getCount();
            left = inventory.insertIntoMain(left);
            if (left.getCount() != before) {
                touch(member);
            }
        }
        return left;
    }

    /**
     * Where a stack goes when it is moved into the "all" grid, as segments of flat cell numbers to try one after the other: member by member, each
     * member's own segments (the category rows that take it, then the tier capacity's cells, then the universal rows) in its order.
     */
    public List<int[]> mainTargetOrder(ItemStack stack) {
        List<int[]> order = new ArrayList<>();
        for (int member = 0; member < inventories.length; member++) {
            BackpackInventory inventory = inventory(member);
            if (inventory == null) {
                continue;
            }
            int start = all.addressing().start(member);
            for (int[] segment : inventory.mainTargetOrder(stack)) {
                int[] flat = new int[segment.length];
                for (int i = 0; i < segment.length; i++) {
                    flat[i] = start + segment[i];
                }
                order.add(flat);
            }
        }
        return order;
    }

    /**
     * A throw-away inventory that only carries the "all" tab's cursor, for the gestures that work on several members at once (a
     * {@code PanelCursor} wants an inventory to keep the cursor in). Read the cursor back with {@link #takeCursorBack}.
     */
    public BackpackInventory cursorHolder() {
        BackpackInventory holder = new BackpackInventory(1);
        holder.setCursor(all.cursor());
        return holder;
    }

    public void takeCursorBack(BackpackInventory holder) {
        all.setCursor(holder.getCursor());
        holder.setCursor(ItemStack.EMPTY);
    }

    /** Saves every member that was changed (each by itself, so each is told to the snapshot service) and sends them, and the cursor, to the client. */
    public void finish() {
        boolean any = false;
        for (int member = 0; member < inventories.length; member++) {
            if (!touched[member] || inventories[member] == null) {
                continue;
            }
            // the cursor is never written into a backpack
            inventories[member].setCursor(ItemStack.EMPTY);
            all.members().get(member).ref.saveInventory(player, inventories[member]);
            any = true;
        }
        for (int member = 0; member < inventories.length; member++) {
            if (touched[member] && inventories[member] != null) {
                if (!AllSync.sendMember(player, all, member, inventories[member])) {
                    CombinedSessions.endAndTell(player);
                    return;
                }
            }
        }
        if (!any) {
            AllSync.sendCell(player, all, -1, ItemStack.EMPTY);
        }
        player.getInventory().setChanged();
    }
}
