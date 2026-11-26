package pnnh.mcfloatingbackpack.network;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting;
import pnnh.mcfloatingbackpack.inventory.nesting.NestingRules;
import pnnh.mcfloatingbackpack.inventory.quick.QuickLists;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 067: the client asks to swap one listed stack with the main hand. The server recomputes the list and does not trust the
 * scroll position. A refusal is an action-bar line plus {@code ok = false}; the grid stays open.
 */
public record QuickSwapPayload(int sourceOrdinal, int invIndex, int slot) implements CustomPacketPayload {
    private static final int NOTICE_TICKS = 20;
    private static final Map<String, Integer> NOTICED = new HashMap<>();

    public static final CustomPacketPayload.Type<QuickSwapPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "quick_swap"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuickSwapPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            QuickSwapPayload::sourceOrdinal,
            ByteBufCodecs.VAR_INT,
            QuickSwapPayload::invIndex,
            ByteBufCodecs.VAR_INT,
            QuickSwapPayload::slot,
            QuickSwapPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(QuickSwapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player player = context.player();
            boolean ok = player instanceof ServerPlayer serverPlayer && swap(serverPlayer, payload);
            if (player instanceof ServerPlayer serverPlayer) {
                PacketDistributor.sendToPlayer(serverPlayer, new QuickSwapResultPayload(ok));
            }
        });
    }

    private static boolean swap(ServerPlayer player, QuickSwapPayload payload) {
        if (player.containerMenu != player.inventoryMenu) {
            return refuse(player, "message.mcfloatingbackpack.quick.refused");
        }
        BackpackRef resolved = BackpackRef.resolve(player);
        BackpackRef asked = decode(payload.sourceOrdinal(), payload.invIndex());
        int selected = player.getInventory().selected;
        if (resolved == null || asked == null || !resolved.normalized(selected).equals(asked.normalized(selected))) {
            return refuse(player, "message.mcfloatingbackpack.quick.refused");
        }
        // acquire only: a newer revision is refused quietly and the 040 confirm window stays shut
        Optional<BackpackInventory> got = BackpackGate.acquire(player, resolved);
        if (got.isEmpty()) {
            return refuse(player, "message.mcfloatingbackpack.quick.refused");
        }
        BackpackInventory inventory = got.get();
        if (!inventory.hasQuickAccess()) {
            return refuse(player, "message.mcfloatingbackpack.quick.need_tool");
        }
        if (!QuickLists.candidates(inventory).contains(payload.slot())) {
            return refuse(player, "message.mcfloatingbackpack.quick.refused");
        }
        ItemStack hand = player.getMainHandItem();
        if (FloatingBackpackItem.isBackpack(hand) || BackpackNesting.refusal(player, resolved, hand) != NestingRules.Verdict.OK) {
            return refuse(player, "message.mcfloatingbackpack.quick.refused");
        }
        int dest = pnnh.mcfloatingbackpack.inventory.quick.HandSwapSlots.destination(inventory, payload.slot(), hand);
        if (!hand.isEmpty() && dest < 0) {
            return refuse(player, "message.mcfloatingbackpack.quick.no_room");
        }
        boolean heldRisky = BackpackNesting.isRisky(hand);
        ItemStack taken = inventory.getStackInSlot(payload.slot()).copy();
        if (hand.isEmpty() || dest == payload.slot()) {
            inventory.setStackInSlot(payload.slot(), hand.copy());
        } else {
            inventory.setStackInSlot(payload.slot(), ItemStack.EMPTY);
            inventory.setStackInSlot(dest, hand.copy());
        }
        player.setItemInHand(InteractionHand.MAIN_HAND, taken);
        player.getInventory().setChanged();
        if (heldRisky) {
            BackpackNesting.forget(player.getUUID());
        }
        if (!taken.isEmpty() && !ItemStack.matches(taken, inventory.getStackInSlot(payload.slot()))) {
            BackpackNesting.rememberTakenOut(player, resolved, taken);
        }
        resolved.saveInventory(player, inventory);
        if (BackpackGate.sessionOpenOn(player, resolved.getStack(player))
                || pnnh.mcfloatingbackpack.inventory.combined.CombinedSessions.has(player.getUUID())) {
            BackpackNetwork.sendSlotUpdate(player, resolved, payload.slot());
            if (dest != payload.slot()) {
                BackpackNetwork.sendSlotUpdate(player, resolved, dest);
            }
        }
        return true;
    }

    private static boolean refuse(ServerPlayer player, String key) {
        int now = player.tickCount;
        String id = player.getUUID() + "\0" + key;
        Integer previous = NOTICED.get(id);
        if (previous == null || now - previous >= NOTICE_TICKS || now < previous) {
            NOTICED.put(id, now);
            player.displayClientMessage(Component.translatable(key), true);
        }
        return false;
    }

    private static BackpackRef decode(int sourceOrdinal, int invIndex) {
        BackpackRef.Source[] sources = BackpackRef.Source.values();
        if (sourceOrdinal < 0 || sourceOrdinal >= sources.length) {
            return null;
        }
        return new BackpackRef(sources[sourceOrdinal], invIndex);
    }
}
