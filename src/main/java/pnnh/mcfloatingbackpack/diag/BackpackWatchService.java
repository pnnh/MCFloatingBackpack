package pnnh.mcfloatingbackpack.diag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.diag.DiagLog.Side;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Once a second, for every player: where are their backpacks? A backpack that was in the inventory, on the cursor or in an open
 * container a second ago and is nowhere now (not lying on the ground nearby either) is reported as a warning, together with
 * the player's last panel actions, so that a backpack that vanishes without any error can be traced afterwards. A backpack that
 * was only dropped is an INFO line. Looks at the vanilla inventory, the cursor, the open container and the backpack the
 * panel resolves (hand, chest, Curios, Accessories); a backpack in an ender chest or another container the player is not looking
 * into is reported as gone too, which is why this is a lead and not a verdict.
 */
public final class BackpackWatchService {
    private static final Map<UUID, BackpackWatch<UUID>> WATCHES = new HashMap<>();

    private BackpackWatchService() {
    }

    public static void tick(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            try {
                check(player);
            } catch (RuntimeException e) {
                DiagLog.log(Side.SERVER, LogLevel.ERROR, "Watching the backpacks of {} failed", player.getGameProfile().getName(), e);
            }
        }
    }

    public static void forget(UUID player) {
        WATCHES.remove(player);
        DiagLog.forget(player);
    }

    private static UUID identityOf(ItemStack stack) {
        if (stack.isEmpty() || !FloatingBackpackItem.isBackpack(stack)) {
            return null;
        }
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        return identity == null ? null : identity.uuid();
    }

    private static void note(Map<UUID, String> now, ItemStack stack, String where) {
        UUID id = identityOf(stack);
        if (id != null) {
            now.putIfAbsent(id, where);
        }
    }

    private static Map<UUID, String> whereAreTheBackpacks(ServerPlayer player) {
        Map<UUID, String> now = new HashMap<>();
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.items.size(); i++) {
            String where = i < 9 ? "hotbar " + i + (i == inventory.selected ? " (selected)" : "") : "inventory " + i;
            note(now, inventory.items.get(i), where);
        }
        for (int i = 0; i < inventory.armor.size(); i++) {
            note(now, inventory.armor.get(i), "armor " + i);
        }
        for (int i = 0; i < inventory.offhand.size(); i++) {
            note(now, inventory.offhand.get(i), "offhand");
        }
        note(now, player.containerMenu.getCarried(), "cursor of the open menu");
        note(now, player.inventoryMenu.getCarried(), "cursor of the inventory menu");
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != inventory) {
                note(now, slot.getItem(), "open container slot " + slot.index);
            }
        }
        BackpackRef resolved = BackpackRef.resolve(player);
        if (resolved != null) {
            note(now, resolved.getStack(player), "worn or held: " + resolved.source());
        }
        return now;
    }

    private static void check(ServerPlayer player) {
        BackpackWatch<UUID> watch = WATCHES.computeIfAbsent(player.getUUID(), id -> new BackpackWatch<>());
        List<BackpackWatch.Gone<UUID>> gone = watch.update(whereAreTheBackpacks(player));
        for (BackpackWatch.Gone<UUID> backpack : gone) {
            boolean lying = !player.level()
                    .getEntitiesOfClass(
                            ItemEntity.class,
                            player.getBoundingBox().inflate(16),
                            entity -> backpack.id().equals(identityOf(entity.getItem())))
                    .isEmpty();
            String name = player.getGameProfile().getName();
            if (lying) {
                DiagLog.log(Side.SERVER, LogLevel.INFO, "Backpack #{} of {} (was at {}) was dropped: it lies within 16 blocks", shortId(backpack.id()), name, backpack.lastSeenAt());
                continue;
            }
            DiagLog.log(
                    Side.SERVER,
                    LogLevel.WARN,
                    "Backpack #{} of {} (was at {}) is gone: not in the inventory, on a cursor, in the open container, worn, or lying within 16 blocks."
                            + " Game mode {}, open menu {}.",
                    shortId(backpack.id()),
                    name,
                    backpack.lastSeenAt(),
                    player.gameMode.getGameModeForPlayer(),
                    player.containerMenu.getClass().getSimpleName());
            List<String> events = DiagLog.dump(player.getUUID());
            DiagLog.log(Side.SERVER, LogLevel.WARN, "  The last {} panel actions of {}, oldest first:", events.size(), name);
            for (String event : events) {
                DiagLog.log(Side.SERVER, LogLevel.WARN, "    {}", event);
            }
        }
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }
}
