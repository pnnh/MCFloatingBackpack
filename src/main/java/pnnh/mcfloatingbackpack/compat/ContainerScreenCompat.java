package pnnh.mcfloatingbackpack.compat;

import java.util.Locale;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Soft detection for container GUIs used with the floating panel cursor path.
 * No compile-time references to third-party mods.
 */
public final class ContainerScreenCompat {
    public enum CompatTarget {
        NONE,
        VANILLA_CONTAINER,
        SOPHISTICATED,
        TRAVELERS,
        UNKNOWN_CONTAINER
    }

    private ContainerScreenCompat() {
    }

    /**
     * True when the floating panel should use the open menu's carried stack: a container screen, unless the mod that
     * owns it is set to OFF in the client config (020), in which case it counts as any other screen and the panel stays
     * out of it. Vanilla screens are never turned off.
     */
    @OnlyIn(Dist.CLIENT)
    public static boolean isOpenContainerScreen(Screen screen) {
        String owner = ownerOf(screen);
        return owner != null && CompatConfig.policy().allowsInteraction(owner);
    }

    /** The mod that owns a container screen, or null if it is not one. */
    @OnlyIn(Dist.CLIENT)
    private static String ownerOf(Screen screen) {
        if (screen == null) {
            return null;
        }
        if (screen instanceof AbstractContainerScreen<?> container) {
            return MenuOwner.ownerOf(container.getMenu());
        }
        CompatTarget tagged = identifyByClassName(screen.getClass().getName());
        if (tagged == CompatTarget.SOPHISTICATED) {
            return "sophisticatedbackpacks";
        }
        if (tagged == CompatTarget.TRAVELERS) {
            return "travelersbackpack";
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    public static CompatTarget identify(Screen screen) {
        if (screen == null) {
            return CompatTarget.NONE;
        }
        CompatTarget tagged = identifyByClassName(screen.getClass().getName());
        if (tagged == CompatTarget.SOPHISTICATED || tagged == CompatTarget.TRAVELERS) {
            return tagged;
        }
        if (screen instanceof AbstractContainerScreen) {
            return CompatTarget.VANILLA_CONTAINER;
        }
        return CompatTarget.NONE;
    }

    /**
     * Pure string matcher for unit tests and soft tagging.
     */
    public static CompatTarget identifyByClassName(String className) {
        if (className == null || className.isEmpty()) {
            return CompatTarget.NONE;
        }
        String name = className.toLowerCase(Locale.ROOT);
        if (name.contains("travelersbackpack")) {
            return CompatTarget.TRAVELERS;
        }
        if (name.contains("net.p3pp3rf1y")
                || (name.contains("sophisticated")
                        && (name.contains("backpack") || name.contains("storage")))) {
            return CompatTarget.SOPHISTICATED;
        }
        return CompatTarget.UNKNOWN_CONTAINER;
    }
}
