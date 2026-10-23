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
