package pnnh.mcfloatingbackpack.worldmarker;

import net.minecraft.CrashReport;
import net.minecraft.ReportedException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.revision.DataRevision;
import pnnh.mcfloatingbackpack.version.VersionDebug;

/**
 * 041: the world-level downgrade guard, the only place where it meets Minecraft. {@link ServerAboutToStartEvent} comes before
 * {@code loadLevel()} and so before any player data is read (which happens when a player logs in), on the integrated server and the
 * dedicated server alike; a refusal is an exception thrown out of the server's start, which the server turns into a crash report.
 * In a single player game the client has to be let out of its wait first (see {@link #releaseWaitingClient}). See
 * {@code specs/041-world-downgrade-guard/research.md}.
 */
public final class WorldMarkerEvents {
    private WorldMarkerEvents() {
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        WorldMarkerCheck.Result result = WorldMarkerCheck.run(
                server.getWorldPath(LevelResource.ROOT).resolve(WorldMarkerStore.RELATIVE_PATH),
                DataRevision.CURRENT,
                VersionDebug.acceptDowngrade(),
                VersionDebug.worldMarkerRevisionOverride(),
                modVersion());
        for (WorldMarkerCheck.Line line : result.lines()) {
            switch (line.severity()) {
                case INFO -> MCFloatingBackpack.LOGGER.info(line.text());
                case WARN -> MCFloatingBackpack.LOGGER.warn(line.text());
                case ERROR -> MCFloatingBackpack.LOGGER.error(line.text());
            }
        }
        if (result.refused()) {
            releaseWaitingClient(server);
            throw new ReportedException(CrashReport.forThrowable(new IllegalStateException(result.crashDetail()), result.crashTitle()));
        }
    }

    /**
     * In a single player game the client, right after it started the integrated server, waits in a loop that draws nothing and does not
     * look for a crash until the server has made its chunk progress listener; the server makes it in {@code loadLevel()}, which a
     * refusal skips, so the game would hang for good (found in the first real try, 2026-11-11). Making the listener here lets the client
     * go on to its next loop, which shows the server's crash: the game ends with the crash report. The factory is a private field of
     * the server, read by reflection (this mod is for 1.21.1 only); if that fails the refusal still goes ahead and the log says what
     * to do. A dedicated server has no such client.
     */
    private static void releaseWaitingClient(MinecraftServer server) {
        if (!server.isSingleplayer()) {
            return;
        }
        try {
            java.lang.reflect.Field field = MinecraftServer.class.getDeclaredField("progressListenerFactory");
            field.setAccessible(true);
            ((ChunkProgressListenerFactory) field.get(server)).create(0);
        } catch (ReflectiveOperationException | RuntimeException failed) {
            MCFloatingBackpack.LOGGER.error("MCFloatingBackpack: could not let the game leave the loading screen; if it stays there, close "
                    + "the game (the refusal above is why). / 无法让游戏离开加载界面；如果停在那里，请直接关闭游戏（原因见上面的拒绝说明）。", failed);
        }
    }

    /** This version's number as the mod loader has it, or null when it cannot be told. */
    private static String modVersion() {
        return ModList.get().getModContainerById(MCFloatingBackpack.MOD_ID)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse(null);
    }
}
