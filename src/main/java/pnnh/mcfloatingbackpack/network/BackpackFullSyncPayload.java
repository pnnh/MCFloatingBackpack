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
import pnnh.mcfloatingbackpack.inventory.ToolAreaContents;

public record BackpackFullSyncPayload(
        int sourceOrdinal, int invIndex, List<ItemStack> slots, ItemStack cursor, ToolAreaContents tools)
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
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.MAX_SIZE)),
                    BackpackFullSyncPayload::slots,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackFullSyncPayload::cursor,
                    ToolAreaContents.STREAM_CODEC,
                    BackpackFullSyncPayload::tools,
                    BackpackFullSyncPayload::new);

    public static BackpackFullSyncPayload from(BackpackRef ref, BackpackInventory inventory) {
        List<ItemStack> slots = new ArrayList<>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            slots.add(inventory.getStackInSlot(i).copy());
        }
        return new BackpackFullSyncPayload(
                ref.source().ordinal(),
                ref.invIndex(),
                slots,
                inventory.getCursor().copy(),
                inventory.toolContents());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackFullSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientBackpackView.get().applyFull(payload.slots(), payload.tools(), payload.cursor());
            BackpackRef.Source[] sources = BackpackRef.Source.values();
            int ord = payload.sourceOrdinal();
            BackpackRef.Source source =
                    ord >= 0 && ord < sources.length ? sources[ord] : BackpackRef.Source.MAIN_HAND;
            ClientBackpackView.get()
                    .setActiveRef(new BackpackRef(source, payload.invIndex()));
        });
    }
}
