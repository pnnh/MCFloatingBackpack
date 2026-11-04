package pnnh.mcfloatingbackpack.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;

public final class BackpackNetwork {
    private BackpackNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BackpackNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MCFloatingBackpack.MOD_ID).versioned("7");
        registrar.playToClient(
                BackpackFullSyncPayload.TYPE,
                BackpackFullSyncPayload.STREAM_CODEC,
                BackpackFullSyncPayload::handleClient);
        registrar.playToClient(
                BackpackSlotUpdatePayload.TYPE,
                BackpackSlotUpdatePayload.STREAM_CODEC,
                BackpackSlotUpdatePayload::handleClient);
        registrar.playToClient(
                BackpackSessionPayload.TYPE,
                BackpackSessionPayload.STREAM_CODEC,
                BackpackSessionPayload::handleClient);
        registrar.playToServer(
                BackpackClickPayload.TYPE,
                BackpackClickPayload.STREAM_CODEC,
                BackpackClickPayload::handleServer);
        registrar.playToServer(
                MergePanelCursorPayload.TYPE,
                MergePanelCursorPayload.STREAM_CODEC,
                MergePanelCursorPayload::handleServer);
        registrar.playToServer(
                BackpackQuickTransferPayload.TYPE,
                BackpackQuickTransferPayload.STREAM_CODEC,
                BackpackQuickTransferPayload::handleServer);
        registrar.playToServer(
                BackpackTakeFromContainerPayload.TYPE,
                BackpackTakeFromContainerPayload.STREAM_CODEC,
                BackpackTakeFromContainerPayload::handleServer);
        registrar.playToServer(
                BackpackCursorGesturePayload.TYPE,
                BackpackCursorGesturePayload.STREAM_CODEC,
                BackpackCursorGesturePayload::handleServer);
        registrar.playToServer(
                BackpackOpenRequestPayload.TYPE,
                BackpackOpenRequestPayload.STREAM_CODEC,
                BackpackOpenRequestPayload::handleServer);
    }

    public static void sendSession(ServerPlayer player, boolean open, BackpackRef ref) {
        if (!open || ref == null || !ref.stillValid(player)) {
            PacketDistributor.sendToPlayer(player, BackpackSessionPayload.closed());
            return;
        }
        BackpackInventory inventory = ref.loadInventory(player);
        PacketDistributor.sendToPlayer(player, BackpackSessionPayload.open(ref, inventory));
    }

    public static void sendFullSync(ServerPlayer player, BackpackRef ref) {
        if (ref == null || !ref.stillValid(player)) {
            return;
        }
        BackpackInventory inventory = ref.loadInventory(player);
        PacketDistributor.sendToPlayer(player, BackpackFullSyncPayload.from(ref, inventory));
    }

    public static void sendSlotUpdate(ServerPlayer player, BackpackRef ref, int slotIndex) {
        sendSlotUpdate(player, ref, PanelArea.MAIN, slotIndex);
    }

    public static void sendSlotUpdate(ServerPlayer player, BackpackRef ref, PanelArea area, int slotIndex) {
        if (ref == null || !ref.stillValid(player)) {
            PacketDistributor.sendToPlayer(player, BackpackSessionPayload.closed());
            return;
        }
        BackpackInventory inventory = ref.loadInventory(player);
        ItemStack slotStack = slotIndex >= 0 && slotIndex < inventory.size(area)
                ? inventory.getStackInSlot(area, slotIndex).copy()
                : ItemStack.EMPTY;
        PacketDistributor.sendToPlayer(
                player,
                new BackpackSlotUpdatePayload(area.toByte(), slotIndex, slotStack, inventory.getCursor().copy()));
    }
}
