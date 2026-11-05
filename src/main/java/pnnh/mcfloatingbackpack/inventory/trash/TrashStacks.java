package pnnh.mcfloatingbackpack.inventory.trash;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.item.ModTags;

/**
 * 022: the part of the dump rules that needs real stacks. The server uses it to decide what to take, the client to
 * count what a dump would take (so it can ask for confirmation first); both go through {@link TrashDump}.
 */
public final class TrashStacks {
    private TrashStacks() {
    }

    /** The main-area slots a dump takes, ascending. */
    public static int[] dumpSlots(int size, IntFunction<ItemStack> stackAt, ItemTags tags) {
        return TrashDump.select(
                size,
                stackAt,
                ItemStack::isEmpty,
                stack -> hasTrashTag(tags, stack),
                TrashStacks::isProtected,
                TrashStacks::onlyDefaultData);
    }

    /** Whether the stack's item category (its registry id) carries the trash tag in this backpack. */
    public static boolean hasTrashTag(ItemTags tags, ItemStack stack) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return tags.tagsOf(id).contains(TagProtection.TRASH);
    }

    /** Whether the item is on the protection list (the {@code never_trash} item tag). */
    public static boolean isProtected(ItemStack stack) {
        return stack.is(ModTags.NEVER_TRASH);
    }

    /** Whether the stack differs from the default stack of its item by wear at most. */
    public static boolean onlyDefaultData(ItemStack stack) {
        List<String> ids = new ArrayList<>();
        for (DataComponentType<?> type : stack.getComponentsPatch().entrySet().stream().map(java.util.Map.Entry::getKey).toList()) {
            ResourceLocation key = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            ids.add(key == null ? "?" : key.toString());
        }
        return StackExtraData.onlyDamage(ids);
    }
}
