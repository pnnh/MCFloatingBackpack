package pnnh.mcfloatingbackpack.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackAttachments;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;

public final class BackpackNetwork {
    private BackpackNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BackpackNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MCFloatingBackpack.MOD_ID).versioned("1");
        registrar.playToClient(
                BackpackFullSyncPayload.TYPE,
                BackpackFullSyncPayload.STREAM_CODEC,
                BackpackFullSyncPayload::handleClient);
        registrar.playToClient(
                BackpackSlotUpdatePayload.TYPE,
                BackpackSlotUpdatePayload.STREAM_CODEC,
                BackpackSlotUpdatePayload::handleClient);
        registrar.playToServer(
                BackpackClickPayload.TYPE,
                BackpackClickPayload.STREAM_CODEC,
                BackpackClickPayload::handleServer);
        registrar.playToServer(
                MergePanelCursorPayload.TYPE,
                MergePanelCursorPayload.STREAM_CODEC,
                MergePanelCursorPayload::handleServer);
    }

    public static void sendFullSync(ServerPlayer player) {
        BackpackInventory inventory = player.getData(BackpackAttachments.BACKPACK.get());
        PacketDistributor.sendToPlayer(player, BackpackFullSyncPayload.from(inventory));
    }

    public static void sendSlotUpdate(ServerPlayer player, int slotIndex) {
        BackpackInventory inventory = player.getData(BackpackAttachments.BACKPACK.get());
        ItemStack slotStack = slotIndex >= 0 && slotIndex < BackpackInventory.SIZE
                ? inventory.getStackInSlot(slotIndex).copy()
                : ItemStack.EMPTY;
        PacketDistributor.sendToPlayer(
                player,
                new BackpackSlotUpdatePayload(slotIndex, slotStack, inventory.getCursor().copy()));
    }
}
