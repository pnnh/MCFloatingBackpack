package pnnh.mcfloatingbackpack.compat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.loading.FMLPaths;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 020: tells the player when NeoForge could not use the config file they saved. When it cannot read it (a syntax error,
 * for example a half-typed line caught by an editor's auto-save while the game is running) or finds values of the wrong
 * kind, NeoForge puts the player's file aside as {@code mcfloatingbackpack-client-N.toml.bak}, writes a fresh default
 * one and then loads it. The fresh backup next to the config is how this is noticed; the player is told to look in it.
 */
public final class ConfigFileWatch {
    /** How recent a backup has to be to count as "made just now". */
    private static final long WINDOW_MILLIS = 10_000L;

    private ConfigFileWatch() {
    }

    public static void onLoading(ModConfigEvent.Loading event) {
        check(event);
    }

    public static void onReloading(ModConfigEvent.Reloading event) {
        check(event);
    }

    private static void check(ModConfigEvent event) {
        if (event.getConfig().getSpec() != CompatConfig.SPEC) {
            return;
        }
        Path folder = FMLPaths.CONFIGDIR.get();
        Map<String, Long> modified = new HashMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                modified.put(file.getFileName().toString(), Files.getLastModifiedTime(file).toMillis());
            }
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.debug("Could not look for config backups in {}", folder, e);
            return;
        }
        List<String> fresh = ConfigNotices.freshBackups(modified, System.currentTimeMillis(), WINDOW_MILLIS);
        for (String backup : fresh) {
            MCFloatingBackpack.LOGGER.warn(
                    "The client config could not be used as saved; the saved file was put aside as {} and the defaults were restored",
                    backup);
            ConfigNotices.fileProblem(backup);
        }
    }
}
