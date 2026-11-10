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
import pnnh.mcfloatingbackpack.inventory.extension.RowView;

public record BackpackFullSyncPayload(
        int sourceOrdinal,
        int invIndex,
        List<ItemStack> slots,
        ItemStack cursor,
        ToolAreaContents tools,
        RowView rows)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackFullSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "full_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> SLOTS_CODEC =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.MAX_MAIN_SLOTS));

    /** Written by hand: with the rows (035) it has more fields than {@code StreamCodec.composite} takes. */
    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackFullSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.sourceOrdinal());
                ByteBufCodecs.VAR_INT.encode(buf, payload.invIndex());
                SLOTS_CODEC.encode(buf, payload.slots());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.cursor());
                ToolAreaContents.STREAM_CODEC.encode(buf, payload.tools());
                RowView.STREAM_CODEC.encode(buf, payload.rows());
            },
            buf -> {
                int sourceOrdinal = ByteBufCodecs.VAR_INT.decode(buf);
                int invIndex = ByteBufCodecs.VAR_INT.decode(buf);
                List<ItemStack> slots = SLOTS_CODEC.decode(buf);
                ItemStack cursor = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                ToolAreaContents tools = ToolAreaContents.STREAM_CODEC.decode(buf);
                RowView rows = RowView.STREAM_CODEC.decode(buf);
                return new BackpackFullSyncPayload(sourceOrdinal, invIndex, slots, cursor, tools, rows);
            });

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
                inventory.toolContents(),
                inventory.rowView());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackFullSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientBackpackView.get().applyFull(payload.slots(), payload.tools(), payload.cursor(), payload.rows());
            BackpackRef.Source[] sources = BackpackRef.Source.values();
            int ord = payload.sourceOrdinal();
            BackpackRef.Source source =
                    ord >= 0 && ord < sources.length ? sources[ord] : BackpackRef.Source.MAIN_HAND;
            ClientBackpackView.get()
                    .setActiveRef(new BackpackRef(source, payload.invIndex()));
        });
    }
}
