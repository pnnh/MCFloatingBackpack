package pnnh.mcfloatingbackpack.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ContainerMenuAccess;
import pnnh.mcfloatingbackpack.inventory.ContainerToBackpack;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest;

/**
 * Client request (012, reverse direction): move the stacks in these slots of the open container
 * into the floating backpack. {@code menuSlots} are <em>menu</em> slot indices, so the request names
 * the menu it was made against ({@code containerId}); if the server's open menu is not that one
 * (closed or replaced meanwhile) the whole request is dropped.
 */
public record BackpackTakeFromContainerPayload(int containerId, int[] menuSlots) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackTakeFromContainerPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "take_from_container"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackTakeFromContainerPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeVarInt(payload.containerId());
                        buf.writeVarInt(payload.menuSlots().length);
                        for (int slot : payload.menuSlots()) {
                            buf.writeVarInt(slot);
                        }
                    },
                    buf -> {
                        int containerId = buf.readVarInt();
                        int count = buf.readVarInt();
                        if (count < 0 || count > QuickTransferRequest.MAX_MENU_SLOTS) {
                            throw new DecoderException("take-from-container slot count out of range: " + count);
                        }
                        int[] slots = new int[count];
                        for (int i = 0; i < count; i++) {
                            slots[i] = buf.readVarInt();
                        }
                        return new BackpackTakeFromContainerPayload(containerId, slots);
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackTakeFromContainerPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BackpackRef ref = BackpackRef.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            AbstractContainerMenu menu = player.containerMenu;
            if (!ServerCompatGate.allows(Action.TAKE_FROM_CONTAINER, MenuOwner.ownerOf(menu))) {
                return;
            }
            // Same precondition as the other quick transfers: nothing on the cursor. The menu must be an
            // open container (not the player's own inventory) and the very one the client clicked in.
            if (!menu.getCarried().isEmpty()
                    || menu == player.inventoryMenu
                    || menu.containerId != payload.containerId()
                    || !QuickTransferRequest.validateMenuSlots(payload.menuSlots(), menu.slots.size())) {
                return;
            }

            // 026: every request into a backpack goes through the registry first.

            BackpackInventory backpack = BackpackGate.acquireForPanel(player, ref).orElse(null);

            if (backpack == null) {

                BackpackNetwork.sendSession(player, false, null);

                return;

            }
            takeIntoBackpack(player, backpack, payload.menuSlots());

            ref.saveInventory(player, backpack);
            player.getInventory().setChanged();
            menu.broadcastChanges();
            BackpackNetwork.sendFullSync(player, ref);
        });
    }

    static void takeIntoBackpack(ServerPlayer player, BackpackInventory backpack, int[] menuSlots) {
        ContainerMenuAccess access = new ContainerMenuAccess(player);
        for (int menuIndex : menuSlots) {
            // Slots of the player's own inventory go through 011's request; here only the container's slots.
            if (!access.isExternalSlot(menuIndex)) {
                continue;
            }
            ItemStack before = access.get(menuIndex).copy();
            ContainerToBackpack.Result result = ContainerToBackpack.take(
                    ItemStackTransferOps.INSTANCE,
                    access.slotAccess(menuIndex),
                    backpack::mainTargetOrder,
                    backpack::getStackInSlot,
                    (s, i) -> backpack.setStackInSlot(i, s),
                    access.fallback());
            if (result.usedFallback() || result.dropped()) {
                MCFloatingBackpack.LOGGER.warn(
                        "Quick transfer out of container needed a fallback (menu {}, slot {}, item {}, moved {}, dropped {})",
                        access.menu().getClass().getName(),
                        menuIndex,
                        before.getItem(),
                        result.moved(),
                        result.dropped());
            }
        }
    }
}
