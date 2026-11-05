package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;

/**
 * 018: server tells the client the whole tag set of the open backpack (after the session opens and after every
 * change). Kept apart from the session payload, whose codec already has as many fields as a composite allows.
 */
public record BackpackTagsPayload(ItemTags tags) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackTagsPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "tags"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackTagsPayload> STREAM_CODEC =
            StreamCodec.composite(ItemTags.STREAM_CODEC, BackpackTagsPayload::tags, BackpackTagsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackTagsPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBackpackView.get().setTags(payload.tags()));
    }
}
