package pnnh.mcfloatingbackpack.client.panel.combined;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.search.SearchState;
import pnnh.mcfloatingbackpack.inventory.ItemSortOrder;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.combined.AllAddressing;
import pnnh.mcfloatingbackpack.inventory.extension.RowView;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.network.CombinedCellUpdatePayload;
import pnnh.mcfloatingbackpack.network.CombinedMemberSyncPayload;

/**
 * 066: what the client holds of the "all" tab: each member's main area (as the server sent it for one epoch), the in-memory cursor, the order the
 * sort button gave (a permutation of the flat cells, only about display), and the layout of the grid over the panel's columns. When every member
 * of an epoch has arrived the tab is {@link #ready()} and the cells are put, one member after the other, into {@link ClientBackpackView} (the flat
 * numbers), which is then what the panel draws and what the gestures read. {@link ClientBackpackView} is not otherwise changed: on a single
 * backpack's tab it is that backpack's mirror, as before.
 */
@OnlyIn(Dist.CLIENT)
public final class ClientCombinedView {
    private static final ClientCombinedView INSTANCE = new ClientCombinedView();

    /** One member's part of the picture. */
    public record Slice(List<ItemStack> slots, RowView rows, ItemTags tags) {
    }

    private int epoch;
    private Slice[] slices = new Slice[0];
    private boolean ready;
    private AllAddressing addressing = AllAddressing.of(new int[0]);
    private int[] sizes = new int[0];
    /** The sort button was pressed (and not undone by a new session): the display follows {@link #sortOrder}. */
    private boolean sorted;
    private int[] sortOrder;
    private int cachedCols = -1;
    private int cachedVersion = -1;
    private int[] cachedMap;
    private int version;
    private ItemStack cursor = ItemStack.EMPTY;

    private ClientCombinedView() {
    }

    public static ClientCombinedView get() {
        return INSTANCE;
    }

    public boolean ready() {
        return ready;
    }

    public int epoch() {
        return epoch;
    }

    public AllAddressing addressing() {
        return addressing;
    }

    public int memberCount() {
        return slices.length;
    }

    /** The member a flat cell belongs to, or -1. */
    public int memberOf(int flat) {
        AllAddressing.Cell cell = addressing.locate(flat);
        return cell == null ? -1 : cell.member();
    }

    public Slice slice(int member) {
        return member >= 0 && member < slices.length ? slices[member] : null;
    }

    /** A member's rows and tags, for the grey cells; null if the member is not known. */
    public Slice sliceOfFlat(int flat) {
        return slice(memberOf(flat));
    }

    /** The cell numbers inside a member, for what a cell's own rules ask (its row). */
    public int localOf(int flat) {
        AllAddressing.Cell cell = addressing.locate(flat);
        return cell == null ? -1 : cell.local();
    }

    /** A tab's own colour or none: the colour of the member's place in the tab strip. */
    public static int colorOf(int member) {
        return CombinedColors.argb(member);
    }

    /** The combined panel is over (closed, ended, the world was left): forgets everything, the sort order too. */
    public void clear() {
        sorted = false;
        leave();
    }

    /**
     * The "all" tab is left for a single backpack's tab: its cells, cursor and grid are dropped, and so is the search over the grid; the sort
     * button's mode is kept, so coming back to the tab shows the order again.
     */
    public void leave() {
        epoch = 0;
        slices = new Slice[0];
        ready = false;
        addressing = AllAddressing.of(new int[0]);
        sizes = new int[0];
        sortOrder = null;
        cachedMap = null;
        cachedCols = -1;
        cursor = ItemStack.EMPTY;
        version++;
        FloatingPanelState state = FloatingPanelState.get();
        boolean was = state.isAllMode();
        state.setBaseMapProvider(null);
        state.setAllMode(false);
        if (was) {
            SearchState.get().reset();
        }
    }

    /** The "all" tab was entered (or begun again): the state that goes with it. */
    public void enterAll() {
        FloatingPanelState state = FloatingPanelState.get();
        state.setAllMode(true);
        state.setBaseMapProvider(this::displayMap);
    }

    /** What the sort button did: the display follows the items' order from now until the tab is left or the members change. */
    public void sortNow() {
        if (!ready) {
            return;
        }
        sorted = true;
        recomputeSort();
        bump();
        // a search that is on shows its results in the new order
        SearchState.get().refresh(pnnh.mcfloatingbackpack.client.panel.search.SearchContext.source());
        FloatingPanelState.get().setScrollRow(0);
    }

