package pnnh.mcfloatingbackpack.inventory.pickup;

import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.pickup.AutoPickupDecision.Outcome;
import pnnh.mcfloatingbackpack.network.BackpackSlotUpdatePayload;

/**
 * 024: the auto pickup on the server. It listens to {@code ItemEntityPickupEvent.Pre} (the event Sophisticated Backpacks and
 * Traveler's Backpack use too, which is where "fighting over the same item" happens) at low priority, so those run first.
 *
 * <p>Other backpack mods are not detected or avoided: they run first, and this stands aside whenever the event was already
 * settled ({@code canPickup()} is FALSE) or the stack was already shrunk; what they leave is picked up here (nothing is
 * copied or lost, but which backpack gets how much is up to the order they run in).
 *
 * <p>Only the player's <em>active</em> backpack counts: the one the panel key would open ({@link BackpackRef#resolve}, worked
 * out afresh each time, no session state), and only if its left area holds an auto pickup. Whatever the panel is doing is
 * irrelevant. What is decided for an item (see {@link AutoPickupDecision}):
 *
 * <ul>
 *   <li>VANILLA: nothing here; vanilla puts it in the inventory.
 *   <li>BLOCK: blacklisted; {@code setCanPickup(FALSE)}, so it stays on the ground and goes nowhere.
 *   <li>TAKE: into the backpack's main area. What does not fit is left in the item entity and the event is left at its
 *       default, so vanilla puts the rest into the inventory, and whatever does not fit there stays on the ground.
 * </ul>
 *
 * <p>{@code setCanPickup(TRUE)} cannot be used to "pick up elsewhere": the game still calls {@code Inventory.add} after it.
 * So the pickup is done here (move, shrink the entity, take animation and sound, statistics) and, when the whole stack went
 * in, the entity is discarded and the event set to FALSE. {@code ItemEntityPickupEvent.Post} is deliberately not fired: it is
 * documented as "after the item was added to the player's inventory", which is not what happened. The backpack is saved
 * before the entity is shrunk, so a failure in between loses nothing.
 */
public final class AutoPickupEvents {
    private AutoPickupEvents() {
    }

    public static void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.canPickup().isFalse() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemEntity entity = event.getItemEntity();
        ItemStack stack = entity.getItem();
        if (stack.isEmpty() || entity.hasPickUpDelay()) {
            return;
        }
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null) {
            return;
        }
        // The cheap check first: the stored record, not a working copy of the whole backpack.
        BackpackContents contents = ref.getStack(player).get(ModDataComponents.BACKPACK_CONTENTS.get());
        if (contents == null || !contents.hasAutoPickup()) {
            return;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        boolean blacklisted = contents.pickupBlacklist().matches(contents.tags().tagsOf(itemId));
        Outcome outcome = AutoPickupDecision.decide(true, blacklisted);
        switch (outcome) {
            case VANILLA -> {
            }
            case BLOCK -> event.setCanPickup(TriState.FALSE);
            case TAKE -> take(event, player, entity, ref);
        }
    }

    private static void take(ItemEntityPickupEvent.Pre event, ServerPlayer player, ItemEntity entity, BackpackRef ref) {
        // Vanilla's own conditions (the pickup delay was checked by the caller): who the item is meant for.
        UUID owner = entity.getTarget();
        if (owner != null && !owner.equals(player.getUUID())) {
            return;
        }
        ItemStack stack = entity.getItem();
        PickupInsert.Result<ItemStack> result;
        BackpackInventory inventory;
        try {
            if (!ref.stillValid(player)) {
                return;
            }
            // 026: the registry decides first; a refused backpack (over the limit, deleted...) does not take pickups.
            java.util.Optional<BackpackInventory> acquired = BackpackGate.acquire(player, ref);
            if (acquired.isEmpty()) {
                // 040: a backpack saved by a newer version takes no pickups; the item goes the vanilla way. Only a line in the log.
                BackpackGate.noteAutoPickupSkipped(player, ref);
                return;
            }
            inventory = acquired.get();
            result = PickupInsert.insert(
                    ItemStackTransferOps.INSTANCE,
                    stack,
                    inventory.mainTargetOrder(stack),
                    index -> inventory.getStackInSlot(index),
                    (written, index) -> inventory.setStackInSlot(index, written));
            if (result.moved() == 0) {
                return;
            }
            ref.saveInventory(player, inventory);
        } catch (RuntimeException e) {
            MCFloatingBackpack.LOGGER.error("Auto pickup failed, leaving the item to vanilla", e);
            return;
        }

        int moved = result.moved();
        Item item = stack.getItem();
        stack.shrink(moved);
        for (int index : result.changedSlots()) {
            PacketDistributor.sendToPlayer(
                    player,
                    new BackpackSlotUpdatePayload(
                            PanelArea.MAIN.toByte(),
                            index,
                            inventory.getStackInSlot(index).copy(),
                            inventory.getCursor().copy()));
        }
        player.take(entity, moved);
        player.awardStat(Stats.ITEM_PICKED_UP.get(item), moved);
        player.onItemPickup(entity);
        if (stack.isEmpty()) {
            entity.discard();
            event.setCanPickup(TriState.FALSE);
        }
    }
}
