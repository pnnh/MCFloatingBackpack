package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;

/**
 * Client notifies that a container screen opened; server merges leftover panel cursor
 * into the menu carried stack when possible.
 */
public record MergePanelCursorPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<MergePanelCursorPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "merge_cursor"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MergePanelCursorPayload> STREAM_CODEC =
            StreamCodec.unit(new MergePanelCursorPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(MergePanelCursorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            if (!(player instanceof ServerPlayer serverPlayer)) {
                return;
            }
            AbstractContainerMenu menu = serverPlayer.containerMenu;
            if (menu == null || !ServerCompatGate.allows(Action.MERGE_CURSOR, MenuOwner.ownerOf(menu))) {
                return;
            }
            BackpackRef ref = BackpackRef.resolve(serverPlayer);
            if (ref == null || !ref.stillValid(serverPlayer)) {
                return;
            }
            // 027: opening any container screen sends this. With nothing on the panel's own cursor there is nothing to merge,
            // so do not go through the registry (which could refuse and tell the player off for merely opening the
            // inventory) and do not save (which would count the backpack's revision up and make its other copies stale).
            BackpackContents stored = ref.getStack(serverPlayer).get(ModDataComponents.BACKPACK_CONTENTS.get());
            if (stored == null || stored.cursor().isEmpty()) {
                return;
            }
            // 026: every request into a backpack goes through the registry first.
            BackpackInventory inventory = BackpackGate.acquire(serverPlayer, ref).orElse(null);
            if (inventory == null) {
                BackpackNetwork.sendSession(serverPlayer, false, null);
                return;
            }
            DiagLog.record(serverPlayer.getUUID(), "merge panel cursor: panelCursor=" + DiagLog.stack(inventory.getCursor())
                    + " carried=" + DiagLog.stack(menu.getCarried()) + " menu=" + menu.getClass().getSimpleName());
            BackpackMenuBridge.mergePanelCursorIntoCarried(inventory, menu);
            ref.saveInventory(serverPlayer, inventory);
            menu.broadcastChanges();
            BackpackNetwork.sendSlotUpdate(serverPlayer, ref, -1);
        });
    }
}
