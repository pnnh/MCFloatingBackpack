package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.HostKeyRouter.KeyRole;

class HostKeyRouterTest {
    private static final String TOGGLE = "key.mcfloatingbackpack.toggle";

    private static KeyRole classify(String... names) {
        return HostKeyRouter.classify(List.of(names), TOGGLE);
    }

    @Test
    void theOwnToggleKeyIsLeftToTheOpenCloseHandler() {
        assertEquals(KeyRole.TOGGLE, classify(TOGGLE));
        assertEquals(KeyRole.TOGGLE, classify(TOGGLE, "key.inventory"));
        assertEquals(KeyRole.TOGGLE, classify("key.jump", TOGGLE));
    }

    @Test
    void aKeyWithNoBindingPassesThrough() {
        assertEquals(KeyRole.PASS, HostKeyRouter.classify(List.of(), TOGGLE));
    }

    @Test
    void everyWorldActionIsSwallowed() {
        List<String> expected = List.of(
                "key.forward", "key.left", "key.back", "key.right", "key.jump", "key.sneak", "key.sprint",
                "key.attack", "key.use", "key.pickItem", "key.drop", "key.swapOffhand",
                "key.hotbar.1", "key.hotbar.2", "key.hotbar.3", "key.hotbar.4", "key.hotbar.5",
                "key.hotbar.6", "key.hotbar.7", "key.hotbar.8", "key.hotbar.9",
                "key.saveToolbarActivator", "key.loadToolbarActivator");
        assertEquals(Set.copyOf(expected), HostKeyRouter.WORLD_ACTIONS);
        for (String name : expected) {
            assertEquals(KeyRole.SWALLOW, classify(name), name);
        }
    }

    @Test
    void twoWorldActionsOnTheSameKeyAreStillSwallowed() {
        assertEquals(KeyRole.SWALLOW, classify("key.jump", "key.sneak"));
    }

    @Test
    void anythingThatOpensAScreenOrSwitchesAFeatureYields() {
        for (String name : List.of(
                "key.inventory", "key.chat", "key.command", "key.advancements", "key.togglePerspective",
                "key.sophisticatedbackpacks.open_backpack")) {
            assertEquals(KeyRole.YIELD, classify(name), name);
        }
    }

    @Test
    void aKeyBoundToBothAWorldActionAndSomethingElseYields() {
        assertEquals(KeyRole.YIELD, classify("key.jump", "key.inventory"));
        assertEquals(KeyRole.YIELD, classify("key.inventory", "key.jump"));
    }
}
