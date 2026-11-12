package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionPacks;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionRules;
import pnnh.mcfloatingbackpack.inventory.extension.ItemCategoryMatcher;
import pnnh.mcfloatingbackpack.inventory.extension.PlacedPacks;
import pnnh.mcfloatingbackpack.inventory.extension.Row;
import pnnh.mcfloatingbackpack.inventory.extension.RowReconciler;
import pnnh.mcfloatingbackpack.inventory.extension.RowSet;
import pnnh.mcfloatingbackpack.inventory.extension.RowView;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.inventory.trash.TrashContents;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.item.TankItem;
import pnnh.mcfloatingbackpack.tank.FluidKind;
import pnnh.mcfloatingbackpack.tank.FluidStore;
import pnnh.mcfloatingbackpack.tank.TankCatalog;

/**
 * Mutable working copy of one backpack item. Capacity follows that item's tier.
 *
 * <p>035: the main area is the cells of the tier capacity followed by the cells of the visible rows of the category
 * extensions (nine per row, in the order of the rows); {@link #size()} counts all of them, {@link #baseCapacity()} only the
 * first part. The hidden rows are not in the main area at all; they are only carried along. The rows are reconciled with
 * the extension packs placed in the tool areas (and on the cursor) when the backpack is loaded and when it is saved.
 */
public final class BackpackInventory {
    /** Most cells the capacity of a tier can have (the netherite backpack). */
    public static final int MAX_TIER_CAPACITY = ExtensionCategories.MAX_TIER_CAPACITY;
    /** Most cells the main area can have: the capacity of the tier and every row of the category extensions (035). */
    public static final int MAX_MAIN_SLOTS = ExtensionCategories.MAX_MAIN_SLOTS;

    private final int baseCapacity;
    private final ItemStackHandler items;
    /** The categories of the visible rows, in order; the cells of row {@code i} are the nine after the base capacity plus {@code 9 * i}. */
    private List<String> visibleCategories = List.of();
    private List<Row<ItemStack>> hiddenRows = List.of();
    /** The three tool areas (016), indexed by {@link PanelArea#ordinal()}; the MAIN slot of the array is unused. */
    private final ItemStackHandler[] tools = new ItemStackHandler[PanelArea.values().length];
    private ItemStack cursor = ItemStack.EMPTY;
    private ItemTags tags = ItemTags.EMPTY;
    private TrashContents trash = TrashContents.EMPTY;
    private PickupBlacklist pickupBlacklist = PickupBlacklist.EMPTY;
    /** 048: what the backpack holds of each liquid; it stays when a tank is taken out. */
    private FluidStore fluids = FluidStore.EMPTY;

    public BackpackInventory(int capacity) {
        int size = Math.max(1, Math.min(MAX_TIER_CAPACITY, capacity));
        this.baseCapacity = size;
        this.items = new ItemStackHandler(size);
        for (PanelArea area : PanelArea.values()) {
            if (area.isTool()) {
                tools[area.ordinal()] = new ItemStackHandler(ToolAreaCapacity.of(area, size));
            }
        }
    }

    /** The main area's cells: the tier capacity and the cells of the visible rows. */
    public int size() {
        return items.getSlots();
    }

    /** The tier capacity: the cells before the rows of the category extensions, which is what sizes the tool areas. */
    public int baseCapacity() {
        return baseCapacity;
    }

    /** The categories of the visible rows, in order. */
    public List<String> visibleRowCategories() {
        return visibleCategories;
    }

    /** The hidden rows, in the order they were hidden. */
    public List<Row<ItemStack>> hiddenRows() {
        return hiddenRows;
    }

    /** The rows as they are in the working copy now (not reconciled). */
    public RowSet<ItemStack> rowSet() {
        List<Row<ItemStack>> visible = new ArrayList<>(visibleCategories.size());
        for (int row = 0; row < visibleCategories.size(); row++) {
            List<ItemStack> cells = new ArrayList<>(ExtensionCategories.ROW_CELLS);
            for (int cell = 0; cell < ExtensionCategories.ROW_CELLS; cell++) {
                cells.add(items.getStackInSlot(baseCapacity + row * ExtensionCategories.ROW_CELLS + cell).copy());
            }
            visible.add(new Row<>(visibleCategories.get(row), cells));
        }
        return new RowSet<>(visible, hiddenRows);
    }

    /** What the client is told about the rows besides their cells. */
    public RowView rowView() {
        return new RowView(baseCapacity, visibleCategories, new RowSet<>(List.of(), hiddenRows).hiddenSummary(ItemStack::isEmpty));
    }

