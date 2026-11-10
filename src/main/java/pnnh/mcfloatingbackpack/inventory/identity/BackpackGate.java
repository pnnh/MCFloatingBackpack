package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;
import pnnh.mcfloatingbackpack.snapshot.SnapshotService;

/**
 * 026, 030: the one way into a backpack's contents on the server. Every request that reads or changes a backpack (opening the
 * panel, clicks, transfers, tags, the trash can, the auto pickup...) asks {@link #acquire} first. A backpack that has no
 * identity yet gets one (an internal id, from which its id is worked out). That is all it does to the backpack: nothing is limited,
 * nothing is refused, and copies of a stack share their identity.
 *
 * <p>Since 030 the gate also tells the snapshot service about the backpack (the first time it is used in a run, and every time
 * it is saved), so a snapshot of it is written outside the world a little later. There is no registry in the world any more.
 *
 * <p>Only the server's main thread calls this.
 */
public final class BackpackGate {
    /** Which backpack each player's panel was opened on; see {@link OpenSessions}. */
    private static final OpenSessions<UUID, UUID> SESSIONS = new OpenSessions<>();
    /** The server tick of the last resync after a stale view, per player. */
    private static final java.util.Map<UUID, Integer> RESYNCED = new java.util.HashMap<>();

    private BackpackGate() {
    }

    private static UUID identityOf(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !FloatingBackpackItem.isBackpack(stack)) {
            return null;
        }
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        return identity == null ? null : identity.uuid();
    }

    /** The panel has just been opened on (or re-synced to) this stack. */
    public static void noteOpened(ServerPlayer player, ItemStack stack) {
        SESSIONS.opened(player.getUUID(), identityOf(stack));
    }

    public static void noteClosed(UUID player) {
        SESSIONS.closed(player);
    }

    public static void forget(UUID player) {
        SESSIONS.closed(player);
        RESYNCED.remove(player);
    }

    /** Whether a stale view was re-synced in this very tick, so that the refusal which follows must not close the panel too. */
    public static boolean justResynced(ServerPlayer player) {
        Integer tick = RESYNCED.get(player.getUUID());
        return tick != null && tick == player.server.getTickCount();
    }

    /**
     * {@link #acquire} for a request that comes from the open panel. If the backpack at the place the request names is not the one
     * the panel was opened on (another backpack was put there since), nothing is touched: the panel is re-synced to the backpack
     * that is there now and the result is empty, so the click that was meant for the picture of the old one cannot change the new
     * one. The callers treat empty as "refused", and {@link #justResynced} keeps that from closing the panel again.
     */
    public static Optional<BackpackInventory> acquireForPanel(ServerPlayer player, BackpackRef ref) {
        if (SESSIONS.check(player.getUUID(), identityOf(ref.getStack(player))) == OpenSessions.Verdict.STALE) {
            resync(player, ref);
            return Optional.empty();
        }
        return acquire(player, ref);
    }

    /** The panel shows another backpack than the one that is now where it looked: show the new one. */
    private static void resync(ServerPlayer player, BackpackRef ref) {
        pnnh.mcfloatingbackpack.diag.DiagLog.record(
                player.getUUID(), "stale panel: it was opened on another backpack than " + pnnh.mcfloatingbackpack.diag.DiagLog.stack(ref.getStack(player))
                        + " at " + ref + "; the request is refused and the panel is re-synced to the one that is there");
        if (acquire(player, ref).isPresent()) {
            pnnh.mcfloatingbackpack.network.BackpackNetwork.sendSession(player, true, ref);
        } else {
            pnnh.mcfloatingbackpack.network.BackpackNetwork.sendSession(player, false, null);
        }
        RESYNCED.put(player.getUUID(), player.server.getTickCount());
    }

    /**
     * Once a second for a player whose panel is open: if the backpack the panel finds now is not the one it was opened on, show
     * the new one without waiting for a click; if there is none any more, close the panel.
     */
    public static void tickOpenSession(ServerPlayer player) {
        if (!SESSIONS.has(player.getUUID())) {
            return;
        }
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null) {
            pnnh.mcfloatingbackpack.network.BackpackNetwork.sendSession(player, false, null);
            return;
        }
        if (SESSIONS.check(player.getUUID(), identityOf(ref.getStack(player))) == OpenSessions.Verdict.STALE) {
            resync(player, ref);
        }
    }

    /** @return the backpack's contents for the request to work on, or empty if the stack is not a backpack any more */
    public static Optional<BackpackInventory> acquire(ServerPlayer player, BackpackRef ref) {
        ItemStack stack = ref.getStack(player);
        if (!FloatingBackpackItem.isBackpack(stack)) {
            return Optional.empty();
        }
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        boolean changedStack = false;
        ItemStack updated = stack;
        if (identity == null) {
            identity = new BackpackIdentity(UUID.randomUUID());
            updated = stack.copy();
            updated.set(ModDataComponents.BACKPACK_IDENTITY.get(), identity);
            changedStack = true;
        }
        int capacity = BackpackInventory.capacityOf(stack);
        BackpackContents contents = updated.getOrDefault(ModDataComponents.BACKPACK_CONTENTS.get(), BackpackContents.empty(capacity));
        UUID uuid = identity.uuid();
        SnapshotService.get().ifPresent(service -> service.noteUse(player, uuid, capacity, contents));
        if (changedStack) {
            ref.setStack(player, updated);
        }
        return Optional.of(BackpackInventory.fromItem(ref.getStack(player)));
    }

    /**
     * The stack has just been saved with new contents: tell the snapshot service. Nothing happens for a stack without identity
     * or on the client.
     */
    public static void afterSave(Player player, ItemStack stack) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        if (identity == null) {
            return;
        }
        BackpackContents contents = stack.get(ModDataComponents.BACKPACK_CONTENTS.get());
        int capacity = BackpackInventory.capacityOf(stack);
        SnapshotService.get().ifPresent(service -> service.noteSaved(serverPlayer, identity.uuid(), capacity, contents));
    }
}
