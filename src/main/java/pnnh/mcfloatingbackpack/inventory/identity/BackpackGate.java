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
    private BackpackGate() {
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