    /** The display order (display position -> flat cell) after a sort, or null for the default order. */
    public int[] sortOrder() {
        return sorted ? sortOrder : null;
    }

    public void onMemberSync(CombinedMemberSyncPayload payload) {
        ClientCombinedState state = ClientCombinedState.get();
        if (!state.isAllSelected() || payload.epoch() < epoch) {
            return;
        }
        boolean newEpoch = payload.epoch() > epoch || slices.length != payload.memberCount();
        if (newEpoch) {
            epoch = payload.epoch();
            slices = new Slice[payload.memberCount()];
            ready = false;
        }
        if (payload.memberIndex() >= slices.length) {
            return;
        }
        slices[payload.memberIndex()] = new Slice(copy(payload.slots()), payload.rows(), payload.tags());
        cursor = payload.cursor().isEmpty() ? ItemStack.EMPTY : payload.cursor().copy();
        boolean complete = true;
        for (Slice slice : slices) {
            if (slice == null) {
                complete = false;
                break;
            }
        }
        if (!complete) {
            return;
        }
        boolean wasReady = ready;
        ready = true;
        compose(newEpoch || !wasReady);
        state.onAllReady(epoch);
    }

    public void onCellUpdate(CombinedCellUpdatePayload payload) {
        if (!ready || payload.epoch() != epoch || !ClientCombinedState.get().isAllSelected()) {
            return;
        }
        if (payload.flat() >= 0) {
            AllAddressing.Cell cell = addressing.locate(payload.flat());
            if (cell != null && slices[cell.member()] != null && cell.local() < slices[cell.member()].slots().size()) {
                slices[cell.member()].slots().set(cell.local(), payload.stack().isEmpty() ? ItemStack.EMPTY : payload.stack().copy());
            }
            ClientBackpackView.get().applySlotUpdate(PanelArea.MAIN, payload.flat(), payload.stack(), payload.cursor());
        } else {
            ClientBackpackView.get().applyCursor(payload.cursor());
        }
        cursor = payload.cursor().isEmpty() ? ItemStack.EMPTY : payload.cursor().copy();
    }

    private static List<ItemStack> copy(List<ItemStack> in) {
        List<ItemStack> out = new ArrayList<>(in.size());
        for (ItemStack stack : in) {
            out.add(stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        return out;
    }

    /**
     * Puts the members' cells one after the other into the panel's view. With {@code resetOrder} (a new epoch: the members are not the same any
     * more) the display order is made again if the sort button had been used; otherwise (a member's cells changed) it stays as it is.
     */
    private void compose(boolean resetOrder) {
        int[] newSizes = new int[slices.length];
        List<ItemStack> flat = new ArrayList<>();
        for (int i = 0; i < slices.length; i++) {
            newSizes[i] = slices[i].slots().size();
            flat.addAll(slices[i].slots());
        }
        boolean sameLayout = java.util.Arrays.equals(newSizes, sizes);
        sizes = newSizes;
        addressing = AllAddressing.of(sizes);
        ClientBackpackView.get().applyCombined(flat, cursor);
        if (resetOrder || !sameLayout) {
            if (sorted) {
                recomputeSort();
            } else {
                sortOrder = null;
            }
            FloatingPanelState.get().setScrollRow(0);
        }
        bump();
        FloatingPanelState.get().setTotalRows(ClientBackpackView.get().rows());
    }

    private void recomputeSort() {
        ClientBackpackView view = ClientBackpackView.get();
        Comparator<Integer> byKind = (a, b) -> ItemSortOrder.INSTANCE.compare(view.getStackInSlot(a), view.getStackInSlot(b));
        sortOrder = AllSortPermutation.of(addressing.total(), i -> view.getStackInSlot(i).isEmpty(), byKind);
    }

    private void bump() {
        version++;
        cachedMap = null;
    }

    /** The grid as the panel shows it at {@code cols} columns: the sorted order if the sort button was used, else each backpack from a new row. */
    int[] displayMap(int cols) {
        if (sorted && sortOrder != null) {
            return sortOrder;
        }
        if (cachedMap == null || cachedCols != cols || cachedVersion != version) {
            cachedMap = AllGrid.displayMap(sizes);
            cachedCols = cols;
            cachedVersion = version;
        }
        return cachedMap;
    }
}
