package pnnh.mcfloatingbackpack.network;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.combined.AllState;

/**
 * 066: what the server sends the client for the "all" tab, and what it remembers it sent (so that only a member that really changed is sent
 * again). One packet for each member, each no bigger than a backpack's own full sync.
 */
public final class AllSync {
    private AllSync() {
    }

    /** Sends member {@code index} and remembers what was sent. False if the packet is over the limit (nothing was sent). */
    public static boolean sendMember(ServerPlayer player, AllState all, int index, BackpackInventory inventory) {
        AllState.Member member = all.members().get(index);
        List<ItemStack> slots = new ArrayList<>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            slots.add(inventory.getStackInSlot(i).copy());
        }
        CombinedMemberSyncPayload payload = new CombinedMemberSyncPayload(
                all.epoch(), all.members().size(), index, slots, inventory.rowView(), inventory.tags(), all.cursor().copy());
        if (!BackpackNetwork.withinLimit(player, CombinedMemberSyncPayload.STREAM_CODEC, payload, "combined member sync")) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, payload);
        markSent(player, member, slots, inventory);
        return true;
    }

    /** Sends every member, in order. False if one could not be sent. */
    public static boolean sendAll(ServerPlayer player, AllState all) {
        for (int i = 0; i < all.members().size(); i++) {
            BackpackInventory inventory = all.members().get(i).ref.loadInventory(player);
            if (!sendMember(player, all, i, inventory)) {
                return false;
            }
        }
        return true;
    }

    /** One cell changed (or only the cursor: {@code flat} -1). */
    public static void sendCell(ServerPlayer player, AllState all, int flat, ItemStack stack) {
        PacketDistributor.sendToPlayer(
                player, new CombinedCellUpdatePayload(all.epoch(), flat, stack.copy(), all.cursor().copy()));
    }

    /** What the client now shows of this member: the contents object of its stack and the main area, rows and tags. */
    public static void markSent(ServerPlayer player, AllState.Member member, List<ItemStack> slots, BackpackInventory inventory) {
        ItemStack stack = member.ref.getStack(player);
        member.lastContents = stack.get(ModDataComponents.BACKPACK_CONTENTS.get());
        member.lastMain = slots;
        member.lastRows = inventory.rowView();
        member.lastTags = inventory.tags();
    }
}
