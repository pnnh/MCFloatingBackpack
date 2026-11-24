package pnnh.mcfloatingbackpack.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.compat.ServerCompatDenials;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.inventory.BackpackClickLogic;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.PanelCursor;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic.DragMode;
import pnnh.mcfloatingbackpack.inventory.PanelCursorRequest;
import pnnh.mcfloatingbackpack.inventory.PanelCursorRequest.Mode;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest;
import pnnh.mcfloatingbackpack.inventory.combined.AllAddressing;
import pnnh.mcfloatingbackpack.inventory.combined.AllContext;
import pnnh.mcfloatingbackpack.inventory.combined.AllRequestRules;
import pnnh.mcfloatingbackpack.inventory.combined.BackpackSlots;
import pnnh.mcfloatingbackpack.inventory.combined.PanelTargets;
import pnnh.mcfloatingbackpack.inventory.combined.SlotAccess;

/**
 * Client request for a drag gesture on the floating panel while holding a stack on the cursor (013):
 * distribute it over the slots that were dragged across, collect same-kind items into it, or the
 * double-click gather. The cursor is wherever the player's held stack lives right now (the open menu's
 * carried stack, or the panel's own cursor); {@code screenOpen} is reported by the client like in
 * {@link BackpackClickPayload}. The server re-resolves the backpack and re-checks every slot; nothing in
 * this request is trusted.
 */
