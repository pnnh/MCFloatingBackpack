package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 057: the notices and the count of stacks left empty are wired as the plan says. Source text checks. */
class SnapshotNoticesWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void thePlayersDataIsReadThenTheirLossCountedWhereItIsReadAndToldAtLoginAndForgottenAtLogout() throws IOException {
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("PlayerEvent.LoadFromFile event"));
        assertTrue(main.contains("SnapshotNotices.onPlayerDataRead("));
        assertTrue(main.contains("event.getPlayerUUID() + \".dat\""), "the player's file is read again");
        assertTrue(main.contains("SnapshotNotices.onLogin(player)"));
        assertTrue(main.contains("SnapshotNotices.onLogout(player.getUUID())"));
        assertFalse(main.contains("LostCells"), "the count is not kept across ticks any more: it is taken on the thread that decodes");
    }

    @Test
    void theNoticeIsGivenOncePerPlayerAndTheStateWrittenWithThem() throws IOException {
        String notices = read("snapshot/SnapshotNotices.java");
        assertTrue(notices.contains("!state.notified().contains(uuid)"));
        assertTrue(notices.contains("state = state.withNotified(uuid);") && notices.contains("store.writeState(state);"));
        assertTrue(notices.contains("LOST.remove(uuid)"));
        assertTrue(notices.contains("LostCells.take()") && notices.contains("LostCells.reset();"));
        assertTrue(notices.contains("new ConcurrentHashMap<>()"), "the data may be read on another thread");
        assertTrue(notices.contains("BackpackContents.CODEC.parse(ops, contents)"), "the backpacks in the file are decoded again here");
    }

    @Test
    void whatCannotBeReadIsCountedWhereItIsLeftEmptyAndOnlyOnTheThreadThatReads() throws IOException {
        assertTrue(read("inventory/LenientStacks.java").contains("LostCells.add(lost);"));
        assertTrue(read("inventory/LostCells.java").contains("ThreadLocal<int[]>"));
    }

    @Test
    void aRestoreCountsWhatItCouldNotRestoreAndTellsIt() throws IOException {
        String ops = read("snapshot/SnapshotOps.java");
        int reset = ops.indexOf("LostCells.reset();");
        int read = ops.indexOf("readContents(server, service, chosen)");
        int take = ops.indexOf("LostCells.take()");
        assertTrue(reset > 0 && read > reset && take > read);
        assertTrue(read("snapshot/SnapshotServer.java").contains("result.lostCells(), 0"), "the window gets it in n1");
        assertTrue(read("snapshot/SnapshotCommands.java").contains("restored.lostCells() > 0"));
        assertTrue(read("client/snapshot/SnapshotResultText.java").contains("restored_lost"));
    }

    @Test
    void theVersionCheckAndTheWorldMarkerShareTheModVersion() throws IOException {
        assertTrue(read("version/ModVersion.java").contains("getModContainerById(MCFloatingBackpack.MOD_ID)"));
        String marker = read("worldmarker/WorldMarkerEvents.java");
        assertTrue(marker.contains("ModVersion.current()"));
        assertFalse(marker.contains("private static String modVersion()"));
    }
}
