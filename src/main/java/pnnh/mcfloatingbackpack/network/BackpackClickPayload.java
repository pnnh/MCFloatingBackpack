package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.compat.ServerCompatDenials;
import pnnh.mcfloatingbackpack.inventory.BackpackClickLogic;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionPackRules;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting;
import pnnh.mcfloatingbackpack.inventory.nesting.NestingRules;
import pnnh.mcfloatingbackpack.inventory.ToolAreaContents;
import pnnh.mcfloatingbackpack.inventory.ToolAreaRules;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.item.TankItem;
import pnnh.mcfloatingbackpack.tank.TankPlacement;
import pnnh.mcfloatingbackpack.inventory.combined.AllAddressing;
import pnnh.mcfloatingbackpack.inventory.combined.AllContext;
import pnnh.mcfloatingbackpack.inventory.combined.AllRequestRules;
import pnnh.mcfloatingbackpack.inventory.combined.CursorLoan;
import pnnh.mcfloatingbackpack.inventory.combined.PanelTargets;

/**
 * {@code swapHand} is the old {@code shiftDown} bit (014): the click asks to swap the slot with the main-hand
 * item. The client now sets it from Alt, because Shift belongs to the panel selection. The wire format is
 * unchanged.
 */
public record BackpackClickPayload(
        byte area,
        int slotIndex,
        boolean swapHand,
        boolean rightClick,
        boolean screenOpen,
        boolean panelOpen,
        int sourceOrdinal,
        int invIndex,
        int epoch)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackClickPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "click"));

    // StreamCodec.composite tops out at 6 fields; this record has 7, so the boolean flags are
    // packed into a single byte instead of nesting another composite.
    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackClickPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.area());
                        buf.writeVarInt(payload.slotIndex());
                        int flags = (payload.swapHand() ? 1 : 0)
                                | (payload.rightClick() ? 2 : 0)
                                | (payload.screenOpen() ? 4 : 0)
                                | (payload.panelOpen() ? 8 : 0);
                        buf.writeByte(flags);
                        buf.writeVarInt(payload.sourceOrdinal());
                        buf.writeVarInt(payload.invIndex());
                        // 066: 0 for a single backpack's tab; for the "all" tab the epoch the client's flat cell numbers were made with
                        buf.writeVarInt(payload.epoch());
                    },
                    buf -> {
                        byte area = buf.readByte();
                        int slotIndex = buf.readVarInt();
                        int flags = buf.readByte();
                        int sourceOrdinal = buf.readVarInt();
                        int invIndex = buf.readVarInt();
                        int epoch = buf.readVarInt();
                        return new BackpackClickPayload(
                                area,
                                slotIndex,
                                (flags & 1) != 0,
                                (flags & 2) != 0,
                                (flags & 4) != 0,
                                (flags & 8) != 0,
                                sourceOrdinal,
                                invIndex,
                                epoch);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackClickPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            PanelArea area = PanelArea.fromByte(payload.area());
            // 066: on the "all" tab the slot number is a flat cell number of the whole grid
            boolean inAll = PanelTargets.inAll(serverPlayer);
            if (area == null
                    || !payload.panelOpen()
                    || payload.slotIndex() < 0
                    || payload.slotIndex() >= (inAll
                            ? AllAddressing.MAX_MEMBERS * BackpackInventory.MAX_MAIN_SLOTS
                            : area.isTool() ? ToolAreaContents.MAX_TOOL_SLOTS : BackpackInventory.MAX_MAIN_SLOTS)) {
                DiagLog.record(serverPlayer.getUUID(), "click ignored: bad request area=" + payload.area() + " slot=" + payload.slotIndex()
                        + " panelOpen=" + payload.panelOpen());
                return;
            }
            if (inAll) {
                handleAll(payload, serverPlayer, area);
                return;
            }
            if (!AllRequestRules.epochOk(false, 0, payload.epoch())) {
                DiagLog.record(serverPlayer.getUUID(), "click ignored: it was made for the all tab (epoch " + payload.epoch() + "), the panel is not on it");
                return;
            }

            BackpackRef claimed = decodeRef(payload.sourceOrdinal(), payload.invIndex());
            BackpackRef resolved = PanelTargets.resolve(serverPlayer);
            if (resolved == null || !resolved.stillValid(serverPlayer)) {
                DiagLog.record(serverPlayer.getUUID(), "click ignored: no backpack to resolve (claimed " + claimed + "); the panel is told to close");
                BackpackNetwork.sendSession(serverPlayer, false, null);
                return;
            }
            // 065: in a combined panel the click was meant for the tab it was drawn on. The one it names that is not the session's target
            // (the tab was switched, or the backpack moved, after the client sent it) is refused and the client shown the current panel,
            // instead of quietly changing another backpack.
            if (pnnh.mcfloatingbackpack.inventory.combined.CombinedSessions.has(serverPlayer.getUUID())
                    && claimed != null
                    && !claimed.equals(resolved)) {
                DiagLog.record(serverPlayer.getUUID(), "click refused: it names " + claimed + ", the combined panel is on " + resolved + "; the panel is re-synced");
                BackpackNetwork.sendSession(serverPlayer, true, resolved);
                return;
            }
            // Prefer live resolve; claimed must still be a backpack at that location if used.
            BackpackRef ref = resolved;
            if (claimed != null
                    && claimed.stillValid(serverPlayer)
                    && claimed.source() == resolved.source()
                    && claimed.invIndex() == resolved.invIndex()) {
                ref = claimed;
            }

            // 026: every request into a backpack goes through the registry first.

            BackpackInventory inventory = BackpackGate.acquireForPanel(serverPlayer, ref).orElse(null);

            if (inventory == null) {
                DiagLog.record(serverPlayer.getUUID(), "click ignored: the backpack gate refused " + ref + "; the panel is told to close");
                BackpackNetwork.sendSession(serverPlayer, false, null);

                return;

            }
            if (payload.slotIndex() >= inventory.size(area)) {
                BackpackNetwork.sendSession(serverPlayer, false, null);
                return;
            }
            applyClick(serverPlayer, payload, ref, inventory, area, payload.slotIndex(), null);
        });
    }

    private static void settle(CursorLoan loan, BackpackInventory inventory) {
        if (loan != null) {
            loan.settle(inventory);
        }
    }

    /**
     * 066: a click on a cell of the "all" tab: the flat cell number is looked up to a member and the cell in it, and the click is carried out on
     * that member exactly as on a single backpack's tab, with the tab's in-memory cursor lent to it for the request.
     */
    private static void handleAll(BackpackClickPayload payload, ServerPlayer serverPlayer, PanelArea area) {
        if (area != PanelArea.MAIN) {
            DiagLog.record(serverPlayer.getUUID(), "click ignored: the all tab has only the main area (area " + area + ")");
            return;
        }
        AllContext context = AllContext.begin(serverPlayer, payload.epoch());
        if (context == null) {
            return;
        }
        AllAddressing.Cell cell = context.addressing().locate(payload.slotIndex());
        if (cell == null) {
            context.abandon();
            return;
        }
        BackpackInventory inventory = context.inventory(cell.member());
        if (inventory == null) {
            context.abandon();
            return;
        }
        BackpackRef ref = context.ref(cell.member());
        applyClick(serverPlayer, payload, ref, inventory, area, cell.local(), CursorLoan.borrow(context.all(), inventory));
    }

    /**
     * What a click does to the backpack {@code ref}, whose inventory has been acquired: the menu's carried stack, the nesting rule, the tool
     * area and category rules, the click itself, saving and the answer. {@code loan} is the "all" tab's in-memory cursor lent to the
     * inventory (null on a single backpack's tab); it is taken back before each save so the backpack never holds it.
     */
    private static void applyClick(
            ServerPlayer serverPlayer, BackpackClickPayload payload, BackpackRef ref, BackpackInventory inventory, PanelArea area, int slot, CursorLoan loan) {
        AbstractContainerMenu menu = serverPlayer.containerMenu;
        boolean viaMenu = payload.screenOpen() && menu != null;
        int handSwapDest = -1;
        DiagLog.record(serverPlayer.getUUID(), "click " + area + "#" + slot + (payload.rightClick() ? " right" : " left")
                + (payload.swapHand() ? " swapHand" : "") + (viaMenu ? " viaMenu(" + menu.getClass().getSimpleName() + ")" : "")
                + " ref=" + ref + " | " + state(serverPlayer, menu, inventory, area, slot));
        if (viaMenu && !ServerCompatDenials.allows(serverPlayer, menu, Action.USE_MENU_CURSOR)) {
            return;
        }

        // 064: a floating backpack (or an item with one inside) goes into no area of a floating backpack, except back into the one
        // it was just taken out of (an older version's nesting is kept, never added to). A refused click changes nothing.
        ItemStack nestingHeld = viaMenu
                ? (menu.getCarried().isEmpty() ? inventory.getCursor() : menu.getCarried())
                : payload.swapHand() ? serverPlayer.getMainHandItem() : inventory.getCursor();
        boolean nestingHeldRisky = false;
        if (!nestingHeld.isEmpty()) {
            NestingRules.Verdict nesting = BackpackNesting.refusal(serverPlayer, ref, nestingHeld);
            if (nesting != NestingRules.Verdict.OK) {
                BackpackNesting.tell(serverPlayer, nesting);
                BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                return;
            }
            nestingHeldRisky = BackpackNesting.isRisky(nestingHeld);
        }
        ItemStack nestingSlotBefore = inventory.getStackInSlot(area, slot).copy();

        // 016: what goes into a tool area is the server's call. Since 021 the right area takes one trash can and the
        // bottom area one tag manager and (024) the left area one auto pickup (ToolAreaRules); a refused click changes nothing.
        if (area.isTool()) {
            ItemStack held = viaMenu
                    ? (menu.getCarried().isEmpty() ? inventory.getCursor() : menu.getCarried())
                    : payload.swapHand() ? serverPlayer.getMainHandItem() : inventory.getCursor();
            // 035: a pack from the panel's own cursor is already counted among the placed packs; one from the hand or the menu is not.
            boolean heldIsCursor = viaMenu ? menu.getCarried().isEmpty() : !payload.swapHand();
            if (!held.isEmpty()
                    && !ExtensionPackRules.canPlace(inventory, held, heldIsCursor)) {
                // 036: say why nothing happened (the backpack already has the most rows).
                serverPlayer.displayClientMessage(Component.translatable("message.mcfloatingbackpack.limit.extension_rows"), true);
                BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                return;
            }
            if (!held.isEmpty()
                    && !ToolAreaRules.canPlace(
                            area,
                            held,
                            slot,
                            inventory.toolStacks(area),
                            stack -> stack.is(ModItems.TRASH_CAN.get()),
                            stack -> stack.is(ModItems.TAG_MANAGER.get()),
                            stack -> stack.is(ModItems.AUTO_PICKUP.get()))) {
                BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                return;
            }
            // 048: a liquid tank goes only into a backpack of its tier or higher, and counts only in the bottom area, one for each liquid
            // that fits what is stored; the player is told why when it is refused.
            if (!held.isEmpty()) {
                TankPlacement.Verdict verdict = TankItem.check(inventory, area, held, slot);
                if (verdict != TankPlacement.Verdict.OK) {
                    serverPlayer.displayClientMessage(TankItem.refusal(verdict, held), true);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                    return;
                }
                // 052: a light (lantern and the like) counts in the bottom area, one only, whatever its kind
                if (pnnh.mcfloatingbackpack.light.ToolLightRules.check(
                                area,
                                held,
                                slot,
                                inventory.toolStacks(area),
                                pnnh.mcfloatingbackpack.light.LightTags::isLight)
                        != pnnh.mcfloatingbackpack.light.ToolLightRules.Verdict.OK) {
                    serverPlayer.displayClientMessage(Component.translatable("message.mcfloatingbackpack.light.duplicate"), true);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                    return;
                }
                // 054: so does the mobile crafting table
                if (pnnh.mcfloatingbackpack.inventory.ToolSingleRules.check(
                                PanelArea.RIGHT,
                                area,
                                held,
                                slot,
                                inventory.toolStacks(area),
                                stack -> stack.is(ModItems.MOBILE_CRAFTING_TABLE.get()))
                        != pnnh.mcfloatingbackpack.inventory.ToolSingleRules.Verdict.OK) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.mcfloatingbackpack.limit.mobile_crafting_table"), true);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                    return;
                }
                // 055: and the mobile furnace
                if (pnnh.mcfloatingbackpack.inventory.ToolSingleRules.check(
                                PanelArea.RIGHT,
                                area,
                                held,
                                slot,
                                inventory.toolStacks(area),
                                stack -> stack.is(ModItems.MOBILE_FURNACE.get()))
                        != pnnh.mcfloatingbackpack.inventory.ToolSingleRules.Verdict.OK) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.mcfloatingbackpack.limit.mobile_furnace"), true);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                    return;
                }
                // 053: the mobile jukebox counts in the right area, one only
                if (pnnh.mcfloatingbackpack.inventory.ToolSingleRules.check(
                                PanelArea.RIGHT,
                                area,
                                held,
                                slot,
                                inventory.toolStacks(area),
                                stack -> stack.is(ModItems.MOBILE_JUKEBOX.get()))
                        != pnnh.mcfloatingbackpack.inventory.ToolSingleRules.Verdict.OK) {
                    serverPlayer.displayClientMessage(Component.translatable("message.mcfloatingbackpack.limit.mobile_jukebox"), true);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                    return;
                }
                // 068: the bottom area takes one quick access, and only the backpack's own tier
                pnnh.mcfloatingbackpack.inventory.quick.QuickAccessPlace.Verdict quick =
                        pnnh.mcfloatingbackpack.item.QuickAccessItem.placement(
                                area == PanelArea.BOTTOM, inventory.tier(), held, slot, inventory.toolStacks(area));
                if (quick != pnnh.mcfloatingbackpack.inventory.quick.QuickAccessPlace.Verdict.OK) {
                    String key = quick == pnnh.mcfloatingbackpack.inventory.quick.QuickAccessPlace.Verdict.WRONG_TIER
                            ? "message.mcfloatingbackpack.quick.wrong_tier"
                            : "message.mcfloatingbackpack.limit.quick_access";
                    serverPlayer.displayClientMessage(Component.translatable(key), true);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                    return;
                }
            }
        }

        // 035: a cell of a category extension row only takes what fits its category (and a hidden row's or a missing cell takes
        // nothing); taking out is always allowed. A refused click changes nothing. 067: a whole-stack hand swap is not
        // refused here. It puts the hand stack in the clicked cell when that cell accepts it, otherwise in the first
        // empty cell that does, and refuses only when a non-empty hand has nowhere to go.
        if (!area.isTool() && !payload.swapHand()) {
            ItemStack held = viaMenu
                    ? (menu.getCarried().isEmpty() ? inventory.getCursor() : menu.getCarried())
                    : inventory.getCursor();
            if (!held.isEmpty() && !inventory.canPlaceMain(slot, held)) {
                BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                return;
            }
        }

        if (viaMenu) {
            BackpackMenuBridge.clickWithMenuCarried(
                    inventory,
                    menu,
                    index -> inventory.getStackInSlot(area, index),
                    (stack, index) -> inventory.setStackInSlot(area, index, stack),
                    slot,
                    payload.rightClick());
            menu.broadcastChanges();
        } else if (payload.swapHand()) {
            // Do not swap the backpack item itself out of the main hand into a slot.
            ItemStack hand = serverPlayer.getMainHandItem();
            if (FloatingBackpackItem.isBackpack(hand)) {
                settle(loan, inventory);
                ref.saveInventory(serverPlayer, inventory);
                BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                return;
            }
            int dest = slot;
            if (!area.isTool()) {
                dest = pnnh.mcfloatingbackpack.inventory.quick.HandSwapSlots.destination(inventory, slot, hand);
                if (!hand.isEmpty() && dest < 0) {
                    serverPlayer.displayClientMessage(
                            Component.translatable("message.mcfloatingbackpack.quick.no_room"), true);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
                    return;
                }
            }
            ItemStack taken = inventory.getStackInSlot(area, slot).copy();
            if (hand.isEmpty() || dest == slot) {
                inventory.setStackInSlot(area, slot, hand.copy());
            } else {
                inventory.setStackInSlot(area, slot, ItemStack.EMPTY);
                inventory.setStackInSlot(area, dest, hand.copy());
                handSwapDest = dest;
            }
            serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, taken);
            serverPlayer.getInventory().setChanged();
        } else {
            BackpackClickLogic.applyTo(inventory, area, slot, payload.rightClick());
        }

        // 064: the record of what was taken out is used up by putting it back, and made when a nested item is picked up.
        if (nestingHeldRisky) {
            BackpackNesting.forget(serverPlayer.getUUID());
        }
        if (!nestingSlotBefore.isEmpty()
                && !ItemStack.matches(nestingSlotBefore, inventory.getStackInSlot(area, slot))) {
            BackpackNesting.rememberTakenOut(serverPlayer, ref, nestingSlotBefore);
        }

        settle(loan, inventory);
        ref.saveInventory(serverPlayer, inventory);
        DiagLog.record(serverPlayer.getUUID(), "  -> saved | " + state(serverPlayer, menu, inventory, area, slot));
        BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, slot);
        if (handSwapDest >= 0) {
            BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, handSwapDest);
        }
    }

    /** The things a click moves, in a few words: the slot, the panel's cursor, the menu's carried stack and the hand. */
    private static String state(
            ServerPlayer player, AbstractContainerMenu menu, BackpackInventory inventory, PanelArea area, int slot) {
        return "slot=" + DiagLog.stack(inventory.getStackInSlot(area, slot)) + " panelCursor=" + DiagLog.stack(inventory.getCursor())
                + " carried=" + DiagLog.stack(menu == null ? net.minecraft.world.item.ItemStack.EMPTY : menu.getCarried())
                + " hand=" + DiagLog.stack(player.getMainHandItem());
    }

    private static BackpackRef decodeRef(int sourceOrdinal, int invIndex) {
        BackpackRef.Source[] sources = BackpackRef.Source.values();
        if (sourceOrdinal < 0 || sourceOrdinal >= sources.length) {
            return null;
        }
        return new BackpackRef(sources[sourceOrdinal], invIndex);
    }
}