    /** Whether a stack fits what {@code slot} of the main area takes: anything in the tier capacity, a row's category in a row. */
    public boolean canPlaceMain(int slot, ItemStack stack) {
        if (slot < baseCapacity) {
            return slot >= 0;
        }
        return ExtensionRules.canPlace(rowSet(), baseCapacity, slot, stack, new ItemCategoryMatcher(tags));
    }

    /**
     * Where a stack is put when it is moved into the main area, as segments to try one after the other (the first is the cells
     * of the visible category rows it fits, the second the tier capacity's cells, the third the cells of the universal rows, 038:
     * the overflow); see {@link ExtensionRules#targetSegments}. With no rows the first and the last segment are empty and the
     * second is {@code 0..size()-1}, as it always was.
     */
    public List<int[]> mainTargetOrder(ItemStack stack) {
        if (visibleCategories.isEmpty()) {
            int[] all = new int[size()];
            for (int i = 0; i < all.length; i++) {
                all[i] = i;
            }
            return List.of(new int[0], all, new int[0]);
        }
        return ExtensionRules.targetSegments(rowSet(), baseCapacity, stack, new ItemCategoryMatcher(tags));
    }

    /**
     * Moves a stack into the main area: into the visible rows that take it first, then into the tier capacity's cells, each time
     * onto same-kind stacks before empty cells. Returns what did not fit.
     */
    public ItemStack insertIntoMain(ItemStack stack) {
        return QuickTransferLogic.moveIntoSegments(
                ItemStackTransferOps.INSTANCE,
                stack,
                mainTargetOrder(stack),
                this::getStackInSlot,
                (written, index) -> setStackInSlot(index, written));
    }

    /**
     * 047: tidies the main area (the tier capacity's cells and the cells of the visible rows): stacks of one kind are merged, the kinds
     * are put in registry order and every stack goes where {@link BackpackSorter} says (the rows that take it first, then the tier
     * capacity's cells, the universal rows last). Nothing else is touched: not the tool areas, the hidden rows, the cursor, the tags,
     * the trash or the pickup blacklist. Returns false, with nothing changed, when it cannot be done without losing or inventing
     * anything.
     */
    public boolean sortMain() {
        List<ItemStack> before = new ArrayList<>(size());
        for (int slot = 0; slot < size(); slot++) {
            before.add(items.getStackInSlot(slot).copy());
        }
        List<ItemStack> after = BackpackSorter.sort(
                before, baseCapacity, visibleCategories, new ItemCategoryMatcher(tags), ItemStackTransferOps.INSTANCE, ItemSortOrder.INSTANCE);
        if (after == null) {
            return false;
        }
        for (int slot = 0; slot < after.size(); slot++) {
            items.setStackInSlot(slot, after.get(slot));
        }
        return true;
    }

    /** Replaces the rows: resizes the main area to the tier capacity plus the visible rows and fills their cells. */
    private void applyRows(RowSet<ItemStack> rows) {
        List<ItemStack> base = new ArrayList<>(baseCapacity);
        for (int i = 0; i < baseCapacity; i++) {
            base.add(items.getStackInSlot(i));
        }
        items.setSize(baseCapacity + rows.visibleCells());
        for (int i = 0; i < baseCapacity; i++) {
            items.setStackInSlot(i, base.get(i));
        }
        List<String> categories = new ArrayList<>(rows.visible().size());
        for (int row = 0; row < rows.visible().size(); row++) {
            categories.add(rows.visible().get(row).category());
            List<ItemStack> cells = rows.visible().get(row).cells();
            for (int cell = 0; cell < ExtensionCategories.ROW_CELLS; cell++) {
                items.setStackInSlot(
                        baseCapacity + row * ExtensionCategories.ROW_CELLS + cell, cells.get(cell).copy());
            }
        }
        visibleCategories = List.copyOf(categories);
        List<Row<ItemStack>> hidden = new ArrayList<>(rows.hidden().size());
        for (Row<ItemStack> row : rows.hidden()) {
            List<ItemStack> cells = new ArrayList<>(row.cells().size());
            for (ItemStack cell : row.cells()) {
                cells.add(cell.copy());
            }
            hidden.add(new Row<>(row.category(), cells));
        }
        hiddenRows = List.copyOf(hidden);
    }

    /**
     * The packs placed, counted by category: a category extension counts wherever it is (the three tool areas and the cursor); a
     * universal extension (038) only in the left or right area of a backpack of its own tier, once per area (see
     * {@link PlacedPacks}).
     */
    public Map<String, Integer> placedPacks() {
        List<PlacedPacks.Pack> packs = new ArrayList<>();
        for (PanelArea area : PanelArea.values()) {
            if (area.isTool()) {
                PlacedPacks.Place place = switch (area) {
                    case LEFT -> PlacedPacks.Place.LEFT;
                    case RIGHT -> PlacedPacks.Place.RIGHT;
                    default -> PlacedPacks.Place.BOTTOM;
                };
                ItemStackHandler handler = handler(area);
                for (int i = 0; i < handler.getSlots(); i++) {
                    notePack(packs, handler.getStackInSlot(i), place);
                }
            }
        }
        notePack(packs, cursor, PlacedPacks.Place.CURSOR);
        BackpackTier tier = BackpackTier.byCapacity(baseCapacity);
        return PlacedPacks.count(packs, tier == null ? null : tier.name().toLowerCase());
    }

