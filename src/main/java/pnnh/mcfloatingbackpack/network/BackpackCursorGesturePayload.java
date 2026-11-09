package pnnh.mcfloatingbackpack.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.MenuOwner;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate.Action;
import pnnh.mcfloatingbackpack.compat.ServerCompatGate;
import pnnh.mcfloatingbackpack.inventory.BackpackClickLogic;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackMenuBridge;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.PanelCursor;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic;
import pnnh.mcfloatingbackpack.inventory.PanelCursorLogic.DragMode;
import pnnh.mcfloatingbackpack.inventory.PanelCursorRequest;
import pnnh.mcfloatingbackpack.inventory.PanelCursorRequest.Mode;
import pnnh.mcfloatingbackpack.inventory.QuickTransferRequest;

/**
 * Client request for a drag gesture on the floating panel while holding a stack on the cursor (013):
 * distribute it over the slots that were dragged across, collect same-kind items into it, or the
 * double-click gather. The cursor is wherever the player's held stack lives right now (the open menu's
 * carried stack, or the panel's own cursor); {@code screenOpen} is reported by the client like in
 * {@link BackpackClickPayload}. The server re-resolves the backpack and re-checks every slot; nothing in
 * this request is trusted.
 */
public record BackpackCursorGesturePayload(byte mode, int[] slots, boolean screenOpen)
        implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackCursorGesturePayload> TYPE =
            new CustomPacketPayload.Type<>(
                    ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "cursor_gesture"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackCursorGesturePayload> STREAM_CODEC =
            StreamCodec.of(
                    (buf, payload) -> {
                        buf.writeByte(payload.mode());
                        buf.writeBoolean(payload.screenOpen());
                        buf.writeVarInt(payload.slots().length);
                        for (int slot : payload.slots()) {
                            buf.writeVarInt(slot);
                        }
                    },
                    buf -> {
                        byte mode = buf.readByte();
                        boolean screenOpen = buf.readBoolean();
                        int count = buf.readVarInt();
                        if (count < 0 || count > QuickTransferRequest.MAX_BACKPACK_SLOTS) {
                            throw new DecoderException("cursor gesture slot count out of range: " + count);
                        }
                        int[] slots = new int[count];
                        for (int i = 0; i < count; i++) {
                            slots[i] = buf.readVarInt();
                        }
                        return new BackpackCursorGesturePayload(mode, slots, screenOpen);
                    });

    public static BackpackCursorGesturePayload of(Mode mode, int[] slots, boolean screenOpen) {
        return new BackpackCursorGesturePayload(mode.toByte(), slots, screenOpen);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackCursorGesturePayload payload, IPayloadContext context) {
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
            BackpackInventory backpack = BackpackGate.acquire(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            Mode mode = Mode.fromByte(payload.mode());
            if (!PanelCursorRequest.validate(mode, payload.slots(), backpack.size())) {
                return;
            }

            AbstractContainerMenu menu = player.containerMenu;
            if (payload.screenOpen() && menu != null
                    && !ServerCompatGate.allows(Action.USE_MENU_CURSOR, MenuOwner.ownerOf(menu))) {
                return;
            }
            PanelCursor cursor = PanelCursor.of(payload.screenOpen(), menu, backpack);
            if (cursor.usesMenu()) {
                BackpackMenuBridge.mergePanelCursorIntoCarried(backpack, menu);
            }
            ItemStack held = cursor.get();
            if (held.isEmpty()) {
                return; // the cursor changed since the client decided (for example it was emptied)
            }

            int before = total(backpack, held);
            switch (mode) {
                case DISTRIBUTE_LEFT -> distribute(backpack, cursor, held, payload.slots(), DragMode.LEFT);
                case DISTRIBUTE_RIGHT -> distribute(backpack, cursor, held, payload.slots(), DragMode.RIGHT);
                case GATHER_DRAG -> gatherSlots(backpack, cursor, held, payload.slots());
                case GATHER_ALL -> gatherAll(backpack, cursor, held);
            }
            int after = total(backpack, cursor.get());
            if (before != after) {
                // Cannot happen with the pure logic under test; if it ever does, the log says so.
                MCFloatingBackpack.LOGGER.error(
                        "Panel cursor gesture {} changed the item total from {} to {}", mode, before, after);
            }

            ref.saveInventory(player, backpack);
            player.getInventory().setChanged();
            if (menu != null) {
                menu.broadcastChanges();
            }
            BackpackNetwork.sendFullSync(player, ref);
        });
    }

    private static int total(BackpackInventory backpack, ItemStack cursorStack) {
        int sum = cursorStack.getCount();
        for (int i = 0; i < backpack.size(); i++) {
            sum += backpack.getStackInSlot(i).getCount();
        }
        return sum;
    }

    private static void distribute(
            BackpackInventory backpack, PanelCursor cursor, ItemStack held, int[] requested, DragMode mode) {
        ItemStackTransferOps ops = ItemStackTransferOps.INSTANCE;
        // Slots that cannot take part any more (changed since the client looked) are dropped, as vanilla
        // does when the drag ends; only the remaining ones share the stack.
        List<Integer> slots = new ArrayList<>();
        for (int slot : requested) {
            if (PanelCursorLogic.canQuickReplace(ops, backpack.getStackInSlot(slot), held)) {
                slots.add(slot);
            }
        }
        if (slots.isEmpty()) {
            return;
        }
        if (slots.size() == 1) {
            // A drag over a single slot is an ordinary click on it.
            ItemStack left = BackpackClickLogic.click(
                    backpack::getStackInSlot,
                    (stack, index) -> backpack.setStackInSlot(index, stack),
                    held,
                    slots.get(0),
                    mode == DragMode.RIGHT);
            cursor.set(left);
            return;
        }
        List<ItemStack> current = new ArrayList<>(slots.size());
        for (int slot : slots) {
            current.add(backpack.getStackInSlot(slot).copy());
        }
        PanelCursorLogic.Distribution<ItemStack> result =
                PanelCursorLogic.distribute(ops, mode, held, current, Item.ABSOLUTE_MAX_STACK_SIZE);
        if (result.rejected()) {
            return;
        }
        for (int i = 0; i < slots.size(); i++) {
            backpack.setStackInSlot(slots.get(i), result.stacks().get(i));
        }
        cursor.set(result.cursorLeft());
    }

    private static void gatherSlots(BackpackInventory backpack, PanelCursor cursor, ItemStack held, int[] slots) {
        ItemStackTransferOps ops = ItemStackTransferOps.INSTANCE;
        ItemStack current = held;
        for (int slot : slots) {
            PanelCursorLogic.Gathered<ItemStack> g =
                    PanelCursorLogic.gather(ops, backpack.getStackInSlot(slot), current);
            if (g.moved() > 0) {
                backpack.setStackInSlot(slot, g.slot());
                current = g.cursor();
            }
        }
        cursor.set(current);
    }

    private static void gatherAll(BackpackInventory backpack, PanelCursor cursor, ItemStack held) {
        List<ItemStack> all = new ArrayList<>(backpack.size());
        for (int i = 0; i < backpack.size(); i++) {
            all.add(backpack.getStackInSlot(i).copy());
        }
        PanelCursorLogic.PickedAll<ItemStack> result =
                PanelCursorLogic.pickupAll(ItemStackTransferOps.INSTANCE, held, all);
        if (result.moved() <= 0) {
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            backpack.setStackInSlot(i, result.slots().get(i));
        }
        cursor.set(result.cursor());
    }
}