public record BackpackCursorGesturePayload(byte mode, int[] slots, boolean screenOpen, int epoch)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackCursorGesturePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "cursor_gesture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackCursorGesturePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.mode());
                        buf.writeBoolean(payload.screenOpen());
                        buf.writeVarInt(payload.epoch());
                        buf.writeVarInt(payload.slots().length);
                        for (int slot : payload.slots()) {
                            buf.writeVarInt(slot);
                        }
                    },
                    buf -> {
                        byte mode = buf.readByte();
                        boolean screenOpen = buf.readBoolean();
                        int epoch = buf.readVarInt();
                        int count = buf.readVarInt();
                        if (count < 0 || count > AllAddressing.MAX_MEMBERS * QuickTransferRequest.MAX_BACKPACK_SLOTS) {
                            throw new DecoderException("cursor gesture slot count out of range: " + count);
                        }
                        int[] slots = new int[count];
                        for (int i = 0; i < count; i++) {
                            slots[i] = buf.readVarInt();
                        }
                        return new BackpackCursorGesturePayload(mode, slots, screenOpen, epoch);
                    });

    public static BackpackCursorGesturePayload of(Mode mode, int[] slots, boolean screenOpen) {
        return of(mode, slots, screenOpen, 0);
    }

    /** 066: {@code epoch} is 0 on a single backpack's tab, the "all" tab's epoch on that tab (the slots are then flat cell numbers). */
    public static BackpackCursorGesturePayload of(Mode mode, int[] slots, boolean screenOpen, int epoch) {
        return new BackpackCursorGesturePayload(mode.toByte(), slots, screenOpen, epoch);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackCursorGesturePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // 066: on the "all" tab the slots are flat cell numbers of the whole grid
            if (PanelTargets.inAll(player)) {
                handleAll(payload, player);
                return;
            }
            if (!AllRequestRules.epochOk(false, 0, payload.epoch())) {
                return;
            }
            BackpackRef ref = PanelTargets.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // 026: every request into a backpack goes through the registry first.
            BackpackInventory backpack = BackpackGate.acquireForPanel(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            Mode mode = Mode.fromByte(payload.mode());
            if (!PanelCursorRequest.validate(mode, payload.slots(), backpack.size())) {
                return;
            }

            AbstractContainerMenu menu = player.containerMenu;
            if (payload.screenOpen() && menu != null
                    && !ServerCompatDenials.allows(player, menu, Action.USE_MENU_CURSOR)) {
                return;
            }
            PanelCursor cursor = PanelCursor.of(payload.screenOpen(), menu, backpack);
            if (cursor.usesMenu()) {
                BackpackMenuBridge.mergePanelCursorIntoCarried(backpack, menu);
            }
            ItemStack held = cursor.get();
            if (held.isEmpty()) {
                return; // the cursor changed since the client decided (for example it was emptied)
            }

            // 064: a floating backpack (or an item with one inside) is not spread over the slots, unless it is what was just taken out of this one.
            if (mode == Mode.DISTRIBUTE_LEFT || mode == Mode.DISTRIBUTE_RIGHT) {
                pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict nesting =
                        pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.refusal(player, ref, held);
                if (nesting != pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict.OK) {
                    pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.tell(player, nesting);
                    return;
                }
                if (pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.isRisky(held)) {
                    pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.forget(player.getUUID()); // the record is used up
                }
            }
            SlotAccess<ItemStack> cells = new BackpackSlots(backpack);
            int before = total(cells, held);
            DiagLog.record(player.getUUID(), "cursor gesture " + mode + " slots=" + payload.slots().length + " held=" + DiagLog.stack(held)
                    + " viaMenu=" + cursor.usesMenu() + " hand=" + DiagLog.stack(player.getMainHandItem()));
            switch (mode) {
                case DISTRIBUTE_LEFT -> distribute(cells, cursor, held, payload.slots(), DragMode.LEFT);
                case DISTRIBUTE_RIGHT -> distribute(cells, cursor, held, payload.slots(), DragMode.RIGHT);
                case GATHER_DRAG -> gatherSlots(cells, cursor, held, payload.slots());
                case GATHER_ALL -> gatherAll(cells, cursor, held);
            }
            int after = total(cells, cursor.get());
            if (before != after) {
                // Cannot happen with the pure logic under test; if it ever does, the log says so.
                MCFloatingBackpack.LOGGER.error(
                        "Panel cursor gesture {} changed the item total from {} to {}", mode, before, after);
            }

            ref.saveInventory(player, backpack);
            player.getInventory().setChanged();
            if (menu != null) {
                menu.broadcastChanges();
            }
            BackpackNetwork.sendFullSync(player, ref);
        });
    }

    /**
     * 066: a drag or gather on the "all" tab: the same gestures, over the flat grid of all the members. The tab's in-memory cursor is held by a
     * throw-away inventory for the gesture; every member that was written to is saved and sent.
     */
    private static void handleAll(BackpackCursorGesturePayload payload, ServerPlayer player) {
        AllContext context = AllContext.begin(player, payload.epoch());
        if (context == null) {
            return;
        }
        Mode mode = Mode.fromByte(payload.mode());
        if (!PanelCursorRequest.validate(
                mode, payload.slots(), context.addressing().total(), AllAddressing.MAX_MEMBERS * QuickTransferRequest.MAX_BACKPACK_SLOTS)) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (payload.screenOpen() && menu != null && !ServerCompatDenials.allows(player, menu, Action.USE_MENU_CURSOR)) {
            return;
        }
        BackpackInventory holder = context.cursorHolder();
        PanelCursor cursor = PanelCursor.of(payload.screenOpen(), menu, holder);
        if (cursor.usesMenu()) {
            BackpackMenuBridge.mergePanelCursorIntoCarried(holder, menu);
        }
        ItemStack held = cursor.get();
        if (held.isEmpty()) {
            context.takeCursorBack(holder);
            return; // the cursor changed since the client decided
        }
        AllAddressing.Cell first = context.addressing().locate(payload.slots()[0]);
        if (first == null) {
            context.abandon();
            return;
        }
        if (mode == Mode.DISTRIBUTE_LEFT || mode == Mode.DISTRIBUTE_RIGHT) {
            pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict nesting =
                    pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.refusal(player, context.ref(first.member()), held);
            if (nesting != pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict.OK) {
                pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.tell(player, nesting);
                context.takeCursorBack(holder);
                context.finish();
                return;
            }
            if (pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.isRisky(held)) {
                pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.forget(player.getUUID());
            }
        }
        SlotAccess<ItemStack> cells = context.slots();
        DiagLog.record(player.getUUID(), "all cursor gesture " + mode + " slots=" + payload.slots().length + " held=" + DiagLog.stack(held)
                + " viaMenu=" + cursor.usesMenu());
        switch (mode) {
            case DISTRIBUTE_LEFT -> distribute(cells, cursor, held, payload.slots(), DragMode.LEFT);
            case DISTRIBUTE_RIGHT -> distribute(cells, cursor, held, payload.slots(), DragMode.RIGHT);
            case GATHER_DRAG -> gatherSlots(cells, cursor, held, payload.slots());
            case GATHER_ALL -> gatherAll(cells, cursor, held);
        }
        context.takeCursorBack(holder);
        if (menu != null) {
            menu.broadcastChanges();
        }
        context.finish();
    }

    private static int total(SlotAccess<ItemStack> cells, ItemStack cursorStack) {
        int sum = cursorStack.getCount();
        for (int i = 0; i < cells.size(); i++) {
            sum += cells.get(i).getCount();
        }
        return sum;
    }

    private static void distribute(
            SlotAccess<ItemStack> cells, PanelCursor cursor, ItemStack held, int[] requested, DragMode mode) {
        ItemStackTransferOps ops = ItemStackTransferOps.INSTANCE;
        // Slots that cannot take part any more (changed since the client looked) are dropped, as vanilla
        // does when the drag ends; only the remaining ones share the stack.
        List<Integer> slots = new ArrayList<>();
        for (int slot : requested) {
            // 035: a row of the category extensions only takes what fits its category.
            if (cells.canPlace(slot, held)
                    && PanelCursorLogic.canQuickReplace(ops, cells.get(slot), held)) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        if (slots.size() == 1) {
            // A drag over a single slot is an ordinary click on it.
            ItemStack left = BackpackClickLogic.click(
                    cells::get,
                    (stack, index) -> cells.set(index, stack),
                    held,
                    slots.get(0),
                    mode == DragMode.RIGHT);
            cursor.set(left);
            return;
        }
        List<ItemStack> current = new ArrayList<>(slots.size());
        for (int slot : slots) {
            current.add(cells.get(slot).copy());
        }
        PanelCursorLogic.Distribution<ItemStack> result =
                PanelCursorLogic.distribute(ops, mode, held, current, Item.ABSOLUTE_MAX_STACK_SIZE);
        if (result.rejected()) {
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            cells.set(slots.get(i), result.stacks().get(i));
        }
        cursor.set(result.cursorLeft());
    }

    private static void gatherSlots(SlotAccess<ItemStack> cells, PanelCursor cursor, ItemStack held, int[] slots) {
        ItemStackTransferOps ops = ItemStackTransferOps.INSTANCE;
        ItemStack current = held;
        for (int slot : slots) {
            PanelCursorLogic.Gathered<ItemStack> g =
                    PanelCursorLogic.gather(ops, cells.get(slot), current);
            if (g.moved() > 0) {
                cells.set(slot, g.slot());
                current = g.cursor();
            }
        }
        cursor.set(current);
    }

    private static void gatherAll(SlotAccess<ItemStack> cells, PanelCursor cursor, ItemStack held) {
        List<ItemStack> all = new ArrayList<>(cells.size());
        for (int i = 0; i < cells.size(); i++) {
            all.add(cells.get(i).copy());
        }
        PanelCursorLogic.PickedAll<ItemStack> result =
                PanelCursorLogic.pickupAll(ItemStackTransferOps.INSTANCE, held, all);
        if (result.moved() <= 0) {
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            cells.set(i, result.slots().get(i));
        }
        cursor.set(result.cursor());
    }
}
