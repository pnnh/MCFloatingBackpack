package pnnh.mcfloatingbackpack.client.panel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.ToolAreaCapacity;
import pnnh.mcfloatingbackpack.inventory.ToolAreaContents;
import pnnh.mcfloatingbackpack.inventory.extension.RowView;
import pnnh.mcfloatingbackpack.item.BackpackTier;

/**
 * Client mirror of the server backpack. Updated only by network payloads.
 */
public final class ClientBackpackView {
    private static final ClientBackpackView INSTANCE = new ClientBackpackView();

    private NonNullList<ItemStack> slots =
            NonNullList.withSize(BackpackTier.LEATHER.capacity(), ItemStack.EMPTY);
    /** The three tool areas (016); a list's length is the area's slot count. */
    private final Map<PanelArea, NonNullList<ItemStack>> tools = new EnumMap<>(PanelArea.class);
    private ItemStack cursor = ItemStack.EMPTY;
    /** 035: the tier capacity, the visible rows' categories and the hidden items' summary; the layout does not read it yet (036). */
    private RowView rowView = RowView.none(BackpackTier.LEATHER.capacity());
    private pnnh.mcfloatingbackpack.inventory.tags.ItemTags tags = pnnh.mcfloatingbackpack.inventory.tags.ItemTags.EMPTY;
    private pnnh.mcfloatingbackpack.inventory.trash.TrashContents trash =
            pnnh.mcfloatingbackpack.inventory.trash.TrashContents.EMPTY;
    private pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist pickupBlacklist =
            pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist.EMPTY;
    /** 048: what the open backpack holds of each liquid. */
    private pnnh.mcfloatingbackpack.tank.FluidStore fluids = pnnh.mcfloatingbackpack.tank.FluidStore.EMPTY;
    private BackpackRef activeRef;

    private ClientBackpackView() {
        resetTools(BackpackTier.LEATHER.capacity());
    }

    private void resetTools(int mainCapacity) {
        for (PanelArea area : PanelArea.values()) {
            if (area.isTool()) {
                tools.put(area, NonNullList.withSize(ToolAreaCapacity.of(area, mainCapacity), ItemStack.EMPTY));
            }
        }
    }

    /** The panel's geometry for the current position, size and scroll, with this view's slot counts. */
    public PanelLayout layout() {
        return FloatingPanelState.get().layoutModel(new int[] {
            slots.size(), toolCount(PanelArea.LEFT), toolCount(PanelArea.RIGHT), toolCount(PanelArea.BOTTOM)});
    }

    /** Slot count of a tool area (0 for MAIN: use {@link #slotCount()}). */
    public int toolCount(PanelArea area) {
        NonNullList<ItemStack> list = tools.get(area);
        return list == null ? 0 : list.size();
    }

    /** 018: whether the function area (bottom row) holds a tag manager, which is what makes the tag panel available. */
    public boolean hasTagManager() {
        for (int i = 0; i < toolCount(PanelArea.BOTTOM); i++) {
            if (getToolStack(PanelArea.BOTTOM, i).is(pnnh.mcfloatingbackpack.item.ModItems.TAG_MANAGER.get())) {
                return true;
            }
        }
        return false;
    }

    /** 021: whether the right area holds a trash can, anywhere in its whole list (not only in the visible cells). */
    public boolean hasTrashCan() {
        for (int i = 0; i < toolCount(PanelArea.RIGHT); i++) {
            if (getToolStack(PanelArea.RIGHT, i).is(pnnh.mcfloatingbackpack.item.ModItems.TRASH_CAN.get())) {
                return true;
            }
        }
        return false;
    }

    /** 024: whether the left area holds an auto pickup, anywhere in its whole list (not only in the visible cells). */
    public boolean hasAutoPickup() {
        for (int i = 0; i < toolCount(PanelArea.LEFT); i++) {
            if (getToolStack(PanelArea.LEFT, i).is(pnnh.mcfloatingbackpack.item.ModItems.AUTO_PICKUP.get())) {
                return true;
            }
        }
        return false;
    }

    /** The stacks of a tool area, its whole list (for the placement rules); a read-only view. */
    public List<ItemStack> toolStacks(PanelArea area) {
        NonNullList<ItemStack> list = tools.get(area);
        return list == null ? List.of() : java.util.Collections.unmodifiableList(list);
    }

