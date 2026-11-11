package pnnh.mcfloatingbackpack.worldmarker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 041: reading and writing the marker file in a real directory. */
class WorldMarkerStoreTest {
    @TempDir
    Path world;

    private Path file() {
        return world.resolve(WorldMarkerStore.RELATIVE_PATH);
    }

    @Test
    void noFileIsMissing() {
        assertEquals(MarkerRead.MISSING, WorldMarkerStore.read(file()));
    }

    @Test
    void whatIsWrittenReadsBack_andTheDirectoryIsMade() throws IOException {
        WorldMarkerStore.write(file(), new WorldMarker(3, "0.2.0"));
        assertEquals(new MarkerRead.Valid(new WorldMarker(3, "0.2.0")), WorldMarkerStore.read(file()));
    }

    @Test
    void anExistingFileIsReplaced_andNoTemporaryFileIsLeft() throws IOException {
        WorldMarkerStore.write(file(), new WorldMarker(1, "0.1.0"));
        WorldMarkerStore.write(file(), new WorldMarker(2, "0.2.0"));
        assertEquals(new MarkerRead.Valid(new WorldMarker(2, "0.2.0")), WorldMarkerStore.read(file()));
        try (Stream<Path> files = Files.list(file().getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void garbageIsCorrupt() throws IOException {
        Files.createDirectories(file().getParent());
        Files.writeString(file(), "this is not a marker", StandardCharsets.UTF_8);
        assertInstanceOf(MarkerRead.Corrupt.class, WorldMarkerStore.read(file()));
    }

    @Test
    void bytesThatAreNotUtf8AreCorrupt() throws IOException {
        Files.createDirectories(file().getParent());
        Files.write(file(), new byte[] {(byte) 0xC3, (byte) 0x28, (byte) 0xFF, (byte) 0xFE});
        assertInstanceOf(MarkerRead.Corrupt.class, WorldMarkerStore.read(file()));
    }

    @Test
    void aDirectoryWhereTheFileShouldBeCannotBeRead() throws IOException {
        Files.createDirectories(file());
        MarkerRead read = WorldMarkerStore.read(file());
        assertTrue(assertInstanceOf(MarkerRead.Corrupt.class, read).reason().startsWith("unreadable"));
    }

    @Test
    void aReadOnlyDirectoryFailsTheWriteWithAnIoException() throws IOException {
        Path directory = Files.createDirectories(file().getParent());
        assumeFalse(!directory.toFile().setWritable(false) || Files.isWritable(directory), "this account can write to a read-only directory");
        try {
            assertThrows(IOException.class, () -> WorldMarkerStore.write(file(), new WorldMarker(1, null)));
        } finally {
            directory.toFile().setWritable(true);
        }
    }
}
