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
import pnnh.mcfloatingbackpack.inventory.BackpackClickLogic;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

public record BackpackClickPayload(
        int slotIndex,
        boolean shiftDown,
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
                        buf.writeVarInt(payload.slotIndex());
                        int flags = (payload.shiftDown() ? 1 : 0)
                                | (payload.rightClick() ? 2 : 0)
                                | (payload.screenOpen() ? 4 : 0)
                                | (payload.panelOpen() ? 8 : 0);
                        buf.writeByte(flags);
                        buf.writeVarInt(payload.sourceOrdinal());
                        buf.writeVarInt(payload.invIndex());
                    },
                    buf -> {
                        int slotIndex = buf.readVarInt();
                        int flags = buf.readByte();
                        int sourceOrdinal = buf.readVarInt();
                        int invIndex = buf.readVarInt();
                        return new BackpackClickPayload(
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
            if (!payload.panelOpen()
                    || payload.slotIndex() < 0
                    || payload.slotIndex() >= BackpackInventory.MAX_SIZE) {
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
            if (payload.slotIndex() >= inventory.size()) {
                BackpackNetwork.sendSession(serverPlayer, false, null);
                return;
            }
            AbstractContainerMenu menu = serverPlayer.containerMenu;

            if (payload.screenOpen() && menu != null) {
                BackpackMenuBridge.clickWithMenuCarried(
                        inventory, menu, payload.slotIndex(), payload.rightClick());
                menu.broadcastChanges();
            } else if (payload.shiftDown()) {
                // Do not swap the backpack item itself out of the main hand into a slot.
                ItemStack hand = serverPlayer.getMainHandItem();
                if (FloatingBackpackItem.isBackpack(hand)) {
                    ref.saveInventory(serverPlayer, inventory);
                    BackpackNetwork.sendSlotUpdate(serverPlayer, ref, payload.slotIndex());
                    return;
                }
                ItemStack handCopy = hand.copy();
                ItemStack slot = inventory.getStackInSlot(payload.slotIndex()).copy();
                inventory.setStackInSlot(payload.slotIndex(), handCopy);
                serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, slot);
                serverPlayer.getInventory().setChanged();
            } else {
                BackpackClickLogic.applyTo(inventory, payload.slotIndex(), payload.rightClick());
            }

            ref.saveInventory(serverPlayer, inventory);
            BackpackNetwork.sendSlotUpdate(serverPlayer, ref, payload.slotIndex());
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
