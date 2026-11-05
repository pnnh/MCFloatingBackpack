package pnnh.mcfloatingbackpack.inventory.trash;

import java.util.Collection;

/**
 * 022: whether a stack differs from the default stack of its item only by wear. The game records exactly the data
 * components that differ from the item's defaults (the stack's component patch), so "only wear" is "the patch names
 * nothing but the damage component"; enchantments, a custom name, container contents, potions, banner patterns, heads,
 * maps, book text and any component a later version adds all show up there without being listed here. Pure logic: the
 * caller hands in the registry names of the patched components.
 */
public final class StackExtraData {
    public static final String DAMAGE = "minecraft:damage";

    private StackExtraData() {
    }

    public static boolean onlyDamage(Collection<String> patchedComponentIds) {
        if (patchedComponentIds == null) {
            return true;
        }
        for (String id : patchedComponentIds) {
            if (!DAMAGE.equals(id)) {
                return false;
            }
        }
        return true;
    }
}
