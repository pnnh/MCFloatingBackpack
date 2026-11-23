package pnnh.mcfloatingbackpack.compat.accessories;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;

/**
 * 062: reads the floating backpacks out of a player's saved data (the NBT of the player data file), in the Accessories part of it. Only
 * plain NBT is used: {@code neoforge:attachments} / {@code accessories:inventory_holder} / {@code accessories_containers}, one entry for
 * each slot type with a list of real stacks ({@code items}) and of cosmetic ones ({@code cosmetics}); no type of Accessories is needed, so
 * this class is safe whatever version of it is there, and whatever is not as expected is read as "nothing there", never as an error.
 * Also counts the backpacks without a number in the player's inventory, to compare with what the player has later ({@link
 * pnnh.mcfloatingbackpack.compat.OrphanRules}).
 */
final class OrphanedBackpackScan {
    /** The five backpack items. */
    static final Set<String> BACKPACKS = Set.of(
            "mcfloatingbackpack:leather_floating_backpack",
            "mcfloatingbackpack:iron_floating_backpack",
            "mcfloatingbackpack:gold_floating_backpack",
            "mcfloatingbackpack:diamond_floating_backpack",
            "mcfloatingbackpack:netherite_floating_backpack");

    /** One backpack in an Accessories slot type: where it lay, what it is, its number if it has one, and its whole NBT. */
    record Found(String slotName, boolean cosmetic, String itemId, UUID identity, CompoundTag stack) {
    }

    /**
     * @param entries every backpack in every Accessories slot type of the saved data
     * @param unidentified per item id, how many backpacks without a number the saved data has in the inventory and in all Accessories slots
     */
    record Scan(List<Found> entries, Map<String, Integer> unidentified) {
        static final Scan EMPTY = new Scan(List.of(), Map.of());
    }

    private OrphanedBackpackScan() {
    }

    /** Reads the file; an unreadable file is an empty scan. */
    static Scan readFile(Path file) {
        try {
            return read(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
        } catch (IOException | RuntimeException e) {
            return Scan.EMPTY;
        }
    }

    static Scan read(CompoundTag root) {
        try {
            List<Found> found = new ArrayList<>();
            Map<String, Integer> unidentified = new HashMap<>();
            for (Tag entry : list(root, "Inventory")) {
                if (entry instanceof CompoundTag stack) {
                    countUnidentified(stack, unidentified);
                }
            }
            CompoundTag containers = compound(compound(compound(root, "neoforge:attachments"), "accessories:inventory_holder"), "accessories_containers");
            for (String slotName : containers.getAllKeys()) {
                CompoundTag container = compound(containers, slotName);
                for (String key : new String[] {"items", "cosmetics"}) {
                    for (Tag entry : list(container, key)) {
                        if (entry instanceof CompoundTag stack && BACKPACKS.contains(stack.getString("id"))) {
                            found.add(new Found(slotName, key.equals("cosmetics"), stack.getString("id"), identityOf(stack), stack));
                            countUnidentified(stack, unidentified);
                        }
                    }
                }
            }
            return new Scan(List.copyOf(found), Map.copyOf(unidentified));
        } catch (RuntimeException e) {
            return Scan.EMPTY;
        }
    }

    private static void countUnidentified(CompoundTag stack, Map<String, Integer> unidentified) {
        String id = stack.getString("id");
        if (BACKPACKS.contains(id) && identityOf(stack) == null) {
            unidentified.merge(id, 1, Integer::sum);
        }
    }

    /** The number of a backpack: {@code components} / {@code mcfloatingbackpack:backpack_identity} / {@code uuid} (four ints); null if it has none. */
    static UUID identityOf(CompoundTag stack) {
        CompoundTag identity = compound(compound(stack, "components"), "mcfloatingbackpack:backpack_identity");
        if (identity.get("uuid") instanceof IntArrayTag ints && ints.getAsIntArray().length == 4) {
            int[] a = ints.getAsIntArray();
            return new UUID(((long) a[0] << 32) | (a[1] & 0xFFFFFFFFL), ((long) a[2] << 32) | (a[3] & 0xFFFFFFFFL));
        }
        return null;
    }

    private static CompoundTag compound(CompoundTag parent, String key) {
        return parent.contains(key, Tag.TAG_COMPOUND) ? parent.getCompound(key) : new CompoundTag();
    }

    private static ListTag list(CompoundTag parent, String key) {
        return parent.contains(key, Tag.TAG_LIST) ? parent.getList(key, Tag.TAG_COMPOUND) : new ListTag();
    }
}
