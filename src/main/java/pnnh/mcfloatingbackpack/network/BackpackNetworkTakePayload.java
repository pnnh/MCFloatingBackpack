package pnnh.mcfloatingbackpack.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.NetworkStorageBridges;
import pnnh.mcfloatingbackpack.compat.NetworkTakeRules;
import pnnh.mcfloatingbackpack.compat.ServerCompatDenials;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ContainerMenuAccess;
import pnnh.mcfloatingbackpack.inventory.ContainerToBackpack;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.combined.PanelTargets;

/**
 * 060: client request to take one stack of an entry of a network terminal's list (Integrated Terminals, AE2) into the floating backpack.
 * The entries are not slots of the menu, so the request names the item ({@code what}, one of it, with its data) and an opaque number the
 * mod's own client code gave ({@code handle}; AE2's entry number, 0 for Integrated Terminals). {@code amount} is 0 for "a full stack" (the
 * Ctrl+click) or how many of the item the player holds on the terminal's own cursor (Integrated Terminals keeps a picked-up entry as an "active
 * entry" with an amount, not as the menu's carried stack); the server never asks for more than a full stack. Nothing the client says is trusted beyond that: the server checks the open
 * menu, the backpack, the gate of 059 and what the bridge hands back.
 */
public record BackpackNetworkTakePayload(int containerId, ItemStack what, long handle, int amount) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackNetworkTakePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "network_take"));

    /** {@code ItemStack.STREAM_CODEC} does not accept an empty stack (it throws when it reads one), so a request for nothing never arrives. */
    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackNetworkTakePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BackpackNetworkTakePayload::containerId,
            ItemStack.STREAM_CODEC, BackpackNetworkTakePayload::what,
            ByteBufCodecs.VAR_LONG, BackpackNetworkTakePayload::handle,
            ByteBufCodecs.VAR_INT, BackpackNetworkTakePayload::amount,
            BackpackNetworkTakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackNetworkTakePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BackpackRef ref = PanelTargets.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (!ServerCompatDenials.allows(player, menu, Action.TAKE_FROM_CONTAINER)) {
                return;
            }
            // The same preconditions as the other quick transfers: nothing on the cursor, a menu that is not the player's own, and the very
            // one the client clicked in. The item must be a real one; how much is asked for is the server's decision, not the client's.
            if (!menu.getCarried().isEmpty()
                    || menu == player.inventoryMenu
                    || menu.containerId != payload.containerId()
                    || payload.what().isEmpty()) {
                return;
            }
            BackpackInventory backpack = BackpackGate.acquireForPanel(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // 064: a floating backpack (or an item with one inside) is not taken out of the network into a backpack.
            pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict nesting =
                    pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.check(payload.what());
            if (nesting != pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict.OK) {
                pnnh.mcfloatingbackpack.inventory.nesting.BackpackNesting.tell(player, nesting);
                return;
            }
            int amount = NetworkTakeRules.amountFor(ItemStackTransferOps.INSTANCE, payload.what(), payload.amount());
            var taken = NetworkStorageBridges.tryTake(player, menu, payload.what().copyWithCount(Math.max(1, amount)), payload.handle());
            if (taken.isEmpty() || taken.get().isEmpty()) {
                return;
            }
            ItemStack stack = taken.get();
            int count = stack.getCount();
            ItemStack left = backpack.insertIntoMain(stack.copy());
            int backToNetwork = 0;
            ItemStack unplaced = left;
            if (!left.isEmpty()) {
                unplaced = NetworkStorageBridges.tryGiveBack(player, menu, left.copy(), payload.handle()).orElse(left);
                backToNetwork = left.getCount() - unplaced.getCount();
            }
            if (!unplaced.isEmpty()) {
                ContainerToBackpack.Fallback<ItemStack> fallback = new ContainerMenuAccess(player).fallback();
                ItemStack notInInventory = fallback.toInventory(unplaced.copy());
                if (!notInInventory.isEmpty()) {
                    fallback.drop(notInInventory);
                }
                MCFloatingBackpack.LOGGER.warn(
                        "Taking {} x {} out of a network terminal (menu {}): the backpack took {}, the network took back {}, {} went to the inventory or the ground",
                        count, stack.getItem(), menu.getClass().getName(), count - left.getCount(), backToNetwork, unplaced.getCount());
            }
            NetworkTakeRules.Split split = NetworkTakeRules.distribute(count, left.getCount(), unplaced.getCount());
            if (split.total() != count) {
                MCFloatingBackpack.LOGGER.error("Taking out of a network terminal changed the item total (taken {}, split {})", count, split);
            }
            ref.saveInventory(player, backpack);
            player.getInventory().setChanged();
            menu.broadcastChanges();
            BackpackNetwork.sendFullSync(player, ref);
        });
    }
}
