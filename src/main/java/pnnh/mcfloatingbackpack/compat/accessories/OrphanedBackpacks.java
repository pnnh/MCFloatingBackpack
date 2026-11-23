package pnnh.mcfloatingbackpack.compat.accessories;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import io.wispforest.accessories.api.AccessoriesCapability;
import io.wispforest.accessories.api.AccessoriesContainer;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.OrphanRules;
import pnnh.mcfloatingbackpack.compat.ServerCompatLevels;
import pnnh.mcfloatingbackpack.inventory.ContainerMenuAccess;
import pnnh.mcfloatingbackpack.inventory.ContainerToBackpack;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * 062: gives back the floating backpacks that Accessories dropped. When a slot type of Accessories is gone for a player (a data pack
 * changed, a mod was removed, or the start-up switch of 061 was turned off) Accessories does not read what lay in it, and the next save of
 * the player's data writes it away. Until that save the player's data file still has it, so when the data is read ({@link #onPlayerDataRead})
 * what the file shows in Accessories is noted, and a moment after the login ({@link #DELAY_TICKS}: Accessories gives back its own invalid
 * stacks in its first tick, so this comes after it), or when the player leaves (before the data is saved), it is worked out which backpacks
 * lay in a slot type that is gone and are not with the player ({@link OrphanRules}) and they are put in the inventory (or at the feet).
 *
 * <p>Only for a player whose Accessories compatibility is at FULL on this server; whatever goes wrong changes nothing. The mod-specific
 * part is the layout of Accessories' data in the file (read as plain NBT, {@link OrphanedBackpackScan}) and the slot types it gives the
 * player now ({@link AccessoriesCapability}); this class is the only one here that touches Accessories types and is never loaded when
 * Accessories is not.
 */
public final class OrphanedBackpacks {
    /** Accessories gives back its own invalid stacks in its first tick; a second is later than that and earlier than a player can do much. */
    static final int DELAY_TICKS = 20;

    private static final Map<UUID, OrphanedBackpackScan.Scan> PENDING = new ConcurrentHashMap<>();
    /** Players waiting for the check, with the ticks left. Main thread only. */
    private static final Map<UUID, Integer> COUNTDOWN = new HashMap<>();

    private OrphanedBackpacks() {
    }

    /** The player's data file has been read (any thread): what it shows of backpacks in Accessories slots is noted, if there is any. */
    public static void onPlayerDataRead(UUID player, File file) {
        OrphanedBackpackScan.Scan scan = OrphanedBackpackScan.readFile(file.toPath());
        if (scan.entries().isEmpty()) {
            PENDING.remove(player);
        } else {
            PENDING.put(player, scan);
        }
    }

    /** The player has logged in: the check comes {@link #DELAY_TICKS} ticks later. */
    public static void onLogin(ServerPlayer player) {
        if (PENDING.containsKey(player.getUUID())) {
            COUNTDOWN.put(player.getUUID(), DELAY_TICKS);
        }
    }

    /** Once a server tick. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (COUNTDOWN.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, Integer>> it = COUNTDOWN.entrySet().iterator();
        List<UUID> due = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (entry.getValue() <= 1) {
                due.add(entry.getKey());
                it.remove();
            } else {
                entry.setValue(entry.getValue() - 1);
            }
        }
        for (UUID id : due) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                process(player);
            } else {
                PENDING.remove(id);
            }
        }
    }

    /** The player is leaving: if the check has not been made yet it is made now, before the data is saved. */
    public static void onLogout(ServerPlayer player) {
        COUNTDOWN.remove(player.getUUID());
        process(player);
        PENDING.remove(player.getUUID());
    }

    /** The server stops. */
    public static void clear() {
        PENDING.clear();
        COUNTDOWN.clear();
    }

    private static void process(ServerPlayer player) {
        OrphanedBackpackScan.Scan scan = PENDING.remove(player.getUUID());
        if (scan == null || scan.entries().isEmpty()) {
            return;
        }
        try {
            if (!ServerCompatLevels.specificAdaptation("accessories")) {
                return;
            }
            AccessoriesCapability capability = AccessoriesCapability.get(player);
            if (capability == null) {
                return;
            }
            Set<String> validTypes = new HashSet<>(capability.getContainers().keySet());
            Set<UUID> identities = new HashSet<>();
            Map<String, Integer> unidentified = new HashMap<>();
            collectLive(player, capability, identities, unidentified);
            List<OrphanRules.Entry> entries = new ArrayList<>();
            for (OrphanedBackpackScan.Found found : scan.entries()) {
                entries.add(new OrphanRules.Entry(found.slotName(), found.itemId(), found.identity()));
            }
            List<Integer> back = OrphanRules.decide(entries, validTypes, identities, scan.unidentified(), unidentified);
            if (back.isEmpty()) {
                return;
            }
            giveBack(player, scan, back);
        } catch (RuntimeException | LinkageError e) {
            MCFloatingBackpack.LOGGER.warn("Could not check {}'s backpacks in slots of Accessories that no longer exist; nothing was changed", player.getGameProfile().getName(), e);
        }
    }

    private static void giveBack(ServerPlayer player, OrphanedBackpackScan.Scan scan, List<Integer> positions) {
        ContainerToBackpack.Fallback<ItemStack> fallback = new ContainerMenuAccess(player).fallback();
        int given = 0;
        int atFeet = 0;
        for (int position : positions) {
            OrphanedBackpackScan.Found found = scan.entries().get(position);
            CompoundTag tag = found.stack().copy();
            tag.remove("Slot");
            ItemStack stack = ItemStack.parseOptional(player.registryAccess(), tag);
            if (stack.isEmpty()) {
                MCFloatingBackpack.LOGGER.warn("A backpack of {} in the vanished Accessories slot '{}' could not be read back and was not returned: {}",
                        player.getGameProfile().getName(), found.slotName(), found.itemId());
                continue;
            }
            ItemStack rest = fallback.toInventory(stack.copy());
            if (!rest.isEmpty()) {
                fallback.drop(rest);
                atFeet++;
            }
            given++;
            MCFloatingBackpack.LOGGER.info("Gave back to {} a {} (number {}) that lay in the {} slot '{}' of Accessories, which no longer exists for them: {}",
                    player.getGameProfile().getName(), found.itemId(), found.identity() == null ? "none" : found.identity(),
                    found.cosmetic() ? "cosmetic" : "real", found.slotName(), rest.isEmpty() ? "in the inventory" : "at their feet");
        }
        if (given > 0) {
            player.sendSystemMessage(atFeet == 0
                    ? Component.translatable("message.mcfloatingbackpack.orphaned.returned", given)
                    : Component.translatable("message.mcfloatingbackpack.orphaned.dropped", given, atFeet));
        }
    }

    /** What the player has now, over the places the scan counted: inventory, cursor, Accessories slots, and the ground near them. */
    private static void collectLive(ServerPlayer player, AccessoriesCapability capability, Set<UUID> identities, Map<String, Integer> unidentified) {
        var inventory = player.getInventory();
        for (ItemStack stack : inventory.items) {
            note(stack, identities, unidentified);
        }
        for (ItemStack stack : inventory.armor) {
            note(stack, identities, unidentified);
        }
        for (ItemStack stack : inventory.offhand) {
            note(stack, identities, unidentified);
        }
        note(player.containerMenu.getCarried(), identities, unidentified);
        if (player.inventoryMenu != player.containerMenu) {
            note(player.inventoryMenu.getCarried(), identities, unidentified);
        }
        for (AccessoriesContainer container : capability.getContainers().values()) {
            noteAll(container.getAccessories(), identities, unidentified);
            noteAll(container.getCosmeticAccessories(), identities, unidentified);
        }
        for (ItemEntity lying : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(16))) {
            note(lying.getItem(), identities, unidentified);
        }
    }

    private static void noteAll(Container container, Set<UUID> identities, Map<String, Integer> unidentified) {
        for (int i = 0; i < container.getContainerSize(); i++) {
            note(container.getItem(i), identities, unidentified);
        }
    }

    private static void note(ItemStack stack, Set<UUID> identities, Map<String, Integer> unidentified) {
        if (stack.isEmpty() || !FloatingBackpackItem.isBackpack(stack)) {
            return;
        }
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        if (identity != null) {
            identities.add(identity.uuid());
        } else {
            unidentified.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), 1, Integer::sum);
        }
    }
}
