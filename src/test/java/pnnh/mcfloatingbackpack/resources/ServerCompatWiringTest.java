package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** 059: the server's levels are wired as the plan says. Source text checks. */
class ServerCompatWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    // --- the seven calls of the gate ---------------------------------------------------------------------------

    private static final List<String> ENTRIES = List.of(
            "network/BackpackQuickTransferPayload.java", "network/BackpackTakeFromContainerPayload.java", "network/MergePanelCursorPayload.java",
            "network/BackpackCursorGesturePayload.java", "network/BackpackClickPayload.java", "network/BackpackTrashPayload.java");

    @Test
    void everyServerEntryAsksThroughTheDenialsWrapper() throws IOException {
        int total = 0;
        for (String entry : ENTRIES) {
            String text = read(entry);
            int calls = count(text, "ServerCompatDenials.allows(");
            assertTrue(calls >= 1, entry);
            assertFalse(text.contains("ServerCompatGate.allows("), entry + " asks the gate directly, so no notice is sent");
            total += calls;
        }
        assertEquals(7, total, "six entries, the trash one asks twice");
    }

    @Test
    void theTrashSendEntryStaysOnVanillaMenusOnly() throws IOException {
        String text = read("network/BackpackTrashSendPayload.java");
        assertTrue(text.contains("!\"minecraft\".equals(owner) || !ServerCompatGate.allows(Action.TAKE_FROM_CONTAINER, owner)"));
    }

    @Test
    void noOtherServerCodeAsksTheGateOutsideTheWrapper() throws IOException {
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String name = file.toString().replace('\\', '/');
                if (name.endsWith("compat/ServerCompatGate.java") || name.endsWith("compat/ServerCompatDenials.java")
                        || name.endsWith("network/BackpackTrashSendPayload.java")) {
                    continue;
                }
                assertFalse(Files.readString(file, StandardCharsets.UTF_8).contains("ServerCompatGate.allows("), name);
            }
        }
    }

    @Test
    void theRefusalComesBeforeAnythingIsChangedInTheEntriesWhoseGateIsInAnIf() throws IOException {
        // every entry returns right after a refusal, before any change to the backpack or the menu
        for (String entry : ENTRIES) {
            String text = read(entry);
            int from = 0;
            while (true) {
                int at = text.indexOf("ServerCompatDenials.allows(", from);
                if (at < 0) {
                    break;
                }
                int open = text.indexOf('{', at);
                int close = text.indexOf('}', open);
                String body = text.substring(open + 1, close).trim();
                assertTrue(body.equals("return;"), entry + ": the refusal only returns, it changes nothing: " + body);
                from = close;
            }
        }
    }

    // --- the wrapper, the gate, the levels ---------------------------------------------------------------------

    @Test
    void theWrapperAsksTheGateThenTheThrottleAndLogsAtDebug() throws IOException {
        String denials = read("compat/ServerCompatDenials.java");
        int gate = denials.indexOf("ServerCompatGate.allows(action, owner)");
        int debug = denials.indexOf("LOGGER.debug(");
        int throttle = denials.indexOf("THROTTLE.shouldNotify(player.getUUID(), menu.containerId, owner)");
        assertTrue(gate > 0 && debug > gate && throttle > debug);
        assertTrue(denials.contains("message.mcfloatingbackpack.compat.server_off"));
        assertTrue(denials.contains("MenuOwner.ownerOf(menu)"));
    }

    @Test
    void theGateGetsItsLevelsFromTheServersEffectiveLevelWhenTheModStarts() throws IOException {
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("ServerCompatGate.setLevelSource(pnnh.mcfloatingbackpack.compat.ServerCompatLevels::effective)"));
        assertTrue(main.contains("ServerCompatDenials.forget(player.getUUID())"));
        assertEquals(2, count(main, "ServerCompatDenials.forget("), "when a screen is closed and when the player leaves");
        assertTrue(main.contains("PlayerContainerEvent.Close"));
    }

    @Test
    void theEffectiveLevelIsTheStricterOfTheServerConfigAndTheClientConfigWithoutAskingTheSide() throws IOException {
        String levels = read("compat/ServerCompatLevels.java");
        assertTrue(levels.contains("EffectiveLevel.effective(serverPolicy(), CompatConfig.policy(), modId)"));
        assertTrue(levels.contains("CompatPolicy.parse("), "the same parsing as the client's list");
        assertTrue(levels.contains("current.raw() == raw"), "parsed again only when the config hands over another list");
        assertTrue(levels.contains("LOGGER.warn(\"Server config compat.modLevels: {}\""));
        assertFalse(levels.contains("FMLEnvironment") && levels.contains("getDist"), "no check of the side: the default client policy is neutral");
        assertTrue(levels.contains("return effective(modId) == CompatLevel.FULL;"));
    }

    // --- the specific adaptations --------------------------------------------------------------------------------

    @Test
    void theWornSourcesAskTheServersLevelOnTheServerAndTheClientConfigOnAClient() throws IOException {
        for (String file : List.of("compat/curios/CuriosWornSource.java", "compat/accessories/AccessoriesWornSource.java")) {
            String text = read(file);
            assertTrue(text.contains("public boolean active(LivingEntity entity)"), file);
            assertTrue(text.contains("entity.level().isClientSide()"), file);
            assertTrue(text.contains("ServerCompatLevels.specificAdaptation("), file);
            assertTrue(text.contains("CompatConfig.policy().specificAdaptation("), file);
        }
    }

    @Test
    void theSearchAsksEachSourceAboutThePlayer() throws IOException {
        String search = read("inventory/worn/BackpackSearch.java");
        assertTrue(search.contains("source.active(player)"));
        assertFalse(search.replace("source.active(player)", "").contains("source.active()") && search.contains("firstWorn")
                && search.substring(search.indexOf("public Hit firstWorn")).contains("source.active()"), "firstWorn asks about the player");
        String worn = read("inventory/worn/WornBackpacks.java");
        assertTrue(worn.contains("SEARCH.activeSource(hit.sourceId(), entity)"));
        assertTrue(worn.contains("SEARCH.activeSource(sourceId, player)"));
        assertTrue(read("inventory/worn/WornSource.java").contains("default boolean active(P player)"));
    }

    @Test
    void theBridgeIsAskedOnlyWhileTheServersLevelIsFull() throws IOException {
        assertTrue(read("compat/NetworkStorageBridges.java").contains("ServerCompatLevels::specificAdaptation"));
        String dispatch = read("compat/BridgeDispatch.java");
        assertTrue(dispatch.contains("!current.isActive(bridge.modId()) || !levelAllows.test(bridge.modId())"));
    }

    @Test
    void theProtocolIsNotChanged() throws IOException {
        assertTrue(read("network/BackpackNetwork.java").contains("versioned(\"21\")"));
    }
}
