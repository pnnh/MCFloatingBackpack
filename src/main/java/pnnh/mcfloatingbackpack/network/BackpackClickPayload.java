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
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate;
import pnnh.mcfloatingbackpack.inventory.BackpackClickLogic;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionPackRules;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.ToolAreaContents;
import pnnh.mcfloatingbackpack.inventory.ToolAreaRules;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.item.TankItem;
import pnnh.mcfloatingbackpack.tank.TankPlacement;

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
        int invIndex)
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
                    },
                    buf -> {
                        byte area = buf.readByte();
                        int slotIndex = buf.readVarInt();
                        int flags = buf.readByte();
                        int sourceOrdinal = buf.readVarInt();
                        int invIndex = buf.readVarInt();
                        return new BackpackClickPayload(
                                area,
                                slotIndex,
                                (flags & 1) != 0,
                                (flags & 2) != 0,
                                (flags & 4) != 0,
                                (flags & 8) != 0,
                                sourceOrdinal,
                                invIndex);
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
            if (area == null
                    || !payload.panelOpen()
                    || payload.slotIndex() < 0
                    || payload.slotIndex() >= (area.isTool() ? ToolAreaContents.MAX_TOOL_SLOTS : BackpackInventory.MAX_MAIN_SLOTS)) {
                DiagLog.record(serverPlayer.getUUID(), "click ignored: bad request area=" + payload.area() + " slot=" + payload.slotIndex()
                        + " panelOpen=" + payload.panelOpen());
                return;
            }

            BackpackRef claimed = decodeRef(payload.sourceOrdinal(), payload.invIndex());
            BackpackRef resolved = BackpackRef.resolve(serverPlayer);
            if (resolved == null || !resolved.stillValid(serverPlayer)) {
                DiagLog.record(serverPlayer.getUUID(), "click ignored: no backpack to resolve (claimed " + claimed + "); the panel is told to close");
                BackpackNetwork.sendSession(serverPlayer, false, null);
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
            AbstractContainerMenu menu = serverPlayer.containerMenu;
            boolean viaMenu = payload.screenOpen() && menu != null;
            DiagLog.record(serverPlayer.getUUID(), "click " + area + "#" + payload.slotIndex() + (payload.rightClick() ? " right" : " left")
                    + (payload.swapHand() ? " swapHand" : "") + (viaMenu ? " viaMenu(" + menu.getClass().getSimpleName() + ")" : "")
                    + " ref=" + ref + " | " + state(serverPlayer, menu, inventory, area, payload.slotIndex()));
            if (viaMenu && !ServerCompatGate.allows(Action.USE_MENU_CURSOR, MenuOwner.ownerOf(menu))) {
                return;
            }

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
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                    return;
                }
                if (!held.isEmpty()
                        && !ToolAreaRules.canPlace(
                                area,
                                held,
                                payload.slotIndex(),
                                inventory.toolStacks(area),
                                stack -> stack.is(ModItems.TRASH_CAN.get()),
                                stack -> stack.is(ModItems.TAG_MANAGER.get()),
                                stack -> stack.is(ModItems.AUTO_PICKUP.get()))) {
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                    return;
                }
                // 048: a liquid tank goes only into a backpack of its tier or higher, and counts only in the bottom area, one for each liquid
                // that fits what is stored; the player is told why when it is refused.
                if (!held.isEmpty()) {
                    TankPlacement.Verdict verdict = TankItem.check(inventory, area, held, payload.slotIndex());
                    if (verdict != TankPlacement.Verdict.OK) {
                        serverPlayer.displayClientMessage(TankItem.refusal(verdict, held), true);
                        BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                        return;
                    }
                    // 052: a light (lantern and the like) counts in the bottom area, one only, whatever its kind
                    if (pnnh.mcfloatingbackpack.light.ToolLightRules.check(
                                    area,
                                    held,
                                    payload.slotIndex(),
                                    inventory.toolStacks(area),
                                    pnnh.mcfloatingbackpack.light.LightTags::isLight)
                            != pnnh.mcfloatingbackpack.light.ToolLightRules.Verdict.OK) {
                        serverPlayer.displayClientMessage(Component.translatable("message.mcfloatingbackpack.light.duplicate"), true);
                        BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                        return;
                    }
                    // 054: so does the mobile crafting table
                    if (pnnh.mcfloatingbackpack.inventory.ToolSingleRules.check(
                                    PanelArea.RIGHT,
                                    area,
                                    held,
                                    payload.slotIndex(),
                                    inventory.toolStacks(area),
                                    stack -> stack.is(ModItems.MOBILE_CRAFTING_TABLE.get()))
                            != pnnh.mcfloatingbackpack.inventory.ToolSingleRules.Verdict.OK) {
                        serverPlayer.displayClientMessage(
                                Component.translatable("message.mcfloatingbackpack.limit.mobile_crafting_table"), true);
                        BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                        return;
                    }
                    // 053: the mobile jukebox counts in the right area, one only
                    if (pnnh.mcfloatingbackpack.inventory.ToolSingleRules.check(
                                    PanelArea.RIGHT,
                                    area,
                                    held,
                                    payload.slotIndex(),
                                    inventory.toolStacks(area),
                                    stack -> stack.is(ModItems.MOBILE_JUKEBOX.get()))
                            != pnnh.mcfloatingbackpack.inventory.ToolSingleRules.Verdict.OK) {
                        serverPlayer.displayClientMessage(Component.translatable("message.mcfloatingbackpack.limit.mobile_jukebox"), true);
                        BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                        return;
                    }
                }
            }

            // 035: a cell of a category extension row only takes what fits its category (and a hidden row's or a missing cell takes
            // nothing); taking out is always allowed. A refused click changes nothing.
            if (!area.isTool()) {
                ItemStack held = viaMenu
                        ? (menu.getCarried().isEmpty() ? inventory.getCursor() : menu.getCarried())
                        : payload.swapHand() ? serverPlayer.getMainHandItem() : inventory.getCursor();
                if (!held.isEmpty() && !inventory.canPlaceMain(payload.slotIndex(), held)) {
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                    return;
                }
            }

            if (viaMenu) {
                BackpackMenuBridge.clickWithMenuCarried(
                        inventory,
                        menu,
                        index -> inventory.getStackInSlot(area, index),
                        (stack, index) -> inventory.setStackInSlot(area, index, stack),
                        payload.slotIndex(),
                        payload.rightClick());
                menu.broadcastChanges();
            } else if (payload.swapHand()) {
                // Do not swap the backpack item itself out of the main hand into a slot.
                ItemStack hand = serverPlayer.getMainHandItem();
                if (FloatingBackpackItem.isBackpack(hand)) {
                    ref.saveInventory(serverPlayer, inventory);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
                    return;
                }
                ItemStack handCopy = hand.copy();
                ItemStack slot = inventory.getStackInSlot(area, payload.slotIndex()).copy();
                inventory.setStackInSlot(area, payload.slotIndex(), handCopy);
                serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, slot);
                serverPlayer.getInventory().setChanged();
            } else {
                BackpackClickLogic.applyTo(inventory, area, payload.slotIndex(), payload.rightClick());
            }

            ref.saveInventory(serverPlayer, inventory);
            DiagLog.record(serverPlayer.getUUID(), "  -> saved | " + state(serverPlayer, menu, inventory, area, payload.slotIndex()));
            BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
        });
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
