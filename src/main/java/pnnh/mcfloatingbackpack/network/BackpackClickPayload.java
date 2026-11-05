package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
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
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.ToolAreaContents;
import pnnh.mcfloatingbackpack.inventory.ToolAreaRules;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

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
                    || payload.slotIndex() >= (area.isTool() ? ToolAreaContents.MAX_TOOL_SLOTS : BackpackInventory.MAX_SIZE)) {
                return;
            }

            BackpackRef claimed = decodeRef(payload.sourceOrdinal(), payload.invIndex());
            BackpackRef resolved = BackpackRef.resolve(serverPlayer);
            if (resolved == null || !resolved.stillValid(serverPlayer)) {
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

            BackpackInventory inventory = ref.loadInventory(serverPlayer);
            if (payload.slotIndex() >= inventory.size(area)) {
                BackpackNetwork.sendSession(serverPlayer, false, null);
                return;
            }
            AbstractContainerMenu menu = serverPlayer.containerMenu;
            boolean viaMenu = payload.screenOpen() && menu != null;
            if (viaMenu && !ServerCompatGate.allows(Action.USE_MENU_CURSOR, MenuOwner.ownerOf(menu))) {
                return;
            }

            // 016: what goes into a tool area is the server's call. Every item is accepted for now, but this is
            // where a later feature can refuse an item for a given area; a refused click changes nothing.
            if (area.isTool()) {
                ItemStack held = viaMenu
                        ? (menu.getCarried().isEmpty() ? inventory.getCursor() : menu.getCarried())
                        : payload.swapHand() ? serverPlayer.getMainHandItem() : inventory.getCursor();
                if (!held.isEmpty() && !ToolAreaRules.canPlace(area, held)) {
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
            BackpackNetwork.sendSlotUpdate(serverPlayer, ref, area, payload.slotIndex());
        });
    }

    private static BackpackRef decodeRef(int sourceOrdinal, int invIndex) {
        BackpackRef.Source[] sources = BackpackRef.Source.values();
        if (sourceOrdinal < 0 || sourceOrdinal >= sources.length) {
            return null;
        }
        return new BackpackRef(sources[sourceOrdinal], invIndex);
    }
}