    public ItemStack getToolStack(PanelArea area, int index) {
        NonNullList<ItemStack> list = tools.get(area);
        if (list == null || index < 0 || index >= list.size()) {
            return ItemStack.EMPTY;
        }
        return list.get(index);
    }

    public static ClientBackpackView get() {
        return INSTANCE;
    }

    public int slotCount() {
        return slots.size();
    }

    /**
     * 036: the tier's own capacity, without the cells of the extension rows. {@link #slotCount()} is that plus the visible rows,
     * so it no longer names a tier (the title icon and the tooltip look the tier up by this one).
     */
    public int baseCapacity() {
        return rowView.baseCapacity() > 0 ? rowView.baseCapacity() : slots.size();
    }

    /**
     * Virtual row count after reflowing the backpack's real slots across the player's current
     * column setting (007): {@code ceil(capacity / visibleCols)}, not a fixed 9-wide division.
     */
    public int rows() {
        int cols = Math.max(1, FloatingPanelState.get().getVisibleCols());
        return Math.max(1, (slots.size() + cols - 1) / cols);
    }

    public ItemStack getStackInSlot(int index) {
        if (index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        return slots.get(index);
    }

    /** 018: the open backpack's tags, by item category. */
    public pnnh.mcfloatingbackpack.inventory.tags.ItemTags tags() {
        return tags;
    }

    public void setTags(pnnh.mcfloatingbackpack.inventory.tags.ItemTags tags) {
        this.tags = tags == null ? pnnh.mcfloatingbackpack.inventory.tags.ItemTags.EMPTY : tags;
    }

    /** 021: what the open backpack's trash can holds, newest group first. */
    public pnnh.mcfloatingbackpack.inventory.trash.TrashContents trash() {
        return trash;
    }

    public void setTrash(pnnh.mcfloatingbackpack.inventory.trash.TrashContents trash) {
        this.trash = trash == null ? pnnh.mcfloatingbackpack.inventory.trash.TrashContents.EMPTY : trash;
    }

    /** 024: the open backpack's pickup blacklist. */
    public pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist pickupBlacklist() {
        return pickupBlacklist;
    }

    public void setPickupBlacklist(pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist pickupBlacklist) {
        this.pickupBlacklist = pickupBlacklist == null
                ? pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist.EMPTY
                : pickupBlacklist;
    }

    /** 048: what the open backpack holds of each liquid. */
    public pnnh.mcfloatingbackpack.tank.FluidStore fluids() {
        return fluids;
    }

    public void setFluids(pnnh.mcfloatingbackpack.tank.FluidStore fluids) {
        this.fluids = fluids == null ? pnnh.mcfloatingbackpack.tank.FluidStore.EMPTY : fluids;
    }

    /** 048: the tier of the open backpack, or {@code null} if its capacity is not one of the tiers'. */
    public BackpackTier backpackTier() {
        return BackpackTier.byCapacity(baseCapacity());
    }

    /** 048: the tier of the tank that counts for {@code kind} (the one in the bottom area, anywhere in its whole list), or {@code null}. */
    public BackpackTier tankTier(pnnh.mcfloatingbackpack.tank.FluidKind kind) {
        for (int i = 0; i < toolCount(PanelArea.BOTTOM); i++) {
            pnnh.mcfloatingbackpack.tank.TankCatalog.Tank tank =
                    pnnh.mcfloatingbackpack.item.TankItem.tankOf(getToolStack(PanelArea.BOTTOM, i));
            if (tank != null && tank.kind() == kind) {
                return tank.tier();
            }
        }
        return null;
    }

    /** 048: the liquids this backpack holds that no tank in the bottom area counts for (the title bar's yellow dot, its hint and {@code /hidden} say so). */
    public java.util.List<pnnh.mcfloatingbackpack.tank.HiddenLiquids.Line> hiddenLiquids() {
        return pnnh.mcfloatingbackpack.tank.HiddenLiquids.lines(fluids, kind -> tankTier(kind) != null);
    }

    public ItemStack getCursor() {
        return cursor;
    }

    public BackpackRef getActiveRef() {
        return activeRef;
    }

    public void setActiveRef(BackpackRef activeRef) {
        this.activeRef = activeRef;
    }

    public void clearActiveRef() {
        this.activeRef = null;
    }

    /** 035: what the server said about the rows (where they start, their categories, what is hidden). */
    public RowView rowView() {
        return rowView;
    }

    public void applyFull(List<ItemStack> synced, ToolAreaContents syncedTools, ItemStack syncedCursor, RowView syncedRows) {
        int count = synced == null ? 0 : synced.size();
        if (count <= 0) {
            count = BackpackTier.LEATHER.capacity();
        }
        // 037: whether the backpack itself changed size (another tier or backpack); the rows coming and going does not count.
        int previousBase = baseCapacity();
        rowView = syncedRows == null ? RowView.none(count) : syncedRows;
        // The tool areas are sized by the tier capacity, not by the main area, which the rows make bigger.
        int base = rowView.baseCapacity() > 0 ? rowView.baseCapacity() : count;
        boolean sizeChanged = base != previousBase;
        NonNullList<ItemStack> next = NonNullList.withSize(count, ItemStack.EMPTY);
        for (int i = 0; i < count; i++) {
            ItemStack stack = synced != null && i < synced.size() ? synced.get(i) : ItemStack.EMPTY;
            next.set(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        slots = next;
        for (PanelArea area : PanelArea.values()) {
            if (!area.isTool()) {
                continue;
            }
            List<ItemStack> source = syncedTools == null ? List.of() : syncedTools.list(area);
            int toolSize = source.isEmpty() ? ToolAreaCapacity.of(area, base) : source.size();
            NonNullList<ItemStack> toolNext = NonNullList.withSize(toolSize, ItemStack.EMPTY);
            for (int i = 0; i < toolSize && i < source.size(); i++) {
                ItemStack stack = source.get(i);
                toolNext.set(i, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
            }
            tools.put(area, toolNext);
        }
        cursor = syncedCursor == null || syncedCursor.isEmpty() ? ItemStack.EMPTY : syncedCursor.copy();
        if (sizeChanged) {
            FloatingPanelState.get().setScrollRow(0);
        }
        reportCounts();
        FloatingPanelState.get().setTotalRows(rows());
    }

    public void applySlotUpdate(PanelArea area, int slotIndex, ItemStack stack, ItemStack syncedCursor) {
        List<ItemStack> target = area.isTool() ? tools.get(area) : slots;
        if (target != null && slotIndex >= 0 && slotIndex < target.size()) {
            target.set(slotIndex, stack == null || stack.isEmpty() ? ItemStack.EMPTY : stack.copy());
        }
        cursor = syncedCursor == null || syncedCursor.isEmpty() ? ItemStack.EMPTY : syncedCursor.copy();
    }

    public void clear() {
        slots = NonNullList.withSize(BackpackTier.LEATHER.capacity(), ItemStack.EMPTY);
        resetTools(BackpackTier.LEATHER.capacity());
        cursor = ItemStack.EMPTY;
        rowView = RowView.none(BackpackTier.LEATHER.capacity());
        tags = pnnh.mcfloatingbackpack.inventory.tags.ItemTags.EMPTY;
        trash = pnnh.mcfloatingbackpack.inventory.trash.TrashContents.EMPTY;
        pickupBlacklist = pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist.EMPTY;
        fluids = pnnh.mcfloatingbackpack.tank.FluidStore.EMPTY;
        activeRef = null;
        FloatingPanelState.get().setScrollRow(0);
        reportCounts();
        FloatingPanelState.get().setTotalRows(rows());
    }

    /**
     * The panel state clamps each tool area's scrolling against its slot count (a changed count resets that
     * area) and decides which panel sizes are valid from all the counts (017).
     */
    private void reportCounts() {
        // 037: the panel's valid sizes (017) follow the tier capacity alone, so putting extension packs in or out never changes the
        // shapes the player can choose; the rows are only more lines to scroll to (the total rows are still counted over every cell).
        FloatingPanelState.get().setMainCount(baseCapacity());
        for (PanelArea area : PanelArea.values()) {
            if (area.isTool()) {
                FloatingPanelState.get().setToolCount(area, toolCount(area));
            }
        }
    }
}
