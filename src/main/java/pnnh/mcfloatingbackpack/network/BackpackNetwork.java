package pnnh.mcfloatingbackpack.network;

import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.creative.CreativeCursorBridge;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotActionPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotListPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotListRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotOpenPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotPreviewPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotPreviewRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotResultPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotVersionsPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotVersionsRequestPayload;

public final class BackpackNetwork {
    private BackpackNetwork() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(BackpackNetwork::onRegisterPayloads);
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // 039: the version check's two payloads are optional and registered apart from everything below (a registrar is a copy that
        // keeps its own version and flag), so the protocol number 17 and the checks of the other payloads are not touched. A client
        // that does not have them can still log in. The version "1" must never change: a client and a server that both have the
        // payload and differ in its version cannot connect; if it ever has to change, add another payload instead.
        PayloadRegistrar versionCheck = event.registrar(MCFloatingBackpack.MOD_ID).versioned("1").optional();
        versionCheck.playToClient(VersionHelloPayload.TYPE, VersionHelloPayload.STREAM_CODEC, VersionHelloPayload::handleClient);
        versionCheck.playToServer(VersionReportPayload.TYPE, VersionReportPayload.STREAM_CODEC, VersionReportPayload::handleServer);

        PayloadRegistrar registrar = event.registrar(MCFloatingBackpack.MOD_ID).versioned("20");
        // 027: the creative inventory's cursor, which only the client has, and the server's answer to a panel operation.
        registrar.playToServer(
                CreativeCursorReportPayload.TYPE,
                CreativeCursorReportPayload.STREAM_CODEC,
                CreativeCursorReportPayload::handleServer);
        registrar.playToClient(
                CreativeCursorPushPayload.TYPE,
                CreativeCursorPushPayload.STREAM_CODEC,
                CreativeCursorPushPayload::handleClient);
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
        registrar.playToClient(
                BackpackTagsPayload.TYPE,
                BackpackTagsPayload.STREAM_CODEC,
                BackpackTagsPayload::handleClient);
        registrar.playToClient(
                BackpackTrashContentsPayload.TYPE,
                BackpackTrashContentsPayload.STREAM_CODEC,
                BackpackTrashContentsPayload::handleClient);
        registrar.playToClient(
                BackpackPickupBlacklistPayload.TYPE,
                BackpackPickupBlacklistPayload.STREAM_CODEC,
                BackpackPickupBlacklistPayload::handleClient);
        // 048: what the backpack holds of each liquid
        registrar.playToClient(
                BackpackFluidsPayload.TYPE,
                BackpackFluidsPayload.STREAM_CODEC,
                BackpackFluidsPayload::handleClient);
        registrar.playToServer(
                BackpackClickPayload.TYPE,
                BackpackClickPayload.STREAM_CODEC,
                BackpackClickPayload::handleServer);
        registrar.playToServer(
                MergePanelCursorPayload.TYPE,
                MergePanelCursorPayload.STREAM_CODEC,
                MergePanelCursorPayload::handleServer);
        // 054: which mobile tool the player clicked; the server opens its vanilla menu
        registrar.playToServer(OpenToolPayload.TYPE, OpenToolPayload.STREAM_CODEC, OpenToolPayload::handleServer);
        // 047: tidy the main area
        registrar.playToServer(BackpackSortPayload.TYPE, BackpackSortPayload.STREAM_CODEC, BackpackSortPayload::handleServer);
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
                BackpackTagPayload.TYPE,
                BackpackTagPayload.STREAM_CODEC,
                BackpackTagPayload::handleServer);
        registrar.playToServer(
                BackpackTagOverviewPayload.TYPE,
                BackpackTagOverviewPayload.STREAM_CODEC,
                BackpackTagOverviewPayload::handleServer);
        registrar.playToServer(
                BackpackTrashPayload.TYPE,
                BackpackTrashPayload.STREAM_CODEC,
                BackpackTrashPayload::handleServer);
        registrar.playToServer(
                BackpackTrashSendPayload.TYPE,
                BackpackTrashSendPayload.STREAM_CODEC,
                BackpackTrashSendPayload::handleServer);
        registrar.playToServer(
                BackpackPickupBlacklistOpPayload.TYPE,
                BackpackPickupBlacklistOpPayload.STREAM_CODEC,
                BackpackPickupBlacklistOpPayload::handleServer);
        registrar.playToServer(
                BackpackOpenRequestPayload.TYPE,
                BackpackOpenRequestPayload.STREAM_CODEC,
                BackpackOpenRequestPayload::handleServer);
        // 031: the snapshot window (protocol 16).
        registrar.playToServer(
                SnapshotListRequestPayload.TYPE, SnapshotListRequestPayload.STREAM_CODEC, SnapshotListRequestPayload::handleServer);
        registrar.playToServer(
                SnapshotVersionsRequestPayload.TYPE,
                SnapshotVersionsRequestPayload.STREAM_CODEC,
                SnapshotVersionsRequestPayload::handleServer);
        registrar.playToServer(
                SnapshotPreviewRequestPayload.TYPE,
                SnapshotPreviewRequestPayload.STREAM_CODEC,
                SnapshotPreviewRequestPayload::handleServer);
        registrar.playToServer(SnapshotActionPayload.TYPE, SnapshotActionPayload.STREAM_CODEC, SnapshotActionPayload::handleServer);
        registrar.playToClient(SnapshotListPayload.TYPE, SnapshotListPayload.STREAM_CODEC, SnapshotListPayload::handleClient);
        registrar.playToClient(
                SnapshotVersionsPayload.TYPE, SnapshotVersionsPayload.STREAM_CODEC, SnapshotVersionsPayload::handleClient);
        registrar.playToClient(
                SnapshotPreviewPayload.TYPE, SnapshotPreviewPayload.STREAM_CODEC, SnapshotPreviewPayload::handleClient);
        registrar.playToClient(SnapshotResultPayload.TYPE, SnapshotResultPayload.STREAM_CODEC, SnapshotResultPayload::handleClient);
        registrar.playToClient(SnapshotOpenPayload.TYPE, SnapshotOpenPayload.STREAM_CODEC, SnapshotOpenPayload::handleClient);
    }

    /**
     * 035: the most bytes a session or full sync may take. The same bound the snapshot preview keeps (a packet from the server
     * may not pass 1 MiB), so one very full backpack cannot make the client drop the connection.
     */
    public static final int MAX_SYNC_BYTES = SnapshotRequestRules.MAX_PREVIEW_BYTES;

    /** 035: whether a payload encodes to no more than {@link #MAX_SYNC_BYTES}; logs when it does not. */
    private static <P> boolean withinLimit(ServerPlayer player, StreamCodec<RegistryFriendlyByteBuf, P> codec, P payload, String what) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.level().registryAccess());
        try {
            codec.encode(buf, payload);
            int size = buf.writerIndex();
            if (size > MAX_SYNC_BYTES) {
                MCFloatingBackpack.LOGGER.warn(
                        "Not sending the {} of {}: {} bytes is over the limit of {}", what, player.getName().getString(), size, MAX_SYNC_BYTES);
                return false;
            }
            return true;
        } catch (RuntimeException e) {
            MCFloatingBackpack.LOGGER.warn("Not sending the {} of {}: it could not be encoded", what, player.getName().getString(), e);
            return false;
        } finally {
            buf.release();
        }
    }

    /** 035: a full sync for a player that may be a client-side one (nothing happens there). */
    public static void sendFullSyncIfServer(Player player, BackpackRef ref) {
        if (player instanceof ServerPlayer serverPlayer) {
            sendFullSync(serverPlayer, ref);
        }
    }

    public static void sendSession(ServerPlayer player, boolean open, BackpackRef ref) {
        if (!open || ref == null || !ref.stillValid(player)) {
            // A stale view was just re-synced (see BackpackGate.acquireForPanel): the panel shows the new backpack, do not close it.
            if (BackpackGate.justResynced(player)) {
                return;
            }
            BackpackGate.noteClosed(player.getUUID());
            PacketDistributor.sendToPlayer(player, BackpackSessionPayload.closed());
            return;
        }
        BackpackInventory inventory = ref.loadInventory(player);
        BackpackSessionPayload session = BackpackSessionPayload.open(ref, inventory);
        if (!withinLimit(player, BackpackSessionPayload.STREAM_CODEC, session, "backpack session")) {
            BackpackGate.noteClosed(player.getUUID());
            PacketDistributor.sendToPlayer(player, BackpackSessionPayload.closed());
            return;
        }
        PacketDistributor.sendToPlayer(player, session);
        BackpackGate.noteOpened(player, ref.getStack(player));
        // 018: the tags travel in their own packet (the session payload has no room for another field).
        PacketDistributor.sendToPlayer(player, new BackpackTagsPayload(inventory.tags()));
        // 021: and so does the trash can's content.
        sendTrash(player, inventory);
        // 024: and the pickup blacklist.
        sendPickupBlacklist(player, inventory);
        // 048: and the liquids.
        sendFluids(player, inventory);
    }

    /** 048: what the backpack holds of each liquid, as the server has it. */
    public static void sendFluids(ServerPlayer player, BackpackInventory inventory) {
        PacketDistributor.sendToPlayer(player, new BackpackFluidsPayload(inventory.fluids()));
    }

    /** 024: the pickup blacklist, as the server has it. */
    public static void sendPickupBlacklist(ServerPlayer player, BackpackInventory inventory) {
        PacketDistributor.sendToPlayer(player, new BackpackPickupBlacklistPayload(inventory.pickupBlacklist()));
    }

    /** 021: what the trash can holds, as the server has it. */
    public static void sendTrash(ServerPlayer player, BackpackInventory inventory) {
        PacketDistributor.sendToPlayer(player, new BackpackTrashContentsPayload(inventory.trash()));
        CreativeCursorBridge.pushIfChanged(player);
    }

    public static void sendFullSync(ServerPlayer player, BackpackRef ref) {
        // 027: whatever the panel operation did to the creative cursor goes back with its result.
        CreativeCursorBridge.pushIfChanged(player);
        if (ref == null || !ref.stillValid(player)) {
            return;
        }
        BackpackInventory inventory = ref.loadInventory(player);
        BackpackFullSyncPayload sync = BackpackFullSyncPayload.from(ref, inventory);
        if (!withinLimit(player, BackpackFullSyncPayload.STREAM_CODEC, sync, "backpack sync")) {
            PacketDistributor.sendToPlayer(player, BackpackSessionPayload.closed());
            return;
        }
        PacketDistributor.sendToPlayer(player, sync);
    }

    public static void sendSlotUpdate(ServerPlayer player, BackpackRef ref, int slotIndex) {
        sendSlotUpdate(player, ref, PanelArea.MAIN, slotIndex);
    }

    public static void sendSlotUpdate(ServerPlayer player, BackpackRef ref, PanelArea area, int slotIndex) {
        // 027: whatever the panel operation did to the creative cursor goes back with its result.
        CreativeCursorBridge.pushIfChanged(player);
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
