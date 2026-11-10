package pnnh.mcfloatingbackpack.network;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ContainerMenuAccess;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.OpenContainerDelivery;
import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest;
import pnnh.mcfloatingbackpack.inventory.creative.CreativeInventorySync;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest.Direction;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest.Holder;

/**
 * Client request to quick-transfer stacks between the player's inventory (indices 0-35) and the
 * open floating backpack (011). {@code slots} are indices on the <em>source</em> side. The server
 * validates everything and re-resolves the backpack itself; nothing here is trusted.
 */
public record BackpackQuickTransferPayload(byte direction, int[] slots) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackQuickTransferPayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "quick_transfer"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackQuickTransferPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.direction());
                        buf.writeVarInt(payload.slots().length);
                        for (int slot : payload.slots()) {
                            buf.writeVarInt(slot);
                        }
                    },
                    buf -> {
                        byte direction = buf.readByte();
                        int count = buf.readVarInt();
                        if (count < 0 || count > QuickTransferRequest.MAX_BACKPACK_SLOTS) {
                            throw new DecoderException("quick transfer slot count out of range: " + count);
                        }
                        int[] slots = new int[count];
                        for (int i = 0; i < count; i++) {
                            slots[i] = buf.readVarInt();
                        }
                        return new BackpackQuickTransferPayload(direction, slots);
                    });

    public static BackpackQuickTransferPayload of(Direction direction, int[] slots) {
        return new BackpackQuickTransferPayload(direction.toByte(), slots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackQuickTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            // The server has no "panel is open" state (BackpackClickPayload's flag is client-reported),
            // so what can be checked is that a usable backpack exists on this player right now.
            BackpackRef ref = BackpackRef.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // Same precondition as vanilla shift-click: nothing may be held on the cursor.
            if (!player.containerMenu.getCarried().isEmpty()) {
                return;
            }
            // 026: every request into a backpack goes through the registry first.
            BackpackInventory backpack = BackpackGate.acquire(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            Direction direction = Direction.fromByte(payload.direction());
            DiagLog.record(player.getUUID(), "quick transfer " + direction + " slots=" + (payload.slots() == null ? 0 : payload.slots().length)
                    + " hand=" + DiagLog.stack(player.getMainHandItem()));
            if (!QuickTransferRequest.validate(direction, payload.slots(), backpack.size())) {
                return;
            }

            if (direction == Direction.TO_BACKPACK) {
                int protectedIndex = QuickTransferRequest.protectedInventoryIndex(
                        holderOf(ref), ref.invIndex(), player.getInventory().selected);
                inventoryToBackpack(player.getInventory(), backpack, payload.slots(), protectedIndex);
            } else {
                backpackOut(player, backpack, payload.slots());
            }

            ref.saveInventory(player, backpack);
            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            // 029: the creative inventory's item tab ignores single-slot updates; send the whole inventory.
            CreativeInventorySync.afterInventoryChange(player);
            BackpackNetwork.sendFullSync(player, ref);
        });
    }

    private static Holder holderOf(BackpackRef ref) {
        // 032: anything worn (the chest, later an accessories mod's back slot) is not in inventory 0-35, so there is no index to protect.
        if (ref.source().isWorn()) {
            return Holder.CHEST;
        }
        return switch (ref.source()) {
            case MAIN_HAND -> Holder.MAIN_HAND;
            case OFF_HAND -> Holder.OFF_HAND;
            default -> Holder.INVENTORY;
        };
    }

    private static int[] ascending(int count) {
        int[] order = new int[count];
        for (int i = 0; i < count; i++) {
            order[i] = i;
        }
        return order;
    }

    /** Player inventory indices -> backpack slots. The backpack item itself is never moved. */
    static void inventoryToBackpack(Inventory inventory, BackpackInventory backpack, int[] sources, int protectedIndex) {
        for (int index : sources) {
            if (index == protectedIndex) {
                continue;
            }
            ItemStack stack = inventory.getItem(index);
            if (stack.isEmpty()) {
                continue;
            }
            // 035: into the rows that take the stack first, then into the tier capacity's cells.
            inventory.setItem(index, backpack.insertIntoMain(stack));
        }
    }

    /**
     * Backpack slots -> out of the backpack (012). With a container open (a menu that has slots beyond
     * the player's own inventory) each stack goes into that container by the menu's own shift-click
     * rule; otherwise it goes to the player inventory as in 011. Whatever does not fit stays in the
     * backpack.
     */
    static void backpackOut(ServerPlayer player, BackpackInventory backpack, int[] sources) {
        if (!ContainerMenuAccess.hasOpenContainer(player)) {
            backpackToInventory(player.getInventory(), backpack, sources);
            return;
        }
        if (!ServerCompatGate.allows(Action.DELIVER_TO_CONTAINER, MenuOwner.ownerOf(player.containerMenu))) {
            return;
        }
        ContainerMenuAccess menu = new ContainerMenuAccess(player);
        for (int index : sources) {
            ItemStack stack = backpack.getStackInSlot(index);
            if (stack.isEmpty()) {
                continue;
            }
            OpenContainerDelivery.Result<ItemStack> result =
                    OpenContainerDelivery.deliver(ItemStackTransferOps.INSTANCE, menu, stack);
            backpack.setStackInSlot(index, result.leftover());
            if (result.error() != null) {
                MCFloatingBackpack.LOGGER.error(
                        "Quick transfer into open container threw (menu {}, item {}); items in the temporary slot were taken back",
                        menu.menu().getClass().getName(),
                        stack.getItem(),
                        result.error());
            }
            if (!result.conserved()) {
                MCFloatingBackpack.LOGGER.error(
                        "Quick transfer into open container changed the item total (menu {}, item {}, delivered {}, left over {})",
                        menu.menu().getClass().getName(),
                        stack.getItem(),
                        stack.getCount(),
                        result.leftover().getCount());
            }
        }
    }

    /**
     * Backpack slots -> player inventory indices 0-35. Written against {@code Inventory.getItem/setItem}
     * on purpose: {@code Inventory.add} discards what does not fit for players with infinite materials.
     */
    static void backpackToInventory(Inventory inventory, BackpackInventory backpack, int[] sources) {
        int[] targets = ascending(QuickTransferRequest.INVENTORY_SLOTS);
        for (int index : sources) {
            ItemStack stack = backpack.getStackInSlot(index);
            if (stack.isEmpty()) {
                continue;
            }
            ItemStack rest = QuickTransferLogic.moveInto(
                    ItemStackTransferOps.INSTANCE, stack, targets, inventory::getItem, (s, i) -> inventory.setItem(i, s));
            backpack.setStackInSlot(index, rest);
        }
    }
}
