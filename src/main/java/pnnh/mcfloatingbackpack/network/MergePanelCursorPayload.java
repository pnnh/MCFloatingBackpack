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
import pnnh.mcfloatingbackpack.inventory.BackpackAttachments;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;

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
            if (menu == null) {
                return;
            }
            BackpackInventory inventory = serverPlayer.getData(BackpackAttachments.BACKPACK.get());
            BackpackMenuBridge.mergePanelCursorIntoCarried(inventory, menu);
            menu.broadcastChanges();
            BackpackNetwork.sendSlotUpdate(serverPlayer, -1);
        });
    }
}
