package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** 060: taking items out of a network terminal is wired as the plan says. Source text checks. */
class NetworkTakeWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    // --- the client -----------------------------------------------------------------------------------------------

    @Test
    void theCtrlClickAsksTheRecognisersBeforeTheGestureAndDoesNotStartADrag() throws IOException {
        String input = read("client/panel/QuickTransferInput.java");
        int begin = input.indexOf("public static boolean beginFromScreen(");
        String body = input.substring(begin, input.indexOf("private static void start(", begin));
        int recogniser = body.indexOf("NetworkTerminalClients.entryAt(");
        int send = body.indexOf("new BackpackNetworkTakePayload(");
        int gesture = body.indexOf("screenSlotAt(");
        assertTrue(body.contains("NetworkTerminalClients.entryAt((AbstractContainerScreen<?>) screen, mouseX, mouseY)"));
        assertTrue(recogniser > 0 && send > recogniser && gesture > send, "recogniser, then the request, then the old path");
        String branch = body.substring(recogniser, gesture);
        assertTrue(branch.contains("return true;"), "the press is taken");
        assertFalse(branch.contains("start("), "an entry is a click, not the start of a drag");
    }

    @Test
    void theRecognisersAreOnlyAskedWhileTheModIsOnForThisClient() throws IOException {
        String clients = read("client/compat/NetworkTerminalClients.java");
        assertTrue(clients.contains("ClientCompatVersions.inRange(row)"));
        assertTrue(clients.contains("CompatConfig.policy().specificAdaptation(modId)"));
        assertTrue(clients.contains("MenuOwner.ownerOf(screen.getMenu())"));
        assertTrue(clients.contains("catch (LinkageError | RuntimeException e)"), "a mod that changed does not break the click");
        String versions = read("compat/ClientCompatVersions.java");
        assertTrue(versions.contains("CompatFinding.check(row, installedVersion)"));
        assertFalse(versions.contains("ThirdPartyConfig"), "the client cannot see the server's config");
        for (String client : List.of("client/compat/NetworkTerminalClients.java", "compat/ClientCompatVersions.java")) {
            String text = read(client);
            assertFalse(text.contains("CompatRuntimeState") || text.contains("ThirdPartyCompatService"), client + ": a client has no server state");
        }
    }

    @Test
    void theRecognisersAreRegisteredOnlyWhenTheirModIsLoaded() throws IOException {
        String setup = read("client/ClientSetup.java");
        assertTrue(setup.contains("isLoaded(\"integratedterminals\")") && setup.contains("IntegratedTerminalsCompat.registerClient()"));
        assertTrue(setup.contains("isLoaded(\"ae2\")") && setup.contains("Ae2Compat.registerClient()"));
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("isLoaded(\"ae2\")") && main.contains("Ae2Compat.register()"));
    }

    // --- the request and the server -------------------------------------------------------------------------------

    @Test
    void theRequestCarriesAnAmountTheServerClampsAndNoThirdPartyType() throws IOException {
        String payload = read("network/BackpackNetworkTakePayload.java");
        assertTrue(payload.contains("record BackpackNetworkTakePayload(int containerId, ItemStack what, long handle, int amount)"));
        assertTrue(payload.contains("ItemStack.STREAM_CODEC"), "an empty stack cannot be sent");
        assertTrue(payload.contains("ByteBufCodecs.VAR_LONG") && payload.contains("ByteBufCodecs.VAR_INT, BackpackNetworkTakePayload::amount"));
        assertFalse(payload.contains("org.cyclops") || payload.contains("appeng."));
        assertTrue(read("network/BackpackNetwork.java").contains("BackpackNetworkTakePayload::handleServer"));
        assertTrue(read("network/BackpackNetwork.java").contains("versioned(\"22\")"));
    }

    @Test
    void theServerChecksThenTakesThenPlacesThenGivesBackThenFallsBack() throws IOException {
        String payload = read("network/BackpackNetworkTakePayload.java");
        String[] order = {
            "BackpackRef.resolve(player)", "ServerCompatDenials.allows(player, menu, Action.TAKE_FROM_CONTAINER)", "menu.getCarried().isEmpty()",
            "menu.containerId != payload.containerId()", "BackpackGate.acquireForPanel(player, ref)", "NetworkStorageBridges.tryTake(",
            "backpack.insertIntoMain(", "NetworkStorageBridges.tryGiveBack(", "new ContainerMenuAccess(player).fallback()", "ref.saveInventory(player, backpack)",
            "BackpackNetwork.sendFullSync(player, ref)"};
        int at = -1;
        for (String step : order) {
            int next = payload.indexOf(step, at + 1);
            assertTrue(next > at, step + " comes after the one before it");
            at = next;
        }
        assertTrue(payload.contains("fallback.drop("), "what nobody takes is dropped, never lost");
        assertTrue(payload.contains("NetworkTakeRules.distribute("));
        assertTrue(payload.contains("NetworkTakeRules.amountFor(ItemStackTransferOps.INSTANCE, payload.what(), payload.amount())"),
                "the client's amount is clamped by the server to a full stack");
    }

    @Test
    void theBridgeInterfaceHasTheTakeMethodsWithSafeDefaults() throws IOException {
        String dispatch = read("compat/BridgeDispatch.java");
        assertTrue(dispatch.contains("default boolean handlesTake(M menu)"));
        assertTrue(dispatch.contains("default S take(P player, M menu, S what, long handle)"));
        assertTrue(dispatch.contains("default S giveBack(P player, M menu, S stack, long handle)"));
        assertEquals(3, count(dispatch, "!current.isActive(bridge.modId()) || !levelAllows.test(bridge.modId())"), "store, take and give back all need the state and the level");
        assertTrue(dispatch.contains("NetworkTakeRules.legalTake("));
    }

    // --- the two mods -----------------------------------------------------------------------------------------------

    @Test
    void integratedTerminalsTakesFromTheSelectedChannelWithTheExactMatch() throws IOException {
        String bridge = read("compat/integratedterminals/IntegratedTerminalsBridge.java");
        assertTrue(bridge.contains("public boolean handlesTake(AbstractContainerMenu menu)"));
        assertTrue(bridge.contains("getChannel(((ContainerTerminalStorageBase<?>) menu).getSelectedChannel())"));
        assertTrue(bridge.contains("network.extract(prototype, IngredientComponent.ITEMSTACK.getMatcher().getExactMatchNoQuantityCondition(), false)"));
        assertTrue(bridge.contains("what.copyWithCount(Math.max(1, Math.min(what.getCount(), what.getMaxStackSize())))"));
        assertFalse(bridge.contains("getExactMatchCondition()"), "the exact condition matches the amount too: an entry with fewer than a full stack would give nothing");
        String client = read("compat/integratedterminals/IntegratedTerminalsClient.java");
        assertTrue(client.contains("storage instanceof TerminalStorageTabIngredientComponentItemStackCraftingClient"), "the crafting page is left out");
        assertTrue(client.contains("storage.getIngredientComponent() != IngredientComponent.ITEMSTACK"));
        assertTrue(client.contains("terminal.getStorageSlotIndexAtPosition(mouseX, mouseY)"), "the screen says which entry the cursor is over");
        assertTrue(client.contains("storage.getSlot(terminal.getMenu().getSelectedChannel(), index)"));
        assertTrue(client.contains("new NetworkEntry(stack.copyWithCount(1), 0L)"));
        // the first real-machine test: IT keeps a picked-up entry as the page's "active entry", not as the menu's carried stack
        assertTrue(client.contains("storage.getActiveSlotId() < 0 || storage.getActiveSlotQuantity() <= 0"));
        assertTrue(client.contains("storage.getSlot(terminal.getMenu().getSelectedChannel(), storage.getActiveSlotId())"));
        assertTrue(client.contains("storage.resetActiveSlot()"), "the terminal lets go of the entry once the panel took the drop");
    }

    @Test
    void aPickedUpEntryIsDrawnAboveThePanelAndDroppedIntoItByAClick() throws IOException {
        String drop = read("client/panel/NetworkHeldDrop.java");
        assertTrue(drop.contains("minecraft.player.containerMenu.getCarried().isEmpty()"), "the menu's own carried stack keeps its old path");
        assertTrue(drop.contains("state.hitsBody(mouseX, mouseY)") && drop.contains("hitsCloseButton") && drop.contains("hitsResizeHandle"));
        assertTrue(drop.contains("new BackpackNetworkTakePayload(") && drop.contains("held.get().amount()"));
        assertTrue(drop.contains("NetworkTerminalClients.release(container)"));
        String events = read("client/FloatingPanelEvents.java");
        int drops = events.indexOf("NetworkHeldDrop.tryDrop(");
        assertTrue(drops > 0 && drops < events.indexOf("FloatingPanelInput.onMouseButton(\n                event.getButton(), GLFW.GLFW_PRESS"),
                "the drop is asked before the panel handles the press");
        String renderer = read("client/panel/FloatingPanelRenderer.java");
        assertTrue(renderer.contains("NetworkHeldDrop.heldOn(minecraft, minecraft.screen).map(NetworkHeld::asStack)"));
        assertTrue(renderer.indexOf("NetworkHeldDrop.heldOn(") < renderer.indexOf("translate(0.0F, 0.0F, CURSOR_Z)"), "drawn at the cursor's depth");
        assertFalse(renderer.contains("TEMPDBG"));
    }

    @Test
    void ae2UsesTheMenusOwnPickUpAndSetDownAndLeavesStoringToTheGenericPath() throws IOException {
        String bridge = read("compat/ae2/Ae2Bridge.java");
        assertTrue(bridge.contains("public boolean handles(AbstractContainerMenu menu) {\n        return false;"), "storing stays on the generic path");
        assertTrue(bridge.contains("menu instanceof MEStorageMenu"));
        assertTrue(bridge.contains("!terminal.getCarried().isEmpty()"));
        assertEquals(2, count(bridge, "InventoryAction.PICKUP_OR_SET_DOWN"), "one to take, one to give back");
        assertFalse(bridge.contains("SHIFT_CLICK"), "shift click would put the stack into the player's inventory");
        assertEquals(2, count(bridge, "terminal.setCarried(ItemStack.EMPTY)"), "the carried stack is only the way, it is emptied at once");
        String client = read("compat/ae2/Ae2Client.java");
        assertTrue(client.contains("screen instanceof MEStorageScreen<?>") && client.contains("slot instanceof RepoSlot repoSlot"));
        assertTrue(client.contains("entry.getWhat() instanceof AEItemKey key") && client.contains("entry.getStoredAmount() <= 0"));
        assertTrue(client.contains("new NetworkEntry(key.toStack(1), entry.getSerial())"));
    }

    @Test
    void thirdPartyTypesAreOnlyInTheirOwnCompatFolders() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String name = file.toString().replace('\\', '/');
                boolean cyclops = text.contains("import org.cyclops.");
                boolean appeng = text.contains("import appeng.");
                if (cyclops && !name.contains("/compat/integratedterminals/")) {
                    offenders.add(name);
                }
                if (appeng && !name.contains("/compat/ae2/")) {
                    offenders.add(name);
                }
            }
        }
        assertEquals(List.of(), offenders);
        try (Stream<Path> files = Files.list(SRC.resolve("compat/ae2"))) {
            assertEquals(3, files.count(), "the bridge, the client recogniser and the entry points");
        }
    }

    @Test
    void ae2HasItsRowInTheTableAndItsOptionalDependency() throws IOException {
        assertTrue(read("compat/ThirdPartyTable.java").contains("List.of(INTEGRATED_TERMINALS_ROW, AE2_ROW)"));
        String toml = Files.readString(Path.of("src/main/templates/META-INF/neoforge.mods.toml"), StandardCharsets.UTF_8);
        assertTrue(toml.contains("modId=\"ae2\"\n    type=\"optional\"\n    versionRange=\"[0,)\""));
    }

    private static int count(String text, String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }
}
