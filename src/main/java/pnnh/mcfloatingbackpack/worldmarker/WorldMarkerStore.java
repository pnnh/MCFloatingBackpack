package pnnh.mcfloatingbackpack.worldmarker;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 041: reads and writes a world's marker file. Only {@code java.nio.file}: no Minecraft types, so it is tested in a temporary directory.
 */
public final class WorldMarkerStore {
    /** Where the marker is, relative to the world's root directory. */
    public static final String RELATIVE_PATH = "mcfloatingbackpack/world_marker.properties";

    private WorldMarkerStore() {
    }

    /** No file is {@link MarkerRead#MISSING}; a file that cannot be read or does not parse is {@link MarkerRead.Corrupt}. */
    public static MarkerRead read(Path file) {
        if (!Files.exists(file)) {
            return MarkerRead.MISSING;
        }
        try {
            return WorldMarker.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException unreadable) {
            return new MarkerRead.Corrupt("unreadable: " + unreadable);
        }
    }

    /** Writes the marker next to a temporary file and moves it over, so that a crash leaves the old file or the new one, not half of one. */
    public static void write(Path file, WorldMarker marker) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(temporary, marker.toText(), StandardCharsets.UTF_8);
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException notAtomic) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException | RuntimeException failed) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // The write already failed; a leftover temporary file is the lesser problem.
            }
            throw failed;
        }
    }
}