    private static void notePack(List<PlacedPacks.Pack> packs, ItemStack stack, PlacedPacks.Place place) {
        String category = ExtensionPacks.categoryOf(stack);
        if (category == null) {
            return;
        }
        if (stack.getCount() > 1) {
            MCFloatingBackpack.LOGGER.warn("An extension pack stack of {} holds {} items; counted as one", category, stack.getCount());
        }
        packs.add(new PlacedPacks.Pack(category, ExtensionPacks.tierOf(stack), place));
    }

    /** The rows brought in line with the packs placed (035). */
    private RowSet<ItemStack> reconciledRows() {
        return RowReconciler.reconcile(
                        rowSet(),
                        placedPacks(),
                        ItemStack::isEmpty,
                        () -> ItemStack.EMPTY,
                        ExtensionCategories.MAX_ROWS)
                .rows();
    }

    public ItemStackHandler items() {
        return items;
    }

    /** The slots of a tool area; the main slots are {@link #items()}. */
    public ItemStackHandler handler(PanelArea area) {
        return area.isTool() ? tools[area.ordinal()] : items;
    }

    /** Slot count of an area; for a tool area this can exceed its capacity after a capacity decrease. */
    public int size(PanelArea area) {
        return handler(area).getSlots();
    }

    public ItemStack getStackInSlot(PanelArea area, int slot) {
        ItemStackHandler handler = handler(area);
        if (slot < 0 || slot >= handler.getSlots()) {
            return ItemStack.EMPTY;
        }
        return handler.getStackInSlot(slot);
    }

    public void setStackInSlot(PanelArea area, int slot, ItemStack stack) {
        ItemStackHandler handler = handler(area);
        if (slot < 0 || slot >= handler.getSlots()) {
            return;
        }
        handler.setStackInSlot(slot, stack);
    }

    /** The three tool areas as stored in the data component. */
    public ToolAreaContents toolContents() {
        return ToolAreaContents.normalize(
                list(PanelArea.LEFT), list(PanelArea.RIGHT), list(PanelArea.BOTTOM));
    }

    /** Copies of the stacks of a tool area, its whole stored list (for the placement rules). */
    public List<ItemStack> toolStacks(PanelArea area) {
        return list(area);
    }

    private List<ItemStack> list(PanelArea area) {
        ItemStackHandler handler = handler(area);
        List<ItemStack> out = new ArrayList<>(handler.getSlots());
        for (int i = 0; i < handler.getSlots(); i++) {
            out.add(handler.getStackInSlot(i).copy());
        }
        return out;
    }

    /**
     * 021: whether the right area holds a trash can, which is what makes the trash can panel available. Looks at the
     * whole stored list, not at the cells the panel currently shows.
     */
    public boolean hasTrashCan() {
        for (int i = 0; i < size(PanelArea.RIGHT); i++) {
            if (getStackInSlot(PanelArea.RIGHT, i).is(ModItems.TRASH_CAN.get())) {
                return true;
            }
        }
        return false;
    }

    /** The tags of this backpack, by item category (018). */
    public ItemTags tags() {
        return tags;
    }

    public void setTags(ItemTags tags) {
        this.tags = tags == null ? ItemTags.EMPTY : tags;
    }

    /** What the trash can holds (021), newest group first. */
    public TrashContents trash() {
        return trash;
    }

    public void setTrash(TrashContents trash) {
        this.trash = trash == null ? TrashContents.EMPTY : trash;
    }

