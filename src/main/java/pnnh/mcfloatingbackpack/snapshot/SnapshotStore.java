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
import java.util.concurrent.ConcurrentHashMap;
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
 * contents. 057: the kind of a snapshot is the suffix of its name ({@code <time>-t.dat} timed, {@code <time>.dat} change,
 * {@code <time>-u.dat} before an update; the name decides, the header says the same) and the file format is version 2; a file of a
 * higher version, or with a name this version does not know, is not listed and never deleted. Next to the folders is
 * {@code state.properties} ({@link SnapshotState}). Outside the world folder on purpose: putting an old world folder back must not take the snapshots with it. A snapshot
 * is written to a temporary file and then renamed, so there is never a half-written one. A file that cannot be read is skipped and
 * logged; nothing here throws for a bad file.
 *
 * <p>Methods that write are meant for the snapshot thread, methods that read or delete for the main thread once the writes
 * are done ({@link SnapshotService#flushAndWait}).
 */
public final class SnapshotStore {
    /** The version of the file format this code writes (057: 2; 1 had no kind and is read as the ordinary kind). */
    static final int VERSION = 2;
    private static final String TEMP_EXTENSION = ".tmp";
    public static final String STATE_FILE = "state.properties";

    /** Files of a higher format version that have been mentioned in the log this run (once each). */
    private final Set<Path> futureLogged = ConcurrentHashMap.newKeySet();

    private final Path root;
    /**
     * What each snapshot file says about itself, so listing does not read every file again. A snapshot file is never changed
     * after it is written and its name holds its time, so an entry cannot go stale; one for a file that is gone is dropped when
     * the folder is next listed.
     */
    private final Map<Path, SnapshotMeta> metaCache = new ConcurrentHashMap<>();

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

    /**
     * Writes one snapshot (of the kind in {@code meta}), then deletes this backpack's snapshots of that kind beyond the newest
     * {@link SnapshotKind#keep()} of it; the other kinds are not touched.
     *
     * @return the time it was written under
     */
    public long write(SnapshotMeta meta, Tag contents) throws IOException {
        Path dir = folderOf(meta.uuid());
        Files.createDirectories(dir);
        Set<Long> existing = new HashSet<>(times(dir));
        long time = SnapshotFiles.freeTime(meta.time(), existing);

        CompoundTag tag = header(meta, time);
        tag.put("contents", contents);
        writeAtomic(tag, dir.resolve(SnapshotFiles.fileName(time, meta.kind())));

        pruneKind(meta.uuid(), dir, meta.kind());
        return time;
    }

    /** The header of a snapshot file: everything but the contents. */
    private static CompoundTag header(SnapshotMeta meta, long time) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putUUID("uuid", meta.uuid());
        tag.putUUID("owner", meta.owner());
        tag.putString("ownerName", meta.ownerName());
        tag.putInt("capacity", meta.capacity());
        tag.putLong("time", time);
        tag.putInt("slots", meta.slots());
        tag.putString("kind", meta.kind().name());
        if (meta.kind() == SnapshotKind.UPGRADE) {
            tag.putString("from_mod_version", meta.fromVersion());
            tag.putString("to_mod_version", meta.toVersion());
        }
        return tag;
    }

    /** Writes to a temporary file and renames it, so a half-written file never has the real name. */
    private static void writeAtomic(CompoundTag tag, Path file) throws IOException {
        Path temp = file.resolveSibling(file.getFileName() + TEMP_EXTENSION);
        NbtIo.writeCompressed(tag, temp);
        try {
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Deletes the snapshots of one kind beyond what a backpack keeps of it (only readable ones of a known version count or go). */
    private void pruneKind(UUID uuid, Path dir, SnapshotKind kind) {
        Set<Path> present = new HashSet<>();
        List<SnapshotRetention.Entry> entries = new ArrayList<>();
        for (SnapshotMeta meta : versions(uuid, dir, present)) {
            entries.add(new SnapshotRetention.Entry(meta.time(), meta.kind()));
        }
        for (long old : SnapshotRetention.toDelete(entries, kind)) {
            delete(uuid, old);
        }
    }

    // ---------------------------------------------------------------- pinning (a change of the mod's version)

    /**
     * Makes the snapshot of this time one from before an update: reads its file, changes only the header (the kind, the two mod
     * versions), writes it under the name of that kind, reads it back and compares the contents with what was read; only if they are
     * the same is the old file deleted, otherwise the new file goes and the old one stays. The contents are carried as they are and
     * never decoded.
     *
     * @return true if the snapshot is now (or already was) one from before an update
     */
    public boolean pin(UUID uuid, long time, String from, String to) {
        Path dir = folders().get(uuid);
        if (dir == null) {
            return false;
        }
        Path old = fileOf(dir, time);
        if (old == null) {
            return false;
        }
        SnapshotKind kind = SnapshotFiles.kindOf(old.getFileName().toString());
        if (kind == SnapshotKind.UPGRADE) {
            return updateTarget(uuid, time, to);
        }
        Optional<CompoundTag> read = readTag(old);
        if (read.isEmpty() || !knownVersion(read.get(), old) || !read.get().contains("contents")) {
            return false;
        }
        CompoundTag tag = read.get();
        Tag contents = tag.get("contents");
        tag.putInt("version", VERSION);
        tag.putString("kind", SnapshotKind.UPGRADE.name());
        tag.putString("from_mod_version", from);
        tag.putString("to_mod_version", to);
        Path target = dir.resolve(SnapshotFiles.fileName(time, SnapshotKind.UPGRADE));
        try {
            writeAtomic(tag, target);
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.error("Could not pin the backpack snapshot {}/{}: {}", uuid, time, e.toString());
            deleteQuietly(target);
            return false;
        }
        Optional<CompoundTag> check = readTag(target);
        if (check.isEmpty() || !contents.equals(check.get().get("contents"))) {
            MCFloatingBackpack.LOGGER.error(
                    "The pinned copy of the backpack snapshot {}/{} does not hold what the original does; the original is kept and the copy is removed", uuid, time);
            deleteQuietly(target);
            return false;
        }
        metaCache.remove(old);
        deleteQuietly(old);
        return true;
    }

    /** Notes a newer mod version on a snapshot that is already one from before an update (written to a temporary file, checked, then renamed over it). */
    public boolean updateTarget(UUID uuid, long time, String to) {
        Path dir = folders().get(uuid);
        Path file = dir == null ? null : fileOf(dir, time);
        if (file == null) {
            return false;
        }
        Optional<CompoundTag> read = readTag(file);
        if (read.isEmpty() || !knownVersion(read.get(), file) || !read.get().contains("contents")) {
            return false;
        }
        CompoundTag tag = read.get();
        Tag contents = tag.get("contents");
        tag.putString("to_mod_version", to);
        Path temp = file.resolveSibling(file.getFileName() + TEMP_EXTENSION);
        try {
            NbtIo.writeCompressed(tag, temp);
            Optional<CompoundTag> check = readTag(temp);
            if (check.isEmpty() || !contents.equals(check.get().get("contents"))) {
                MCFloatingBackpack.LOGGER.error("Could not note the new mod version on the backpack snapshot {}/{}: the copy differs; left as it was", uuid, time);
                deleteQuietly(temp);
                return false;
            }
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            metaCache.remove(file);
            return true;
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.error("Could not note the new mod version on the backpack snapshot {}/{}: {}", uuid, time, e.toString());
            deleteQuietly(temp);
            return false;
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.warn("Could not delete {}: {}", file, e.toString());
        }
    }

    /** The file of the snapshot of this time in a backpack's folder, whatever its kind; null if there is none. */
    private static Path fileOf(Path dir, long time) {
        if (!Files.isDirectory(dir)) {
            return null;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .filter(file -> SnapshotFiles.parseTime(file.getFileName().toString()) == time)
                    .findFirst()
                    .orElse(null);
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.warn("Could not list the backpack snapshots in {}: {}", dir, e.toString());
            return null;
        }
    }

    /** Whether the header is of a format version this code knows (1 or 2); a higher one is mentioned in the log, once. */
    private boolean knownVersion(CompoundTag tag, Path file) {
        int version = tag.contains("version") ? tag.getInt("version") : 1;
        if (version > VERSION) {
            if (futureLogged.add(file)) {
                MCFloatingBackpack.LOGGER.info(
                        "The backpack snapshot {} was written by a newer version of this mod (format {}, this one reads up to {}): it is not listed and is left alone", file, version, VERSION);
            }
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- state and sizes

    /** What the snapshot folder remembers between runs; empty when there is no readable record. Main thread. */
    public Optional<SnapshotState> readState() {
        Path file = root.resolve(STATE_FILE);
        try {
            if (!Files.isRegularFile(file)) {
                return Optional.empty();
            }
            return SnapshotState.parse(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException e) {
            MCFloatingBackpack.LOGGER.warn("Could not read {}: {}", file, e.toString());
            return Optional.empty();
        }
    }

    /** Writes the state (a temporary file, then a rename). Main thread. @return false if it could not be written */
    public boolean writeState(SnapshotState state) {
        Path file = root.resolve(STATE_FILE);
        Path temp = root.resolve(STATE_FILE + TEMP_EXTENSION);
        try {
            Files.createDirectories(root);
            Files.writeString(temp, state.toText(), java.nio.charset.StandardCharsets.UTF_8);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            MCFloatingBackpack.LOGGER.warn("Could not write {}: {}", file, e.toString());
            deleteQuietly(temp);
            return false;
        }
    }

    /** Every snapshot file this version knows by name, with its size, for the capacity clean-up. */
    public List<SnapshotCapacity.File> allFiles() {
        List<SnapshotCapacity.File> out = new ArrayList<>();
        for (Map.Entry<UUID, Path> folder : folders().entrySet()) {
            try (Stream<Path> files = Files.list(folder.getValue())) {
                files.forEach(file -> {
                    String name = file.getFileName().toString();
                    long time = SnapshotFiles.parseTime(name);
                    SnapshotKind kind = SnapshotFiles.kindOf(name);
                    if (time >= 0 && kind != null) {
                        out.add(new SnapshotCapacity.File(folder.getKey(), time, kind, sizeOf(file)));
                    }
                });
            } catch (IOException e) {
                MCFloatingBackpack.LOGGER.warn("Could not list the backpack snapshots in {}: {}", folder.getValue(), e.toString());
            }
        }
        return out;
    }

    private static long sizeOf(Path file) {
        try {
            return Files.size(file);
        } catch (IOException e) {
            return 0L;
        }
    }

    /** The size of the snapshot file of this time, or 0 if there is none. */
    public long sizeOfSnapshot(UUID uuid, long time) {
        Path dir = folders().get(uuid);
        Path file = dir == null ? null : fileOf(dir, time);
        return file == null ? 0L : sizeOf(file);
    }

    /**
     * Deletes a snapshot for the capacity clean-up, but only after reading its header and finding the format one this version knows.
     *
     * @return the bytes freed, or -1 if it was left alone (gone, unreadable, or of a newer format)
     */
    public long deleteIfKnown(UUID uuid, long time) {
        Path dir = folders().get(uuid);
        Path file = dir == null ? null : fileOf(dir, time);
        if (file == null) {
            return -1;
        }
        Optional<CompoundTag> tag = readTag(file);
        if (tag.isEmpty() || !knownVersion(tag.get(), file)) {
            return -1;
        }
        long bytes = sizeOf(file);
        metaCache.remove(file);
        deleteQuietly(file);
        return bytes;
    }

    // ---------------------------------------------------------------- reading

    /** Every backpack that has at least one readable snapshot: its newest snapshot and how many it has. */
    public List<Group> groups() {
        List<Group> groups = new ArrayList<>();
        Set<Path> present = new HashSet<>();
        for (Map.Entry<UUID, Path> folder : folders().entrySet()) {
            List<SnapshotMeta> versions = versions(folder.getKey(), folder.getValue(), present);
            if (!versions.isEmpty()) {
                groups.add(new Group(versions.get(0), versions.size()));
            }
        }
        metaCache.keySet().retainAll(present);
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
        if (dir == null) {
            return new ArrayList<>();
        }
        Set<Path> present = new HashSet<>();
        List<SnapshotMeta> versions = versions(uuid, dir, present);
        metaCache.keySet().removeIf(file -> dir.equals(file.getParent()) && !present.contains(file));
        return versions;
    }

    private List<SnapshotMeta> versions(UUID uuid, Path dir, Set<Path> present) {
        List<Long> times = times(dir);
        times.sort(Comparator.reverseOrder());
        List<SnapshotMeta> versions = new ArrayList<>();
        for (long time : times) {
            Path file = fileOf(dir, time);
            if (file == null) {
                continue;
            }
            present.add(file);
            SnapshotMeta cached = metaCache.get(file);
            if (cached != null) {
                versions.add(cached);
                continue;
            }
            SnapshotKind kind = SnapshotFiles.kindOf(file.getFileName().toString());
            readTag(file).ifPresent(tag -> {
                if (kind == null || !knownVersion(tag, file)) {
                    return;
                }
                try {
                    SnapshotMeta meta = new SnapshotMeta(
                            tag.getUUID("uuid"),
                            tag.getUUID("owner"),
                            tag.getString("ownerName"),
                            tag.getInt("capacity"),
                            time,
                            tag.getInt("slots"),
                            kind,
                            kind == SnapshotKind.UPGRADE ? tag.getString("from_mod_version") : "",
                            kind == SnapshotKind.UPGRADE ? tag.getString("to_mod_version") : "");
                    metaCache.put(file, meta);
                    versions.add(meta);
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
        Path file = fileOf(dir, time);
        if (file == null) {
            return Optional.empty();
        }
        return readTag(file)
                .filter(tag -> knownVersion(tag, file) && tag.contains("contents"))
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
            if (dir == null) {
                return false;
            }
            Path file = fileOf(dir, time);
            if (file == null) {
                return false;
            }
            metaCache.remove(file);
            return Files.deleteIfExists(file);
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
        // only the snapshots this version can list: a file of a newer format is never deleted
        for (SnapshotMeta meta : versions(uuid)) {
            if (delete(uuid, meta.time())) {
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
