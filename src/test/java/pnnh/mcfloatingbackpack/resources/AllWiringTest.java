package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 066: the "all" tab: every panel request that can work on it asks for the grid first, and what belongs to one backpack ignores it. */
class AllWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    private static final String[] GRID_HANDLERS = {
        "network/BackpackClickPayload.java",
        "network/BackpackCursorGesturePayload.java",
        "network/BackpackQuickTransferPayload.java",
        "network/BackpackTakeFromContainerPayload.java",
        "network/BackpackNetworkTakePayload.java",
        "network/MergePanelCursorPayload.java",
    };

    private static final String[] ONE_BACKPACK_HANDLERS = {
        "network/BackpackTrashPayload.java",
        "network/BackpackTrashSendPayload.java",
        "network/BackpackSortPayload.java",
        "network/BackpackTagPayload.java",
        "network/BackpackTagOverviewPayload.java",
        "network/BackpackPickupBlacklistOpPayload.java",
    };

    @Test
    void everyHandlerThatCanWorkOnTheGridAsksIfItIsOnTheAllTabBeforeResolvingOneBackpack() throws IOException {
        for (String handler : GRID_HANDLERS) {
            String text = read(handler);
            int inAll = text.indexOf("PanelTargets.inAll(");
            int resolve = text.indexOf("PanelTargets.resolve(");
            assertTrue(inAll > 0, handler + " asks whether the panel is on the all tab");
            assertTrue(resolve < 0 || inAll < resolve, handler + " asks before it resolves one backpack");
            assertFalse(text.contains("acquireForPanel(player, ref)") && text.indexOf("acquireForPanel(player, ref)") < inAll, handler);
        }
    }

    @Test
    void theHandlersWithSlotNumbersCheckTheEpochOnBothSides() throws IOException {
        for (String handler : new String[] {
            "network/BackpackClickPayload.java", "network/BackpackCursorGesturePayload.java", "network/BackpackQuickTransferPayload.java"}) {
            String text = read(handler);
            assertTrue(text.contains("int epoch"), handler + " carries the epoch");
            assertTrue(text.contains("AllContext.begin(") && text.contains("payload.epoch()"), handler + " checks it on the all tab");
            assertTrue(text.contains("AllRequestRules.epochOk(false, 0, payload.epoch())"),
                    handler + ": a request made for the all tab is never read as one backpack's cell number");
        }
    }

    @Test
    void theHandlersOfOneBackpacksFeaturesIgnoreTheAllTab() throws IOException {
        for (String handler : ONE_BACKPACK_HANDLERS) {
            String text = read(handler);
            int inAll = text.indexOf("PanelTargets.inAll(player)");
            int resolve = text.indexOf("PanelTargets.resolve(player)");
            assertTrue(inAll > 0 && inAll < resolve, handler + " returns before it resolves a backpack");
        }
    }

    @Test
    void theAllTabHasNoSingleBackpackToResolve() throws IOException {
        String targets = read("inventory/combined/PanelTargets.java");
        assertTrue(targets.contains("session.inAll()") && targets.contains("return null;"));
        assertTrue(targets.contains("public static boolean inAll("));
    }

    @Test
    void theAutoPickupGoesThroughTheOnePlaceThatKnowsWhatThePanelShows() throws IOException {
        String pickup = read("inventory/pickup/AutoPickupEvents.java");
        assertFalse(pickup.contains("PacketDistributor"), "no packet of its own: it would land on whatever tab is shown");
        assertTrue(pickup.contains("BackpackNetwork.sendPickupUpdate("));
        String network = read("network/BackpackNetwork.java");
        assertTrue(network.contains("public static void sendPickupUpdate("));
        // the three senders of a backpack's update know the combined session
        int sync = network.indexOf("public static void sendFullSync(");
        int slot = network.indexOf("public static void sendSlotUpdate(ServerPlayer player, BackpackRef ref, PanelArea area, int slotIndex)");
        assertTrue(network.indexOf("combined.inAll()", sync) > sync);
        assertTrue(network.indexOf("combined.inAll()", slot) > slot);
    }

    @Test
    void theProtocolIs24AndTheTwoNewPacketsAreRegistered() throws IOException {
        String network = read("network/BackpackNetwork.java");
        assertTrue(network.contains("versioned(\"24\")"));
        assertTrue(network.contains("CombinedMemberSyncPayload.TYPE") && network.contains("CombinedCellUpdatePayload.TYPE"));
        String sync = read("network/CombinedMemberSyncPayload.java");
        assertTrue(sync.contains("BackpackInventory.MAX_MAIN_SLOTS") && sync.contains("AllAddressing.MAX_MEMBERS"));
        assertTrue(read("network/CombinedSelectPayload.java").contains("SELECT_ALL = 2"));
        assertTrue(read("network/CombinedMembersPayload.java").contains("int allEpoch"));
    }

    @Test
    void theCursorIsGivenBackWhereverTheTabCanEnd() throws IOException {
        String network = read("network/BackpackNetwork.java");
        assertTrue(network.contains("CombinedSessions.flush(player);"), "a closed panel");
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("CombinedSessions.flush(player)"), "leaving the world and stopping the server");
        String sessions = read("inventory/combined/CombinedSessions.java");
        assertTrue(sessions.contains("flush(player);"), "ending, switching tab and beginning the tab again");
        assertTrue(sessions.contains("public static void flush(ServerPlayer player)"));
    }

    @Test
    void theCursorIsNeverWrittenIntoABackpack() throws IOException {
        String click = read("network/BackpackClickPayload.java");
        // both saves of a click settle the loan first
        int saves = 0;
        int from = 0;
        while ((from = click.indexOf("ref.saveInventory(serverPlayer, inventory)", from)) >= 0) {
            assertTrue(click.lastIndexOf("settle(loan, inventory)", from) > click.lastIndexOf("\n\n", from - 30) - 400, "settle before save #" + saves);
            saves++;
            from++;
        }
        assertTrue(saves >= 2);
        String context = read("inventory/combined/AllContext.java");
        assertTrue(context.indexOf("setCursor(ItemStack.EMPTY)") < context.indexOf("ref.saveInventory(player, inventories[member])"));
    }
}