    /**
     * 025: whether the bottom (function) area holds a tag manager, which is what lets the tag overview change tags. Looks
     * at the whole stored list, not at the cells the panel currently shows.
     */
    public boolean hasTagManager() {
        for (int i = 0; i < size(PanelArea.BOTTOM); i++) {
            if (getStackInSlot(PanelArea.BOTTOM, i).is(ModItems.TAG_MANAGER.get())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 024: whether the left area holds an auto pickup, which is what makes the pickup blacklist editable. Looks at the
     * whole stored list, not at the cells the panel currently shows.
     */
    public boolean hasAutoPickup() {
        for (int i = 0; i < size(PanelArea.LEFT); i++) {
            if (getStackInSlot(PanelArea.LEFT, i).is(ModItems.AUTO_PICKUP.get())) {
                return true;
            }
        }
        return false;
    }

    /** 048: what the backpack holds of each liquid. */
    public FluidStore fluids() {
        return fluids;
    }

    public void setFluids(FluidStore fluids) {
        this.fluids = fluids == null ? FluidStore.EMPTY : fluids;
    }

    /** 048: the tier of this backpack, or {@code null} if its capacity is not one of the tiers'. */
    public BackpackTier tier() {
        return BackpackTier.byCapacity(baseCapacity);
    }

    /**
     * 048: the tier of the tank that counts for {@code kind}: the one in the bottom area (looks at the whole stored list, not at the
     * cells the panel currently shows), or {@code null} if there is none. There is at most one per liquid.
     */
    public BackpackTier tankTier(FluidKind kind) {
        for (int i = 0; i < size(PanelArea.BOTTOM); i++) {
            TankCatalog.Tank tank = TankItem.tankOf(getStackInSlot(PanelArea.BOTTOM, i));
            if (tank != null && tank.kind() == kind) {
                return tank.tier();
            }
        }
        return null;
    }

    /** The tags of which an item category carrying one is left on the ground by the auto pickup (024). */
    public PickupBlacklist pickupBlacklist() {
        return pickupBlacklist;
    }

    public void setPickupBlacklist(PickupBlacklist pickupBlacklist) {
        this.pickupBlacklist = pickupBlacklist == null ? PickupBlacklist.EMPTY : pickupBlacklist;
    }

    public ItemStack getCursor() {
        return cursor;
    }

    public void setCursor(ItemStack cursor) {
        this.cursor = cursor.isEmpty() ? ItemStack.EMPTY : cursor;
    }

    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= size()) {
            return ItemStack.EMPTY;
        }
        return items.getStackInSlot(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        if (slot < 0 || slot >= size()) {
            return;
        }
        items.setStackInSlot(slot, stack);
    }

    public static BackpackInventory fromContents(BackpackContents contents, int capacity) {
        BackpackInventory inventory = new BackpackInventory(capacity);
        if (contents == null) {
            return inventory;
        }
        BackpackContents fitted = contents.fit(inventory.baseCapacity());
        for (int i = 0; i < inventory.baseCapacity(); i++) {
            inventory.setStackInSlot(i, fitted.items().get(i).copy());
        }
        inventory.setCursor(fitted.cursor());
        inventory.setTags(fitted.tags());
        inventory.setTrash(fitted.trash());
        inventory.setPickupBlacklist(fitted.pickupBlacklist());
        inventory.setFluids(fitted.fluids());
        for (PanelArea area : PanelArea.values()) {
            if (!area.isTool()) {
                continue;
            }
            List<ItemStack> stored = fitted.tools().list(area);
            ItemStackHandler handler = inventory.handler(area);
            handler.setSize(stored.size());
            for (int i = 0; i < stored.size(); i++) {
                handler.setStackInSlot(i, stored.get(i).copy());
            }
        }
        // 035: the rows, reconciled with the packs that are placed now.
        inventory.applyRows(fitted.rows());
        inventory.applyRows(inventory.reconciledRows());
        return inventory;
    }

    public BackpackContents toContents() {
        List<ItemStack> list = new ArrayList<>(baseCapacity);
        for (int i = 0; i < baseCapacity; i++) {
            list.add(getStackInSlot(i).copy());
        }
        // 040: every save stamps the revision of the data it writes (a development switch can stamp another, to make a backpack that
        // looks as if a newer version had saved it).
        Integer stamp = pnnh.mcfloatingbackpack.version.VersionDebug.stampRevisionOverride();
        return BackpackContents.fit(
                        list, getCursor().copy(), toolContents(), tags, trash, pickupBlacklist, reconciledRows(), fluids, baseCapacity)
                .withDataRevision(stamp != null ? stamp : pnnh.mcfloatingbackpack.inventory.revision.DataRevision.CURRENT);
    }

    public static int capacityOf(ItemStack backpack) {
        if (backpack.getItem() instanceof FloatingBackpackItem item) {
            return item.tier().capacity();
        }
        return BackpackTier.LEATHER.capacity();
    }

    public static BackpackInventory fromItem(ItemStack backpack) {
        int capacity = capacityOf(backpack);
        if (backpack.isEmpty()) {
            return new BackpackInventory(capacity);
        }
        BackpackContents contents = backpack.getOrDefault(
                ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty(capacity));
        return fromContents(contents, capacity);
    }

    public void saveToItem(ItemStack backpack) {
        if (backpack.isEmpty()) {
            return;
        }
        backpack.set(ModDataComponents.BACKPACK_CONTENTS.get(), toContents());
    }
}
