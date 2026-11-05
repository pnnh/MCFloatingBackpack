package pnnh.mcfloatingbackpack.client.panel;

import java.util.Collection;
import java.util.Set;

/**
 * 019: what the host screen does with a key press. While any screen is open the game does not apply key
 * mappings, so the host has to decide itself: keys that act on the world are swallowed, the panel's own toggle is
 * left to its handler, and every other bound key (inventory, chat, a mod's "open backpack"...) is handed back to
 * the game by yielding and replaying it. Pure logic with no Minecraft types, so it can be unit-tested.
 */
public final class HostKeyRouter {
    public enum KeyRole {
        /** The panel's own toggle key: not consumed, the open/close handler takes it. */
        TOGGLE,
        /** Bound to nothing: not consumed. */
        PASS,
        /** Only world actions: consumed, nothing happens. */
        SWALLOW,
        /** Bound to something else: consumed, then replayed to the game on the next tick. */
        YIELD
    }

    /** Key mappings that act on the world; the host is on top of the world, so these do nothing there. */
    static final Set<String> WORLD_ACTIONS = Set.of(
            "key.forward", "key.left", "key.back", "key.right", "key.jump", "key.sneak", "key.sprint",
            "key.attack", "key.use", "key.pickItem", "key.drop", "key.swapOffhand",
            "key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5",
            "key.hotbar.6", "key.hotbar.7", "key.hotbar.8", "key.hotbar.9",
            "key.saveToolbarActivator", "key.loadToolbarActivator");

    private HostKeyRouter() {
    }

    /** @param boundMappingNames the names of every key mapping bound to the pressed key */
    public static KeyRole classify(Collection<String> boundMappingNames, String toggleMappingName) {
        if (boundMappingNames.contains(toggleMappingName)) {
            return KeyRole.TOGGLE;
        }
        if (boundMappingNames.isEmpty()) {
            return KeyRole.PASS;
        }
        for (String name : boundMappingNames) {
            if (!WORLD_ACTIONS.contains(name)) {
                return KeyRole.YIELD;
            }
        }
        return KeyRole.SWALLOW;
    }
}
