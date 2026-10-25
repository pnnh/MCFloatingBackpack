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
import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;

/**
 * Server tells client whether the panel may be open and pushes a full contents mirror.
 */
public record BackpackSessionPayload(
        boolean open, int sourceOrdinal, int invIndex, List<ItemStack> slots, ItemStack cursor)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackSessionPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "session"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackSessionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL,
                    BackpackSessionPayload::open,
                    ByteBufCodecs.VAR_INT,
                    BackpackSessionPayload::sourceOrdinal,
                    ByteBufCodecs.VAR_INT,
                    BackpackSessionPayload::invIndex,
                    ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list(BackpackInventory.MAX_SIZE)),
                    BackpackSessionPayload::slots,
                    ItemStack.OPTIONAL_STREAM_CODEC,
                    BackpackSessionPayload::cursor,
                    BackpackSessionPayload::new);

    public static BackpackSessionPayload closed() {
        return new BackpackSessionPayload(
                false, 0, -1, List.of(), ItemStack.EMPTY);
    }

    public static BackpackSessionPayload open(BackpackRef ref, BackpackInventory inventory) {
        List<ItemStack> slots = new ArrayList<>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            slots.add(inventory.getStackInSlot(i).copy());
        }
        return new BackpackSessionPayload(
                true,
                ref.source().ordinal(),
                ref.invIndex(),
                slots,
                inventory.getCursor().copy());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(BackpackSessionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            FloatingPanelState state = FloatingPanelState.get();
            if (!payload.open()) {
                state.setVisible(false);
                state.setScrollRow(0);
                ClientBackpackView.get().clear();
                ClientBackpackView.get().clearActiveRef();
                FloatingPanelEvents.syncMouseToPanelVisibility();
                return;
            }
            ClientBackpackView.get().applyFull(payload.slots(), payload.cursor());
            BackpackRef.Source[] sources = BackpackRef.Source.values();
            int ord = payload.sourceOrdinal();
            BackpackRef.Source source =
                    ord >= 0 && ord < sources.length ? sources[ord] : BackpackRef.Source.MAIN_HAND;
            ClientBackpackView.get()
                    .setActiveRef(new BackpackRef(source, payload.invIndex()));
            state.setVisible(true);
            FloatingPanelEvents.syncMouseToPanelVisibility();
        });
    }
}
