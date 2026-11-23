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

/** 058: the Integrated Terminals bridge sits where the plan says and touches what it should. Source text checks. */
class IntegratedTerminalsWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theBridgeIsAskedBeforeTheGenericDeliveryAndAnAnswerSkipsIt() throws IOException {
        String payload = read("network/BackpackQuickTransferPayload.java");
        int bridge = payload.indexOf("NetworkStorageBridges.tryInsert(player, player.containerMenu, stack)");
        int generic = payload.indexOf("OpenContainerDelivery.deliver(ItemStackTransferOps.INSTANCE, menu, stack)");
        int gate = payload.indexOf("ServerCompatDenials.allows(player, player.containerMenu, Action.DELIVER_TO_CONTAINER)");
        assertTrue(gate > 0 && bridge > gate && generic > bridge, "gate, then bridge, then the generic path");
        String between = payload.substring(bridge, generic);
        assertTrue(between.contains("backpack.setStackInSlot(index, viaBridge.get());") && between.contains("continue;"));
    }

    @Test
    void thirdPartyTypesOfIntegratedTerminalsAreOnlyInTheTwoCompatClasses() throws IOException {
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SRC)) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                boolean touches = text.contains("import org.cyclops.");
                boolean allowed = file.toString().replace('\\', '/').contains("/compat/integratedterminals/");
                if (touches && !allowed) {
                    offenders.add(file.toString());
                }
            }
        }
        assertEquals(List.of(), offenders);
        try (Stream<Path> files = Files.list(SRC.resolve("compat/integratedterminals"))) {
            assertEquals(3, files.count(), "the bridge, the client recogniser (060) and the entry points");
        }
    }

    @Test
    void theBridgeTakesOnlyTheItemStoragePage() throws IOException {
        String bridge = read("compat/integratedterminals/IntegratedTerminalsBridge.java");
        assertTrue(bridge.contains("menu instanceof ContainerTerminalStorageBase<?> container"));
        assertTrue(bridge.contains("container.getTabServer(container.getSelectedTab())"));
        assertTrue(bridge.contains("tab instanceof TerminalStorageTabIngredientComponentServer<?, ?> storage"));
        assertTrue(bridge.contains("storage instanceof TerminalStorageTabIngredientComponentItemStackCraftingServer"), "the crafting page is left out");
        assertTrue(bridge.contains("getIngredientNetwork().getComponent() == IngredientComponent.ITEMSTACK"));
    }

    @Test
    void theBridgeInsertsIntoTheSelectedChannelAndTouchesNothingElse() throws IOException {
        String bridge = read("compat/integratedterminals/IntegratedTerminalsBridge.java");
        assertTrue(bridge.contains(".getIngredientNetwork().getChannel(channel)"));
        assertTrue(bridge.contains("getSelectedChannel()"));
        assertTrue(bridge.contains("network.insert(stack, false)"));
        assertFalse(bridge.contains("getInventory"), "the player's inventory is not touched");
        assertFalse(bridge.contains("setItem"));
        assertFalse(bridge.contains("sendSystemMessage"));
        assertFalse(bridge.contains("PacketDistributor"), "no packet: the terminal syncs by itself");
    }

    @Test
    void theBridgeIsRegisteredOnlyWhenTheModIsLoaded() throws IOException {
        String main = read("MCFloatingBackpack.java");
        int check = main.indexOf("ModList.get().isLoaded(\"integratedterminals\")");
        int register = main.indexOf("IntegratedTerminalsCompat.register()");
        assertTrue(check > 0 && register > check);
        assertEquals(1, main.split("IntegratedTerminalsCompat\\.register\\(\\)", -1).length - 1);
        assertTrue(read("compat/integratedterminals/IntegratedTerminalsCompat.java").contains("NetworkStorageBridges.register(new IntegratedTerminalsBridge())"));
    }

    @Test
    void theClientKnowsNoThirdPartyTypeAndTheServersBridgeInterface() throws IOException {
        // 060 added a client recogniser registry, so the client code mentions the mod only by name in the set-up (isLoaded) and never by type.
        try (Stream<Path> files = Files.walk(SRC.resolve("client"))) {
            for (Path file : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".java"))::iterator) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(text.contains("NetworkStorageBridge"), file.toString());
                assertFalse(text.contains("org.cyclops."), file.toString());
                assertFalse(text.contains("import appeng."), file.toString());
            }
        }
        assertTrue(read("network/BackpackNetwork.java").contains("versioned(\"22\")"));
    }
}
