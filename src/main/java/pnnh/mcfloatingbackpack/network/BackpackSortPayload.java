package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;

/**
 * 047: the client asks the server to tidy the open backpack's main area. No fields: the server works out everything itself from the
 * backpack it resolves (nothing here is trusted), merges the stacks, puts them in registry order and into the category rows first
 * (see {@code BackpackSorter}), saves, and sends the whole backpack back. Like the other batch actions it is refused while anything
 * is held on a cursor.
 */
public record BackpackSortPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackSortPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "sort"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackSortPayload> STREAM_CODEC =
            StreamCodec.unit(new BackpackSortPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackSortPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BackpackRef ref = BackpackRef.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // Same precondition as the quick transfer: nothing may be held on the cursor.
            if (!player.containerMenu.getCarried().isEmpty()) {
                return;
            }
            // 026: every request that changes a backpack goes through the registry first.
            BackpackInventory backpack = BackpackGate.acquireForPanel(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // the panel's own cursor (a stack taken up in the panel) counts as held too
            if (!backpack.getCursor().isEmpty()) {
                return;
            }
            DiagLog.record(player.getUUID(), "sort hand=" + DiagLog.stack(player.getMainHandItem()));
            if (backpack.sortMain()) {
                ref.saveInventory(player, backpack);
            } else {
                MCFloatingBackpack.LOGGER.warn(
                        "Sort of the backpack of {} was given up: no safe layout (nothing was changed)", player.getGameProfile().getName());
            }
            // either way the client is told how the backpack is now, so that both sides agree
            BackpackNetwork.sendFullSync(player, ref);
        });
    }
}
