package pnnh.mcfloatingbackpack.network;

import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.PanelCursor;
import pnnh.mcfloatingbackpack.inventory.trash.TrashBin;
import pnnh.mcfloatingbackpack.inventory.trash.TrashContents;
import pnnh.mcfloatingbackpack.inventory.trash.TrashRequest;

/**
 * 021: the client asks to put the cursor stack into the trash can (ADD), take a group out onto the cursor (TAKE) or
 * move groups into the main area (RESTORE). The request carries trash slot numbers (0..8) and whether a container
 * screen is open, never item ids: the server reads everything from the backpack's real contents and the real cursor.
 * Any check that fails drops the whole request and changes nothing; the client is then sent the current trash contents
 * and cursor so its display catches up.
 */
public record BackpackTrashPayload(byte action, int[] slots, boolean screenOpen) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackTrashPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "trash"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackTrashPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.action());
                buf.writeBoolean(payload.screenOpen());
                buf.writeVarInt(payload.slots().length);
                for (int slot : payload.slots()) {
                    buf.writeVarInt(slot);
                }
            },
            buf -> {
                byte action = buf.readByte();
                boolean screenOpen = buf.readBoolean();
                int count = buf.readVarInt();
                if (count < 0 || count > TrashRequest.GROUPS) {
                    throw new DecoderException("trash request slot count out of range: " + count);
                }
                int[] slots = new int[count];
                for (int i = 0; i < count; i++) {
                    slots[i] = buf.readVarInt();
                }
                return new BackpackTrashPayload(action, slots, screenOpen);
            });

    public static BackpackTrashPayload of(TrashRequest.Action action, int[] slots, boolean screenOpen) {
        return new BackpackTrashPayload(action.toByte(), slots, screenOpen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackTrashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BackpackRef ref = BackpackRef.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            BackpackInventory backpack = ref.loadInventory(player);
            TrashRequest.Action action = TrashRequest.Action.fromByte(payload.action());
            // The trash can only works in the right area: no trash can there, no request.
            if (backpack.hasTrashCan() && TrashRequest.validate(action, payload.slots())) {
                switch (action) {
                    case ADD -> add(player, ref, backpack, payload.screenOpen());
                    case TAKE -> take(player, ref, backpack, payload.slots()[0], payload.screenOpen());
                    case RESTORE -> restore(player, ref, backpack, payload.slots());
                }
            }
            // Whatever happened, the client gets the truth about the trash can and the cursor.
            BackpackNetwork.sendTrash(player, backpack);
            BackpackNetwork.sendSlotUpdate(player, ref, -1);
            if (action == TrashRequest.Action.RESTORE) {
                // The main area changed in as many slots as were restored.
                BackpackNetwork.sendFullSync(player, ref);
            }
        });
    }

    /** The cursor stack, whole, becomes the newest group; the oldest is destroyed if that makes ten. */
    private static void add(ServerPlayer player, BackpackRef ref, BackpackInventory backpack, boolean screenOpen) {
        AbstractContainerMenu menu = player.containerMenu;
        boolean viaMenu = screenOpen && menu != null;
        if (viaMenu && !ServerCompatGate.allows(Action.USE_MENU_CURSOR, MenuOwner.ownerOf(menu))) {
            return;
        }
        PanelCursor cursor = PanelCursor.of(screenOpen, menu, backpack);
        if (cursor.usesMenu()) {
            BackpackMenuBridge.mergePanelCursorIntoCarried(backpack, menu);
            // The merge moved the panel cursor into the menu, so what it changed must be saved even if nothing else is.
            ref.saveInventory(player, backpack);
        }
        ItemStack held = cursor.get();
        if (held.isEmpty()) {
            return; // the cursor changed since the client decided (for example it was emptied)
        }
        if (tooLarge(player, held)) {
            player.sendSystemMessage(Component.translatable("message.mcfloatingbackpack.prefix")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" "))
                    .append(Component.translatable("message.mcfloatingbackpack.trash.too_large")
                            .withStyle(ChatFormatting.YELLOW)));
            return;
        }

        int before = total(backpack, held);
        TrashBin.Push<ItemStack> pushed =
                TrashBin.push(ItemStackTransferOps.INSTANCE, backpack.trash().groups(), held.copy());
        backpack.setTrash(new TrashContents(pushed.groups()));
        cursor.set(ItemStack.EMPTY);
        ItemStack destroyed = pushed.destroyed();
        if (!destroyed.isEmpty()) {
            MCFloatingBackpack.LOGGER.debug(
                    "Trash can destroyed {} x{} (player {})",
                    destroyed.getItem(),
                    destroyed.getCount(),
                    player.getGameProfile().getName());
        }
        int after = total(backpack, cursor.get()) + destroyed.getCount();
        if (before != after) {
            // Cannot happen with the pure logic under test; if it ever does, the log says so.
            MCFloatingBackpack.LOGGER.error(
                    "Trash can add changed the item total (held {} x{}, before {}, after {})",
                    held.getItem(),
                    held.getCount(),
                    before,
                    after);
        }
        ref.saveInventory(player, backpack);
        if (viaMenu) {
            menu.broadcastChanges();
        }
    }

    /** The group in trash slot {@code index} goes onto the (empty) cursor; the groups behind it move forward. */
    private static void take(ServerPlayer player, BackpackRef ref, BackpackInventory backpack, int index, boolean screenOpen) {
        AbstractContainerMenu menu = player.containerMenu;
        boolean viaMenu = screenOpen && menu != null;
        if (viaMenu && !ServerCompatGate.allows(Action.USE_MENU_CURSOR, MenuOwner.ownerOf(menu))) {
            return;
        }
        PanelCursor cursor = PanelCursor.of(screenOpen, menu, backpack);
        if (cursor.usesMenu()) {
            BackpackMenuBridge.mergePanelCursorIntoCarried(backpack, menu);
            ref.saveInventory(player, backpack);
        }
        if (!cursor.get().isEmpty()) {
            return; // the cursor changed since the client decided; taking would swap, which the trash can never does
        }
        TrashBin.Take<ItemStack> taken = TrashBin.take(ItemStackTransferOps.INSTANCE, backpack.trash().groups(), index);
        if (taken.taken().isEmpty()) {
            return;
        }
        int before = total(backpack, ItemStack.EMPTY);
        backpack.setTrash(new TrashContents(taken.groups()));
        cursor.set(taken.taken().copy());
        int after = total(backpack, cursor.get());
        if (before != after) {
            MCFloatingBackpack.LOGGER.error("Trash can take changed the item total (before {}, after {})", before, after);
        }
        ref.saveInventory(player, backpack);
        if (viaMenu) {
            menu.broadcastChanges();
        }
    }

    /** The groups in the named trash slots go into the main area, onto same-kind stacks first; what does not fit stays. */
    private static void restore(ServerPlayer player, BackpackRef ref, BackpackInventory backpack, int[] slots) {
        int[] targets = new int[backpack.size()];
        for (int i = 0; i < targets.length; i++) {
            targets[i] = i;
        }
        int before = total(backpack, ItemStack.EMPTY);
        List<ItemStack> left = TrashBin.restore(
                ItemStackTransferOps.INSTANCE,
                backpack.trash().groups(),
                slots,
                targets,
                backpack::getStackInSlot,
                (stack, index) -> backpack.setStackInSlot(index, stack));
        backpack.setTrash(new TrashContents(left));
        int after = total(backpack, ItemStack.EMPTY);
        if (before != after) {
            MCFloatingBackpack.LOGGER.error("Trash can restore changed the item total (before {}, after {})", before, after);
        }
        ref.saveInventory(player, backpack);
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

    /** Items in the trash can, the main area and the given cursor stack. */
    private static int total(BackpackInventory backpack, ItemStack cursor) {
        int sum = cursor.getCount();
        for (ItemStack group : backpack.trash().groups()) {
            sum += group.getCount();
        }
        for (int i = 0; i < backpack.size(); i++) {
            sum += backpack.getStackInSlot(i).getCount();
        }
        return sum;
    }
}
