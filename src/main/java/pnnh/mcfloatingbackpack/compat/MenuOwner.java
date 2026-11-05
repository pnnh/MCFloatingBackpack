package pnnh.mcfloatingbackpack.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;

/**
 * 020: which mod a menu belongs to, for the compatibility setting. A menu type is registered under its mod's
 * namespace, so that namespace is the owner. A menu with no type (the player's own inventory, the creative
 * inventory's), one that is not registered, or no menu at all counts as vanilla, and so does a mod that opens a
 * vanilla menu type. Works on both sides.
 */
public final class MenuOwner {
    private MenuOwner() {
    }

    public static String ownerOf(AbstractContainerMenu menu) {
        if (menu == null) {
            return CompatPolicy.VANILLA;
        }
        try {
            MenuType<?> type = menu.getType();
            ResourceLocation key = type == null ? null : BuiltInRegistries.MENU.getKey(type);
            return key == null ? CompatPolicy.VANILLA : key.getNamespace();
        } catch (UnsupportedOperationException noType) {
            return CompatPolicy.VANILLA;
        }
    }
}
