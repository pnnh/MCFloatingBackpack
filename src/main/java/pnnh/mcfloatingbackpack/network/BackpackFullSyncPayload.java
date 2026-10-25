package pnnh.mcfloatingbackpack.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;

public record BackpackFullSyncPayload(
        int sourceOrdinal, int invIndex, List<ItemStack> slots, ItemStack cursor)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackFullSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "full_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackFullSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    BackpackFullSyncPayload::sourceOrdinal,
                    ByteBufCodecs.VAR_INT,
                    BackpackFullSyncPayload::invIndex,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.SIZE)),
                    BackpackFullSyncPayload::slots,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackFullSyncPayload::cursor,
                    BackpackFullSyncPayload::new);

    public static BackpackFullSyncPayload from(BackpackRef ref, BackpackInventory inventory) {
        List<ItemStack> slots = new ArrayList<>(BackpackInventory.SIZE);
        for (int i = 0; i < BackpackInventory.SIZE; i++) {
            slots.add(inventory.getStackInSlot(i).copy());
        }
        return new BackpackFullSyncPayload(
                ref.source().ordinal(),
                ref.invIndex(),
                slots,
                inventory.getCursor().copy());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackFullSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientBackpackView.get().applyFull(payload.slots(), payload.cursor());
            BackpackRef.Source[] sources = BackpackRef.Source.values();
            int ord = payload.sourceOrdinal();
            BackpackRef.Source source =
                    ord >= 0 && ord < sources.length ? sources[ord] : BackpackRef.Source.MAIN_HAND;
            ClientBackpackView.get()
                    .setActiveRef(new BackpackRef(source, payload.invIndex()));
        });
    }
}
