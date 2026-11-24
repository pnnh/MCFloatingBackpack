package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 065: every panel request asks the combined session's target; what belongs to "the backpack the B key opens" does not. */
class CombinedWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path RES = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    private static final String[] PANEL_HANDLERS = {
        "network/BackpackClickPayload.java",
        "network/BackpackCursorGesturePayload.java",
        "network/BackpackQuickTransferPayload.java",
        "network/BackpackTakeFromContainerPayload.java",
        "network/BackpackNetworkTakePayload.java",
        "network/MergePanelCursorPayload.java",
        "network/BackpackTrashPayload.java",
        "network/BackpackTrashSendPayload.java",
        "network/BackpackSortPayload.java",
        "network/BackpackTagPayload.java",
        "network/BackpackTagOverviewPayload.java",
        "network/BackpackPickupBlacklistOpPayload.java",
    };

    private static final String[] B_KEY_USERS = {
        "network/BackpackOpenRequestPayload.java",
        "inventory/pickup/AutoPickupEvents.java",
        "tool/MobileTools.java",
        "tool/furnace/MobileFurnaceStore.java",
        "tool/furnace/MobileFurnaceMenu.java",
        "pipe/PipeActions.java",
        "light/PlayerLights.java",
        "diag/BackpackWatchService.java",
        "client/jukebox/JukeboxPlayback.java",
    };

    @Test
    void everyPanelRequestResolvesItsBackpackThroughThePanelTargets() throws IOException {
        for (String file : PANEL_HANDLERS) {
            String code = read(file);
            assertTrue(code.contains("PanelTargets.resolve("), file);
            assertFalse(code.contains("BackpackRef.resolve("), file);
        }
    }

    @Test
    void whatFollowsTheBackpackTheBKeyOpensStillAsksTheOldWay() throws IOException {
        for (String file : B_KEY_USERS) {
            String code = read(file);
            assertTrue(code.contains("BackpackRef.resolve("), file);
            assertFalse(code.contains("PanelTargets.resolve("), file);
        }
    }

    @Test
    void theClickRefusesAClaimThatIsNotTheCombinedTarget() throws IOException {
        String click = read("network/BackpackClickPayload.java");
        assertTrue(click.contains("CombinedSessions.has(serverPlayer.getUUID())") && click.contains("!claimed.equals(resolved)"));
    }

    @Test
    void theProtocolIs23AndBothPacketsAreRegistered() throws IOException {
        String network = read("network/BackpackNetwork.java");
        assertTrue(network.contains("versioned(\"24\")"));
        assertTrue(network.contains("CombinedSelectPayload.TYPE") && network.contains("CombinedMembersPayload.TYPE"));
        assertTrue(network.contains("CombinedSelectPayload::handleServer") && network.contains("CombinedMembersPayload::handleClient"));
    }

    @Test
    void theServerConfigHasTheCombinedSectionAndTheGateKeepsTheSessionsInStep() throws IOException {
        assertTrue(read("diag/DiagConfig.java").contains("CombinedConfig.define(BUILDER)"));
        String gate = read("inventory/identity/BackpackGate.java");
        assertTrue(gate.contains("CombinedSessions.end(player)"), "a closed panel ends the combined session");
        assertTrue(gate.contains("CombinedSessions.forget(player)"), "leaving does too");
        assertTrue(gate.contains("CombinedSessions.recover(player)"), "the stale-panel guard looks for the backpack again");
        assertTrue(gate.contains("CombinedSessions.tick(player)"), "the once-a-second check");
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("CombinedSessions.clear()"), "stopping the server");
        assertTrue(read("network/BackpackOpenRequestPayload.java").contains("CombinedSessions.endAndTell(serverPlayer)"), "the B key ends the combined panel");
    }

    @Test
    void theCombinedBackpackDoesNotFitInsideContainersAndIsNotLetIntoABackpack() throws IOException {
        assertTrue(read("item/CombinedBackpackItem.java").contains("public boolean canFitInsideContainerItems() {\n        return false;"));
        String nesting = read("inventory/nesting/BackpackNesting.java");
        assertTrue(nesting.contains("CombinedBackpackItem.isCombined(stack)") && nesting.contains("CombinedBackpackItem.idOf(tier)"));
    }

    @Test
    void theCombinedKeyIsRegisteredAndTheHostScreenLeavesItToTheHandler() throws IOException {
        String keys = read("client/FloatingBackpackKeys.java");
        assertTrue(keys.contains("COMBINED") && keys.contains("GLFW.GLFW_KEY_U") && keys.contains("event.register(COMBINED)"));
        assertTrue(read("client/panel/PanelHostScreen.java").contains("FloatingBackpackKeys.COMBINED.getName()"));
        assertTrue(read("client/FloatingPanelEvents.java").contains("CombinedSelectPayload.open()"));
    }

    @Test
    void thePanelSendsNothingWhileTheCombinedPanelIsLoadingATab() throws IOException {
        assertTrue(read("client/panel/FloatingPanelInput.java").contains("ClientCombinedState.get().blocksRequests()"));
        assertTrue(read("client/panel/DeleteKeyInput.java").contains("ClientCombinedState.get().blocksRequests()"));
    }

    @Test
    void theBarsAreDrawnClaimedAndKnownToJei() throws IOException {
        assertTrue(read("client/panel/FloatingPanelRenderer.java").contains("CombinedBarRenderer.render("));
        assertTrue(read("client/panel/FloatingPanelInput.java").contains("CombinedBarInput.onPress("));
        assertTrue(read("client/FloatingPanelEvents.java").contains("CombinedBarInput.onScroll("));
        assertTrue(read("compat/jei/FloatingBackpackJeiPlugin.java").contains("CombinedBars.layout().barsRect()"));
    }

    @Test
    void theMobileToolsAreDeadOnATabThatIsNotTheBKeysBackpack() throws IOException {
        assertTrue(read("client/panel/FloatingPanelInput.java").contains("ClientCombinedState.get().toolsDisabled()"));
        assertTrue(read("client/panel/FloatingPanelRenderer.java").contains("ClientCombinedState.get().toolsDisabled()"));
    }

    @Test
    void everyKeyTheCombinedPanelUsesHasAWordingInBothLanguages() throws IOException {
        String[] keys = {
            "key.mcfloatingbackpack.combined",
            "message.mcfloatingbackpack.combined.need_worn",
            "message.mcfloatingbackpack.combined.disabled",
            "message.mcfloatingbackpack.combined.empty_cursor",
            "message.mcfloatingbackpack.combined.no_members",
            "gui.mcfloatingbackpack.combined.skipped_tier",
            "gui.mcfloatingbackpack.combined.skipped_newer",
            "gui.mcfloatingbackpack.combined.skipped_cap",
            "gui.mcfloatingbackpack.combined.place.chest",
            "gui.mcfloatingbackpack.combined.place.curios",
            "gui.mcfloatingbackpack.combined.place.accessories",
            "gui.mcfloatingbackpack.combined.place.offhand",
            "gui.mcfloatingbackpack.combined.place.inventory",
            "gui.mcfloatingbackpack.combined.capacity",
            "gui.mcfloatingbackpack.combined.b_active",
            "gui.mcfloatingbackpack.tools_active_only",
            "tooltip.mcfloatingbackpack.combined",
        };
        for (String lang : new String[] {"en_us.json", "zh_cn.json"}) {
            String text = Files.readString(RES.resolve(lang), StandardCharsets.UTF_8);
            for (String key : keys) {
                assertTrue(text.contains("\"" + key + "\""), lang + " " + key);
            }
        }
    }

    @Test
    void noCombinedCodeTouchesAThirdPartyType() throws IOException {
        for (String file : new String[] {
            "inventory/combined/CombinedSessions.java", "inventory/combined/PanelTargets.java", "inventory/combined/CombinedCandidates.java",
            "item/CombinedBackpackItem.java", "network/CombinedSelectPayload.java", "network/CombinedMembersPayload.java"}) {
            String code = read(file);
            for (String banned : new String[] {"top.theillusivec4", "io.wispforest", "appeng.", "org.cyclops"}) {
                assertFalse(code.contains(banned), file + " " + banned);
            }
        }
    }
}
