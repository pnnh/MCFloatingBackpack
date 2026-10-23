package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackAttachments;
import pnnh.mcfloatingbackpack.inventory.BackpackClickLogic;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;

public record BackpackClickPayload(int slotIndex, boolean shiftDown, boolean screenOpen, boolean panelOpen)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackClickPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackClickPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    BackpackClickPayload::slotIndex,
                    ByteBufCodecs.BOOL,
                    BackpackClickPayload::shiftDown,
                    ByteBufCodecs.BOOL,
                    BackpackClickPayload::screenOpen,
                    ByteBufCodecs.BOOL,
                    BackpackClickPayload::panelOpen,
                    BackpackClickPayload::new);

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
                    || payload.slotIndex() >= BackpackInventory.SIZE) {
                return;
            }

            BackpackInventory inventory = serverPlayer.getData(BackpackAttachments.BACKPACK.get());

            if (payload.shiftDown() && !payload.screenOpen()) {
                ItemStack hand = serverPlayer.getMainHandItem().copy();
                ItemStack slot = inventory.getStackInSlot(payload.slotIndex()).copy();
                inventory.setStackInSlot(payload.slotIndex(), hand);
                serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, slot);
                serverPlayer.getInventory().setChanged();
            } else {
                BackpackClickLogic.applyTo(inventory, payload.slotIndex());
            }

            BackpackNetwork.sendSlotUpdate(serverPlayer, payload.slotIndex());
        });
    }
}
