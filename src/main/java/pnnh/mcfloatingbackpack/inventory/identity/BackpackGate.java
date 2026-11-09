package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.identity.NoticeLimiter.Reason;
import pnnh.mcfloatingbackpack.inventory.identity.RegistryRecord.State;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 026: the one way into a backpack's contents on the server. Every request that reads or changes a backpack (opening the panel,
 * clicks, transfers, tags, the trash can, the auto pickup...) asks {@link #acquire} first. A backpack that has no identity yet
 * gets one (an internal id, from which its id is worked out), and the registry, an index kept as well as it can be, notes it:
 * who last used it, its capacity, when, and a shadow copy of its contents. Nothing is limited and copies are not told apart: a
 * copy of a stack shares its identity. The only refusal is a backpack an administrator has deleted (a tombstone), which
 * returns nothing and changes nothing. {@link #afterSave} keeps the index current after a save.
 *
 * <p>Only the server's main thread calls this.
 */
public final class BackpackGate {
    /** The record's last-touched time is refreshed at most this often by plain use (saving always refreshes it). */
    private static final long TOUCH_INTERVAL_MS = 60_000L;

    private BackpackGate() {
    }

    /** @return the backpack's contents for the request to work on, or empty if the request must be refused */
    public static Optional<BackpackInventory> acquire(ServerPlayer player, BackpackRef ref) {
        ItemStack stack = ref.getStack(player);
        if (!FloatingBackpackItem.isBackpack(stack)) {
            return Optional.empty();
        }
        BackpackRegistry registry = BackpackRegistry.get(player.server);
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        RegistryRecord record = identity == null ? null : registry.index().get(identity.uuid());
        if (record != null && record.state() == State.DELETED) {
            MCFloatingBackpack.LOGGER.info(
                    "Refused {} for {}: backpack {} was deleted", ref, player.getGameProfile().getName(), identity.id());
            BackpackMessages.chatAndActionBar(
                    player, Reason.TOMBSTONE, Component.translatable("message.mcfloatingbackpack.id.tombstone"));
            return Optional.empty();
        }

        int capacity = BackpackInventory.capacityOf(stack);
        long now = System.currentTimeMillis();
        boolean changedStack = false;
        ItemStack updated = stack.copy();
        if (identity == null) {
            identity = new BackpackIdentity(UUID.randomUUID());
            updated.set(ModDataComponents.BACKPACK_IDENTITY.get(), identity);
            changedStack = true;
        }
        BackpackContents contents = updated.getOrDefault(ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty(capacity));
        if (record == null) {
            registry.register(new RegistryRecord(identity.uuid(), player.getUUID(), capacity, State.NORMAL, now, now), contents);
        } else {
            note(registry, record, player, capacity, contents, now, false);
        }
        if (changedStack) {
            ref.setStack(player, updated);
        }
        return Optional.of(BackpackInventory.fromItem(ref.getStack(player)));
    }

    /**
     * The stack has just been saved with new contents: bring the registry's record, and its shadow copy (the very object the
     * stack now holds), up to date; a backpack the registry does not know yet is noted. Nothing happens for a stack without
     * identity or on the client.
     */
    public static void afterSave(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        if (identity == null) {
            return;
        }
        BackpackRegistry registry = BackpackRegistry.get(serverPlayer.server);
        RegistryRecord record = registry.index().get(identity.uuid());
        int capacity = BackpackInventory.capacityOf(stack);
        BackpackContents contents = stack.get(ModDataComponents.BACKPACK_CONTENTS.get());
        long now = System.currentTimeMillis();
        if (record == null) {
            registry.register(new RegistryRecord(identity.uuid(), player.getUUID(), capacity, State.NORMAL, now, now), contents);
        } else if (record.state() != State.DELETED) {
            note(registry, record, serverPlayer, capacity, contents, now, true);
        }
    }

    /** Brings the record up to date with who is using the backpack, its capacity and, when asked, its contents. */
    private static void note(
            BackpackRegistry registry,
            RegistryRecord record,
            ServerPlayer player,
            int capacity,
            BackpackContents contents,
            long now,
            boolean saved) {
        boolean ownerChanged = !record.owner().equals(player.getUUID());
        boolean capacityChanged = record.capacity() != capacity;
        if (ownerChanged || capacityChanged || saved || now - record.touchedAt() > TOUCH_INTERVAL_MS) {
            registry.update(record.uuid(), r -> r.withOwner(player.getUUID()).withCapacity(capacity).withTouchedAt(now));
        }
        if (saved || capacityChanged) {
            registry.setShadow(record.uuid(), contents);
        }
    }
}
