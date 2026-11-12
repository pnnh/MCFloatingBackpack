package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 047: the order of two kinds of item when a backpack is tidied: by registry name, the namespace {@code minecraft} first, then the
 * other namespaces alphabetically, then the path alphabetically; two kinds with the same registry name (the same item with different
 * data components) by a written form of their components that does not depend on the order the patch lists them in. The count
 * plays no part: the stacks of one kind are put together by {@link BackpackSorter}.
 */
public final class ItemSortOrder implements Comparator<ItemStack> {
    public static final ItemSortOrder INSTANCE = new ItemSortOrder();

    private static final String VANILLA = "minecraft";

    private ItemSortOrder() {
    }

    @Override
    public int compare(ItemStack a, ItemStack b) {
        ResourceLocation idA = BuiltInRegistries.ITEM.getKey(a.getItem());
        ResourceLocation idB = BuiltInRegistries.ITEM.getKey(b.getItem());
        boolean vanillaA = VANILLA.equals(idA.getNamespace());
        boolean vanillaB = VANILLA.equals(idB.getNamespace());
        if (vanillaA != vanillaB) {
            return vanillaA ? -1 : 1;
        }
        int byNamespace = idA.getNamespace().compareTo(idB.getNamespace());
        if (byNamespace != 0) {
            return byNamespace;
        }
        int byPath = idA.getPath().compareTo(idB.getPath());
        if (byPath != 0) {
            return byPath;
        }
        return componentKey(a).compareTo(componentKey(b));
    }

    /** The data components that differ from the item's defaults, written in the order of their registry names. */
    static String componentKey(ItemStack stack) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<DataComponentType<?>, java.util.Optional<?>> entry : stack.getComponentsPatch().entrySet()) {
            ResourceLocation type = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(entry.getKey());
            parts.add((type == null ? "?" : type.toString()) + "=" + entry.getValue().map(Object::toString).orElse("removed"));
        }
        parts.sort(Comparator.naturalOrder());
        return String.join(";", parts);
    }
}
