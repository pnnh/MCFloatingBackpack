package pnnh.mcfloatingbackpack.client.panel;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * Client-local panel layout preference (position only).
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingPanelConfig {
    private static final String FILE_NAME = MCFloatingBackpack.MOD_ID + "-panel.properties";
    private static final String KEY_X = "posX";
    private static final String KEY_Y = "posY";
    private static final String KEY_VISIBLE_ROWS = "visibleRows";
    private static final String KEY_VISIBLE_COLS = "visibleCols";

    private FloatingPanelConfig() {
    }

    public static Path configPath() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE_NAME);
    }

    public static void loadInto(FloatingPanelState state) {
        Path path = configPath();
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
            int x = parseInt(properties.getProperty(KEY_X), state.getPosX());
            int y = parseInt(properties.getProperty(KEY_Y), state.getPosY());
            state.setPos(x, y);
            // Only present once the player has actually resized the panel at least once; an
            // older config file (or one that's never had a resize) must keep the auto-fit default
            // instead of locking in whatever size happens to be in the fields right now.
            String rowsRaw = properties.getProperty(KEY_VISIBLE_ROWS);
            String colsRaw = properties.getProperty(KEY_VISIBLE_COLS);
            if (rowsRaw != null || colsRaw != null) {
                int rows = parseInt(rowsRaw, state.getVisibleRows());
                int cols = parseInt(colsRaw, state.getVisibleCols());
                state.setSizePreference(rows, cols);
            }
        } catch (IOException exception) {
            MCFloatingBackpack.LOGGER.warn("Could not load floating panel layout from {}", path, exception);
        }
    }

    public static void saveFrom(FloatingPanelState state) {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Properties properties = new Properties();
            properties.setProperty(KEY_X, Integer.toString(state.getPosX()));
            properties.setProperty(KEY_Y, Integer.toString(state.getPosY()));
            if (state.isSizePreferenceSet()) {
                properties.setProperty(KEY_VISIBLE_ROWS, Integer.toString(state.getVisibleRows()));
                properties.setProperty(KEY_VISIBLE_COLS, Integer.toString(state.getVisibleCols()));
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                properties.store(writer, "MCFloatingBackpack panel layout");
            }
        } catch (IOException exception) {
            MCFloatingBackpack.LOGGER.warn("Could not save floating panel layout to {}", path, exception);
        }
    }

    private static int parseInt(String value, int fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
