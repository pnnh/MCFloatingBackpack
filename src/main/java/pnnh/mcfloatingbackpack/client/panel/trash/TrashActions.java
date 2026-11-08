package pnnh.mcfloatingbackpack.client.panel.trash;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.PanelSelection;
import pnnh.mcfloatingbackpack.client.panel.trash.TrashConfirmState.Kind;
import pnnh.mcfloatingbackpack.inventory.trash.TrashDeletePlan;
import pnnh.mcfloatingbackpack.inventory.trash.TrashSendRequest.Source;
import pnnh.mcfloatingbackpack.inventory.trash.TrashStacks;
import pnnh.mcfloatingbackpack.network.BackpackTrashSendPayload;

/**
 * 022: what the dump, the delete icon and the delete key do on the client: work out what the request would take, ask
 * for confirmation first if it would destroy more than nine groups for good, otherwise send it. The server does all of
 * it again from the real contents; this only spares a round trip and lets the player decide.
 */
@OnlyIn(Dist.CLIENT)
public final class TrashActions {
    private TrashActions() {
    }

    /** The dump icon: every trash-tagged, unprotected, plain stack of the main area goes into the trash can. */
    public static void dump() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientBackpackView view = ClientBackpackView.get();
        int[] slots = TrashStacks.dumpSlots(view.slotCount(), view::getStackInSlot, view.tags());
        if (slots.length == 0) {
            FloatingPanelEvents.chat(minecraft, Component.translatable("message.mcfloatingbackpack.trash.nothing_to_dump"));
            return;
        }
        int destroyed = TrashDeletePlan.destroyed(view.trash().groups().size(), slots.length);
        if (TrashDeletePlan.needsConfirm(destroyed)) {
            TrashConfirmState.get().open(Kind.DUMP, new int[0], destroyed, minecraft.screen);
            return;
        }
        send(Source.DUMP, false, 0, new int[0]);
    }

    /** Whether the delete icon is usable: the selection holds at least one slot with an item in it. */
    public static boolean deleteEnabled() {
        return selectedSlotsWithItems().length > 0;
    }

    /**
     * The delete icon, or the delete key with a selection: the selected stacks go into the trash can and the selection is
     * cleared. If a confirmation is needed the selection stays: it is cleared when the player says yes (see
     * {@link #sendConfirmed()}), so cancelling leaves everything as it was.
     */
    public static void deleteSelection() {
        int[] slots = selectedSlotsWithItems();
        if (slots.length == 0) {
            return;
        }
        if (deleteMainSlots(slots)) {
            PanelSelection.get().clear();
        }
    }

    /**
     * The stacks in these main-area slots go into the trash can (the delete key over one slot, or a selection).
     *
     * @return true if the request was sent now, false if nothing was done or a confirmation was opened instead
     */
    public static boolean deleteMainSlots(int[] slots) {
        if (slots.length == 0) {
            return false;
        }
        ClientBackpackView view = ClientBackpackView.get();
        int destroyed = TrashDeletePlan.destroyed(view.trash().groups().size(), slots.length);
        if (TrashDeletePlan.needsConfirm(destroyed)) {
            TrashConfirmState.get().open(Kind.MAIN_SLOTS, slots, destroyed, Minecraft.getInstance().screen);
            return false;
        }
        send(Source.MAIN_SLOTS, false, 0, slots);
        return true;
    }

    /** The delete key over a slot of the open vanilla inventory or chest: one stack, so it never needs confirming. */
    public static void deleteMenuSlot(int containerId, int menuSlot) {
        send(Source.MENU_SLOT, false, containerId, new int[] {menuSlot});
    }

    /** The player said yes: send what was waiting, with the confirmed flag. */
    public static void sendConfirmed() {
        TrashConfirmState state = TrashConfirmState.get();
        if (!state.isPending()) {
            return;
        }
        Kind kind = state.kind();
        int[] slots = state.slots();
        state.cancel();
        if (kind == Kind.DUMP) {
            send(Source.DUMP, true, 0, new int[0]);
        } else {
            send(Source.MAIN_SLOTS, true, 0, slots);
            PanelSelection.get().clear();
        }
    }

    private static int[] selectedSlotsWithItems() {
        ClientBackpackView view = ClientBackpackView.get();
        List<Integer> slots = new ArrayList<>();
        for (int slot : PanelSelection.get().snapshot()) {
            ItemStack stack = view.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                slots.add(slot);
            }
        }
        return slots.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void send(Source source, boolean confirmed, int containerId, int[] slots) {
        if (Minecraft.getInstance().player == null) {
            return;
        }
        PacketDistributor.sendToServer(BackpackTrashSendPayload.of(source, confirmed, containerId, slots));
    }
}
