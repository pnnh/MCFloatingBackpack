package pnnh.mcfloatingbackpack.snapshot;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.snapshot.SnapshotListing.Group;

/**
 * 030: the snapshot files: {@code <server directory>/mcfloatingbackpack/snapshots/<world folder>/<backpack uuid>-<id>/<time>.dat}, each a
 * compressed NBT file with the backpack's id, who last used it, its capacity, when, how many slots held something, and the
 * contents. Outside the world folder on purpose: putting an old world folder back must not take the snapshots with it. A snapshot
 * is written to a temporary file and then renamed, so there is never a half-written one. A file that cannot be read is skipped and
 * logged; nothing here throws for a bad file.
 *
 * <p>Methods that write are meant for the snapshot thread, methods that read or delete for the main thread once the writes
 * are done ({@link SnapshotService#flushAndWait}).
 */
public final class SnapshotStore {
    private static final int VERSION = 1;
    private static final String TEMP_EXTENSION = ".tmp";

    private final Path root;

    public SnapshotStore(Path root) {
        this.root = root;
    }

    /** The root for this server's world: the instance directory, then the world's folder name. */
    public static Path rootFor(MinecraftServer server) {
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
        String name = world == null ? "world" : world.toString().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return server.getServerDirectory().resolve("mcfloatingbackpack").resolve("snapshots").resolve(name);
    }

    public Path root() {
        return root;
    }

    // ---------------------------------------------------------------- writing

    /** Writes one snapshot, then deletes this backpack's snapshots beyond the newest {@code keep}. @return the time it was written under */
    public long write(SnapshotMeta meta, Tag contents, int keep) throws IOException {
        Path dir = folderOf(meta.uuid());
        Files.createDirectories(dir);
        Set<Long> existing = new HashSet<>(times(dir));
        long time = SnapshotFiles.freeTime(meta.time(), existing);

        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putUUID("uuid", meta.uuid());
        tag.putUUID("owner", meta.owner());
        tag.putString("ownerName", meta.ownerName());
        tag.putInt("capacity", meta.capacity());
        tag.putLong("time", time);
        tag.putInt("slots", meta.slots());
        tag.put("contents", contents);

        Path file = dir.resolve(SnapshotFiles.fileName(time));
        Path temp = dir.resolve(SnapshotFiles.fileName(time) + TEMP_EXTENSION);
        NbtIo.writeCompressed(tag, temp);
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }

        existing.add(time);
        for (long old : SnapshotPolicy.toPrune(new ArrayList<>(existing), keep)) {
            Files.deleteIfExists(dir.resolve(SnapshotFiles.fileName(old)));
        }
        return time;
    }

    // ---------------------------------------------------------------- reading

    /** Every backpack that has at least one readable snapshot: its newest snapshot and how many it has. */
    public List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        for (Map.Entry<UUID, Path> folder : folders().entrySet()) {
            List<SnapshotMeta> versions = versions(folder.getKey(), folder.getValue());
            if (!versions.isEmpty()) {
                groups.add(new Group(versions.get(0), versions.size()));
            }
        }
        return groups;
    }

    /** The backpacks that have a folder here (a folder whose name is not a UUID is not ours and is ignored). */
    public List<UUID> backpacks() {
        return new ArrayList<>(folders().keySet());
    }

    /** Every backpack's folder, found with one listing of the root. */
    private Map<UUID, Path> folders() {
        Map<UUID, Path> found = new LinkedHashMap<>();
        if (!Files.isDirectory(root)) {
            return found;
        }
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory).forEach(dir -> {
                UUID uuid = SnapshotFiles.parseFolder(dir.getFileName().toString());
                if (uuid != null) {
                    found.putIfAbsent(uuid, dir);
                }
            });
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.warn("Could not list the backpack snapshots in {}: {}", root, e.toString());
        }
        return found;
    }

    /**
     * The folder of one backpack: the one that exists (a folder from the first version, named by the UUID alone, is renamed to
     * the current form if it can be), or else where it is to be created. Only the UUID in a folder's name counts.
     */
    private Path folderOf(UUID uuid) {
        Path wanted = root.resolve(SnapshotFiles.folderName(uuid));
        Path existing = folders().get(uuid);
        if (existing == null || existing.equals(wanted)) {
            return wanted;
        }
        if (!Files.exists(wanted)) {
            try {
                Files.move(existing, wanted);
                return wanted;
            } catch (IOException e) {
                MCFloatingBackpack.LOGGER.warn("Could not rename the snapshot folder {} to {}: {}", existing, wanted, e.toString());
            }
        }
        return existing;
    }

    /** One backpack's readable snapshots, the newest first. */
    public List<SnapshotMeta> versions(UUID uuid) {
        Path dir = folders().get(uuid);
        return dir == null ? new ArrayList<>() : versions(uuid, dir);
    }

    private List<SnapshotMeta> versions(UUID uuid, Path dir) {
        List<Long> times = times(dir);
        times.sort(Comparator.reverseOrder());
        List<SnapshotMeta> versions = new ArrayList<>();
        for (long time : times) {
            readTag(dir.resolve(SnapshotFiles.fileName(time))).ifPresent(tag -> {
                try {
                    versions.add(new SnapshotMeta(
                            tag.getUUID("uuid"),
                            tag.getUUID("owner"),
                            tag.getString("ownerName"),
                            tag.getInt("capacity"),
                            time,
                            tag.getInt("slots")));
                } catch (RuntimeException e) {
                    MCFloatingBackpack.LOGGER.warn("Skipping an unreadable backpack snapshot {}/{}: {}", uuid, time, e.toString());
                }
            });
        }
        return versions;
    }

    /** The contents of one snapshot as written, or empty if it cannot be read. */
    public Optional<Tag> readContents(UUID uuid, long time) {
        Path dir = folders().get(uuid);
        if (dir == null) {
            return Optional.empty();
        }
        return readTag(dir.resolve(SnapshotFiles.fileName(time)))
                .filter(tag -> tag.contains("contents"))
                .map(tag -> tag.get("contents"));
    }

    private Optional<CompoundTag> readTag(Path file) {
        try {
            return Optional.of(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
        } catch (IOException | RuntimeException e) {
            MCFloatingBackpack.LOGGER.warn("Could not read the backpack snapshot {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------- deleting

    /** @return false if there was no such snapshot or it could not be deleted */
    public boolean delete(UUID uuid, long time) {
        try {
            Path dir = folders().get(uuid);
            return dir != null && Files.deleteIfExists(dir.resolve(SnapshotFiles.fileName(time)));
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.warn("Could not delete the backpack snapshot {}/{}: {}", uuid, time, e.toString());
            return false;
        }
    }

    /** Deletes all of one backpack's snapshots (and its folder when empty). @return how many files were deleted */
    public int deleteAll(UUID uuid) {
        Path dir = folders().get(uuid);
        if (dir == null) {
            return 0;
        }
        int deleted = 0;
        for (long time : times(dir)) {
            if (delete(uuid, time)) {
                deleted++;
            }
        }
        try {
            Files.deleteIfExists(dir);
        } catch (IOException e) {
            // other files are in it (a stray or temporary file): leave the folder
        }
        return deleted;
    }

    // ---------------------------------------------------------------- helpers

    private static List<Long> times(Path dir) {
        List<Long> times = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return times;
        }
        try (Stream<Path> files = Files.list(dir)) {
            files.forEach(file -> {
                long time = SnapshotFiles.parseTime(file.getFileName().toString());
                if (time >= 0) {
                    times.add(time);
                }
            });
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.warn("Could not list the backpack snapshots in {}: {}", dir, e.toString());
        }
        return times;
    }
}
