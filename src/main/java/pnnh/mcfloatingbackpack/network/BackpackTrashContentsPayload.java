package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.inventory.trash.TrashContents;

/**
 * 021: server tells the client what the open backpack's trash can holds (after the session opens, after every change,
 * and after a request it ignored, so the display catches up with the truth). Kept apart from the session payload,
 * whose codec already has as many fields as a composite allows.
 */
public record BackpackTrashContentsPayload(TrashContents trash) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackTrashContentsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "trash_contents"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackTrashContentsPayload> STREAM_CODEC =
            StreamCodec.composite(TrashContents.STREAM_CODEC, BackpackTrashContentsPayload::trash, BackpackTrashContentsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackTrashContentsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBackpackView.get().setTrash(payload.trash()));
    }
}
