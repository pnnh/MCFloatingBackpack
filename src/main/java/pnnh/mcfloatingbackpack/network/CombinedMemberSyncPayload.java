package pnnh.mcfloatingbackpack.network;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedView;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.combined.AllAddressing;
import pnnh.mcfloatingbackpack.inventory.extension.RowView;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;

/**
 * 066: the main area of one member of the "all" tab, as the server has it: every cell, the rows (for the grey cells and /hidden) and the
 * item tags (for the grey cells), and the in-memory cursor. One packet for each member, none bigger than a backpack's own full sync. The
 * client has the tab ready once it holds {@code memberCount} of them for one epoch; a later one of the same epoch replaces that member's cells.
 */
public record CombinedMemberSyncPayload(
        int epoch, int memberCount, int memberIndex, List<ItemStack> slots, RowView rows, ItemTags tags, ItemStack cursor)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<CombinedMemberSyncPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "combined_member_sync"));

    private static final StreamCodec<RegistryFriendlyByteBuf, List<ItemStack>> SLOTS =
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.MAX_MAIN_SLOTS));

    public static final StreamCodec<RegistryFriendlyByteBuf, CombinedMemberSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ByteBufCodecs.VAR_INT.encode(buf, payload.epoch());
                ByteBufCodecs.VAR_INT.encode(buf, payload.memberCount());
                ByteBufCodecs.VAR_INT.encode(buf, payload.memberIndex());
                SLOTS.encode(buf, payload.slots());
                RowView.STREAM_CODEC.encode(buf, payload.rows());
                ItemTags.STREAM_CODEC.encode(buf, payload.tags());
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, payload.cursor());
            },
            buf -> {
                int epoch = ByteBufCodecs.VAR_INT.decode(buf);
                int count = ByteBufCodecs.VAR_INT.decode(buf);
                int index = ByteBufCodecs.VAR_INT.decode(buf);
                if (count < 0 || count > AllAddressing.MAX_MEMBERS || index < 0 || index >= Math.max(1, count)) {
                    throw new io.netty.handler.codec.DecoderException("combined member sync: bad member " + index + " of " + count);
                }
                List<ItemStack> slots = SLOTS.decode(buf);
                RowView rows = RowView.STREAM_CODEC.decode(buf);
                ItemTags tags = ItemTags.STREAM_CODEC.decode(buf);
                ItemStack cursor = ItemStack.OPTIONAL_STREAM_CODEC.decode(buf);
                return new CombinedMemberSyncPayload(epoch, count, index, slots, rows, tags, cursor);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(CombinedMemberSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCombinedView.get().onMemberSync(payload));
    }
}
