package pnnh.mcfloatingbackpack.network;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest;
import pnnh.mcfloatingbackpack.inventory.trash.DeletableSlots;
import pnnh.mcfloatingbackpack.inventory.trash.TrashBin;
import pnnh.mcfloatingbackpack.inventory.trash.TrashContents;
import pnnh.mcfloatingbackpack.inventory.trash.TrashDeletePlan;
import pnnh.mcfloatingbackpack.inventory.trash.TrashSendRequest;
import pnnh.mcfloatingbackpack.inventory.trash.TrashSendRequest.Source;
import pnnh.mcfloatingbackpack.inventory.trash.TrashStacks;

/**
 * 022: the client asks to move stacks into the trash can in bulk: a dump of the trash-tagged stacks of the main area,
 * the stacks in named main-area slots (a selection, or the slot under the mouse), or the stack in one slot of the open
 * vanilla inventory or chest. The request carries slot numbers (and the menu's id), never items: the server picks the
 * stacks from the real contents. An operation that would destroy more than nine groups for good must say it is
 * confirmed. Any check that fails drops the whole request and changes nothing; the client is then sent the current
 * trash contents and main area so its display catches up.
 */
public record BackpackTrashSendPayload(byte source, boolean confirmed, int containerId, int[] slots)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackTrashSendPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "trash_send"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackTrashSendPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.source());
                buf.writeBoolean(payload.confirmed());
                buf.writeVarInt(payload.containerId());
                buf.writeVarInt(payload.slots().length);
                for (int slot : payload.slots()) {
                    buf.writeVarInt(slot);
                }
            },
            buf -> {
                byte source = buf.readByte();
                boolean confirmed = buf.readBoolean();
                int containerId = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > QuickTransferRequest.MAX_BACKPACK_SLOTS) {
                    throw new DecoderException("trash send slot count out of range: " + count);
                }
                int[] slots = new int[count];
                for (int i = 0; i < count; i++) {
                    slots[i] = buf.readVarInt();
                }
                return new BackpackTrashSendPayload(source, confirmed, containerId, slots);
            });

    public static BackpackTrashSendPayload of(Source source, boolean confirmed, int containerId, int[] slots) {
        return new BackpackTrashSendPayload(source.toByte(), confirmed, containerId, slots);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** One stack that is about to go into the trash can, and how to take it from where it lies. */
    private record Take(ItemStack stack, Runnable remove) {
    }

    public static void handleServer(BackpackTrashSendPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BackpackRef ref = BackpackRef.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // 026: every request into a backpack goes through the registry first.
            BackpackInventory backpack = BackpackGate.acquireForPanel(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            Source source = Source.fromByte(payload.source());
            if (backpack.hasTrashCan()
                    && TrashSendRequest.validate(source, payload.slots(), payload.containerId(), backpack.size())) {
                send(player, ref, backpack, source, payload);
            }
            // Whatever happened, the client gets the truth about the trash can and the main area.
            BackpackNetwork.sendTrash(player, backpack);
            BackpackNetwork.sendFullSync(player, ref);
            BackpackNetwork.sendSlotUpdate(player, ref, -1);
        });
    }

    private static void send(
            ServerPlayer player, BackpackRef ref, BackpackInventory backpack, Source source, BackpackTrashSendPayload payload) {
        AbstractContainerMenu menu = player.containerMenu;
        List<Take> takes = switch (source) {
            case DUMP -> collectDump(backpack);
            case MAIN_SLOTS -> collectMain(backpack, payload.slots());
            case MENU_SLOT -> collectMenu(player, menu, payload.containerId(), payload.slots()[0]);
        };
        if (takes.isEmpty()) {
            return;
        }
        int destroyedGroups = TrashDeletePlan.destroyed(backpack.trash().groups().size(), takes.size());
        if (TrashDeletePlan.needsConfirm(destroyedGroups) && !payload.confirmed()) {
            notice(player, "message.mcfloatingbackpack.trash.changed");
            return;
        }
        for (Take take : takes) {
            if (tooLarge(player, take.stack())) {
                notice(player, "message.mcfloatingbackpack.trash.too_large");
                return;
            }
        }

        int menuHeld = 0;
        if (source == Source.MENU_SLOT) {
            menuHeld = takes.get(0).stack().getCount();
        }
        int before = total(backpack) + menuHeld;
        int destroyedItems = 0;
        List<ItemStack> groups = new ArrayList<>(backpack.trash().groups());
        for (Take take : takes) {
            ItemStack stack = take.stack().copy();
            take.remove().run();
            TrashBin.Push<ItemStack> pushed = TrashBin.push(ItemStackTransferOps.INSTANCE, groups, stack);
            groups = new ArrayList<>(pushed.groups());
            ItemStack destroyed = pushed.destroyed();
            if (!destroyed.isEmpty()) {
                destroyedItems += destroyed.getCount();
                MCFloatingBackpack.LOGGER.debug(
                        "Trash can destroyed {} x{} (player {})",
                        destroyed.getItem(),
                        destroyed.getCount(),
                        player.getGameProfile().getName());
            }
        }
        backpack.setTrash(new TrashContents(groups));
        // The main-area slots were emptied by the removals above through the backpack's own handler.
        int after = total(backpack) + destroyedItems;
        if (before != after) {
            // Cannot happen with the pure logic under test; if it ever does, the log says so.
            MCFloatingBackpack.LOGGER.error(
                    "Trash send changed the item total (source {}, before {}, after {})", source, before, after);
        }
        ref.saveInventory(player, backpack);
        if (source == Source.MENU_SLOT) {
            menu.broadcastChanges();
        }
    }

    /** The main-area stacks a dump takes: trash-tagged, not protected, only wear on them. */
    private static List<Take> collectDump(BackpackInventory backpack) {
        int[] slots = TrashStacks.dumpSlots(backpack.size(), backpack::getStackInSlot, backpack.tags());
        return mainTakes(backpack, slots);
    }

    /** The stacks in the named main-area slots, ascending; empty slots are skipped. */
    private static List<Take> collectMain(BackpackInventory backpack, int[] slots) {
        int[] sorted = slots.clone();
        Arrays.sort(sorted);
        return mainTakes(backpack, sorted);
    }

    private static List<Take> mainTakes(BackpackInventory backpack, int[] slots) {
        List<Take> takes = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                takes.add(new Take(stack.copy(), () -> backpack.setStackInSlot(slot, ItemStack.EMPTY)));
            }
        }
        return takes;
    }

    /**
     * The stack in one slot of the vanilla inventory or chest menu the player has open. Everything is checked: it is
     * the open menu, it is one of those two vanilla kinds, the slot is one the delete key is allowed to take and the
     * player may take from it.
     */
    private static List<Take> collectMenu(ServerPlayer player, AbstractContainerMenu menu, int containerId, int menuSlot) {
        if (menu == null
                || menu.containerId != containerId
                || !(menu instanceof InventoryMenu || menu instanceof ChestMenu)) {
            return List.of();
        }
        String owner = MenuOwner.ownerOf(menu);
        if (!"minecraft".equals(owner) || !ServerCompatGate.allows(Action.TAKE_FROM_CONTAINER, owner)) {
            return List.of();
        }
        if (menuSlot < 0 || menuSlot >= menu.slots.size()) {
            return List.of();
        }
        Slot slot = menu.slots.get(menuSlot);
        boolean own = slot.container == player.getInventory();
        boolean chestOwn = menu instanceof ChestMenu chest && slot.container == chest.getContainer();
        if (!DeletableSlots.allowed(own, slot.getContainerSlot(), chestOwn)
                || !slot.hasItem()
                || !slot.mayPickup(player)) {
            return List.of();
        }
        ItemStack stack = slot.getItem().copy();
        return List.of(new Take(stack, () -> {
            slot.remove(stack.getCount());
            slot.setChanged();
        }));
    }

    /** Whether the stack, as it goes over the wire, is more than one group may take (see {@link TrashContents#MAX_GROUP_ENCODED_BYTES}). */
    private static boolean tooLarge(ServerPlayer player, ItemStack stack) {
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), player.registryAccess());
        try {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, stack);
            return buf.readableBytes() > TrashContents.MAX_GROUP_ENCODED_BYTES;
        } catch (RuntimeException failed) {
            return true;
        } finally {
            buf.release();
        }
    }

    /** Items in the trash can and the main area. */
    private static int total(BackpackInventory backpack) {
        int sum = 0;
        for (ItemStack group : backpack.trash().groups()) {
            sum += group.getCount();
        }
        for (int i = 0; i < backpack.size(); i++) {
            sum += backpack.getStackInSlot(i).getCount();
        }
        return sum;
    }

    private static void notice(ServerPlayer player, String key) {
        player.sendSystemMessage(Component.translatable("message.mcfloatingbackpack.prefix")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(Component.translatable(key).withStyle(ChatFormatting.YELLOW)));
    }
}
