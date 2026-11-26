package pnnh.mcfloatingbackpack.inventory.quick;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;

/**
 * 067: the main-area id list {@link QuickCandidates} selects from. Slot {@code i} of the working inventory (tier cells, then
 * visible extension rows) is index {@code i}. Hidden rows, tool areas and the cursor are not part of {@link BackpackInventory#size()}.
 */
public final class QuickLists {
    private QuickLists() {
    }

    public static List<Integer> candidates(BackpackInventory inventory) {
        if (inventory == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>(inventory.size());
        for (int i = 0; i < inventory.size(); i++) {
            ids.add(idOf(inventory.getStackInSlot(i)));
        }
        ItemTags tags = inventory.tags();
        return QuickCandidates.select(inventory.baseCapacity(), ids, id -> tagged(tags, id));
    }

    public static boolean tagged(ItemTags tags, String id) {
        if (tags == null || id == null || id.isEmpty()) {
            return false;
        }
        ResourceLocation name = ResourceLocation.tryParse(id);
        return name != null && tags.tagsOf(name).contains(QuickCandidates.TAG);
    }

    private static String idOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
