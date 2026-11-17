package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.snapshot.SnapshotNotices.Direction;
import pnnh.mcfloatingbackpack.snapshot.SnapshotNotices.Pending;
import pnnh.mcfloatingbackpack.snapshot.SnapshotScheduler.Due;
import pnnh.mcfloatingbackpack.version.ModVersion;

/**
 * 030: keeps a snapshot of each backpack that is used, outside the world. The server's main thread notes that a backpack changed
 * (it keeps the backpack's contents object, which is never changed afterwards, so this costs a map entry), and when the
 * backpack has been quiet for a while, or its player leaves, or the server stops, or a command is about to read the files, it
 * encodes the contents on the main thread and hands the encoded data to one background thread, which writes the file. A write that
 * fails is logged and goes no further: a snapshot is a backup, never something the game depends on.
 *
 * <p>057: those are the snapshots of kind {@link SnapshotKind#CHANGE}. Every 15 minutes the backpacks that changed since the last round
 * also get a {@link SnapshotKind#TIMED} one. When the server starts the mod's version is compared with the one the snapshot folder
 * remembers, and if it changed the newest snapshot of each backpack is pinned as one from before the update
 * ({@link SnapshotKind#UPGRADE}). After each write the total size is checked against the limit. With the config switch off nothing
 * is written or pinned, but the existing files can still be listed and restored.
 *
 * <p>The background thread skips a snapshot whose contents are the same as the newest one of the same kind of that backpack
 * (compared by hash). It is the only user of {@link #lastHash} and {@link #totalBytes}. Everything else here runs on the server's
 * main thread.
 */
public final class SnapshotService {
    private static SnapshotService current;

    /** What waits for a snapshot: the contents as they were last saved, who used the backpack, and its capacity. */
    private record Change(BackpackContents contents, UUID player, String playerName, int capacity) {
    }

    /** The newest snapshot of which kind of which backpack a hash belongs to. */
    private record HashKey(UUID uuid, SnapshotKind kind) {
    }

    private final MinecraftServer server;
    private final SnapshotStore store;
    private final SnapshotSettings settings;
    private final SnapshotScheduler<Change> scheduler = new SnapshotScheduler<>();
    private final TimedWindow<Change> timed;
    /** Backpacks seen this run: a backpack's first use gets a baseline snapshot even if its contents never change. */
    private final Set<UUID> seen = new HashSet<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mcfloatingbackpack-snapshots");
        thread.setDaemon(true);
        return thread;
    });
    /** Hash of the contents of each backpack's newest snapshot of each kind; only the writer thread touches it. */
    private final Map<HashKey, Integer> lastHash = new HashMap<>();
    /** What the snapshot files take, as far as the writer thread knows (null: not worked out yet); only the writer thread touches it. */
    private Long totalBytes;
    private final Throttle capacityWarning = new Throttle(Throttle.HOUR_MS);

    private SnapshotService(MinecraftServer server, SnapshotStore store, SnapshotSettings settings) {
        this.server = server;
        this.store = store;
        this.settings = settings;
        this.timed = new TimedWindow<>(settings.timedIntervalMs());
    }

    // ---------------------------------------------------------------- the server's one service

    public static void start(MinecraftServer server) {
        stop();
        SnapshotSettings settings = SnapshotConfig.settings();
        current = new SnapshotService(server, new SnapshotStore(SnapshotStore.rootFor(server)), settings);
        MCFloatingBackpack.LOGGER.info("Backpack snapshots go to {}", current.store.root());
        if (settings.clamped()) {
            MCFloatingBackpack.LOGGER.warn(
                    "snapshots.maxTotalMb is outside {} - {}; using {}", SnapshotSettings.MIN_MB, SnapshotSettings.MAX_MB, settings.maxTotalMb());
        }
        if (!settings.enabled()) {
            MCFloatingBackpack.LOGGER.warn("Backpack snapshots are turned off in the server config: none are written and none are protected");
        }
        current.checkVersion();
    }

    public static void stop() {
        SnapshotService service = current;
        current = null;
        SnapshotNotices.end();
        if (service != null) {
            service.close();
        }
    }

    /** @return the running service, or empty before the server has started or after it stopped */
    public static Optional<SnapshotService> get() {
        return Optional.ofNullable(current);
    }

    public SnapshotStore store() {
        return store;
    }

    public SnapshotSettings settings() {
        return settings;
    }

    // ---------------------------------------------------------------- a change of the mod's version (main thread, at the start)

    /**
     * Compares the mod's version with the one the folder remembers. A change (an update or a downgrade) pins the newest snapshot of
     * each backpack as one from before the update and starts a new round of notices; no record (a new folder, a damaged file) only
     * writes the version down; the same version leaves the state as it is, and if the last change is not yet told to everybody the
     * notice goes on being given at login.
     */
    private void checkVersion() {
        String version = ModVersion.current();
        String now = version == null || version.isBlank() ? "unknown" : version.strip();
        Optional<SnapshotState> read = store.readState();
        if (read.isEmpty()) {
            SnapshotState fresh = new SnapshotState(now, 1, "", 0, List.of());
            store.writeState(fresh);
            SnapshotNotices.begin(store, fresh, null);
            MCFloatingBackpack.LOGGER.info("No record of the mod's version in the snapshot folder; wrote {}", now);
            return;
        }
        SnapshotState state = read.get();
        if (state.lastModVersion().equals(now)) {
            Pending pending = state.fromVersion().isEmpty()
                    ? null
                    : new Pending(state.changeId(), state.fromVersion(), now, state.pinned(), SnapshotNotices.directionOf(state.fromVersion(), now, settings.enabled()));
            SnapshotNotices.begin(store, state, pending);
            return;
        }
        int pinned = settings.enabled() ? pinNewest(state.lastModVersion(), now) : 0;
        SnapshotState next = new SnapshotState(now, state.changeId() + 1, state.lastModVersion(), pinned, List.of());
        store.writeState(next);
        SnapshotNotices.begin(
                store, next, new Pending(next.changeId(), next.fromVersion(), now, pinned, SnapshotNotices.directionOf(next.fromVersion(), now, settings.enabled())));
        MCFloatingBackpack.LOGGER.warn(
                "The mod's version changed from {} to {}: {}", state.lastModVersion(), now,
                settings.enabled() ? "the newest snapshot of " + pinned + " backpack(s) is kept as one from before the update" : "snapshots are off, nothing was protected");
    }

    /** @return how many backpacks had a snapshot pinned (or its new version noted) */
    private int pinNewest(String from, String to) {
        int pinned = 0;
        for (UUID backpack : store.backpacks()) {
            List<SnapshotRetention.Entry> entries = new ArrayList<>();
            for (SnapshotMeta meta : store.versions(backpack)) {
                entries.add(new SnapshotRetention.Entry(meta.time(), meta.kind()));
            }
            UpgradePlan.Plan plan = UpgradePlan.plan(entries);
            boolean done = switch (plan.action()) {
                case PIN -> store.pin(backpack, plan.target(), from, to);
                case UPDATE_TARGET -> store.updateTarget(backpack, plan.target(), to);
                case NONE -> false;
            };
            if (done) {
                pinned++;
                for (long old : plan.drop()) {
                    store.delete(backpack, old);
                }
            }
        }
        return pinned;
    }

    // ---------------------------------------------------------------- noting changes (main thread)

    /** A backpack was used: the first time this run, make sure it has a snapshot of what it holds now. */
    public void noteUse(ServerPlayer player, UUID backpack, int capacity, BackpackContents contents) {
        if (settings.enabled() && contents != null && seen.add(backpack)) {
            mark(player, backpack, capacity, contents);
        }
    }

    /** A backpack was saved with new contents. */
    public void noteSaved(ServerPlayer player, UUID backpack, int capacity, BackpackContents contents) {
        if (settings.enabled() && contents != null) {
            seen.add(backpack);
            mark(player, backpack, capacity, contents);
        }
    }

    private void mark(ServerPlayer player, UUID backpack, int capacity, BackpackContents contents) {
        long now = System.currentTimeMillis();
        Change change = new Change(contents, player.getUUID(), player.getGameProfile().getName(), capacity);
        scheduler.mark(backpack, change, now);
        timed.mark(backpack, change, now);
    }

    /** Called about once a second: writes what has been quiet long enough, and the timed round when its time has come. */
    public void tick() {
        if (!settings.enabled() || (scheduler.isEmpty() && timed.isEmpty())) {
            return;
        }
        long now = System.currentTimeMillis();
        write(scheduler.due(now), SnapshotKind.CHANGE);
        List<TimedWindow.Due<Change>> round = timed.due(now);
        if (!round.isEmpty()) {
            List<Due<Change>> asDue = new ArrayList<>();
            for (TimedWindow.Due<Change> item : round) {
                asDue.add(new Due<>(item.uuid(), item.value()));
            }
            write(asDue, SnapshotKind.TIMED);
        }
    }

    /** A player left: write that player's waiting changes now. */
    public void onLogout(UUID player) {
        write(scheduler.drainWhere(change -> change.player().equals(player)), SnapshotKind.CHANGE);
    }

    /** Writes everything that waits and waits (a little) for the files to be there: for the commands that read them. */
    public void flushAndWait() {
        write(scheduler.drainAll(), SnapshotKind.CHANGE);
        try {
            writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            MCFloatingBackpack.LOGGER.warn("Gave up waiting for the backpack snapshots to be written: {}", e.toString());
        }
    }

    /** A backpack's snapshots were deleted by a command: its next snapshot must be written even if the contents are the same. */
    public void forget(UUID backpack) {
        writer.execute(() -> {
            for (SnapshotKind kind : SnapshotKind.values()) {
                lastHash.remove(new HashKey(backpack, kind));
            }
            totalBytes = null;
        });
    }

    private void close() {
        write(scheduler.drainAll(), SnapshotKind.CHANGE);
        writer.shutdown();
        try {
            if (!writer.awaitTermination(30, TimeUnit.SECONDS)) {
                MCFloatingBackpack.LOGGER.warn("Some backpack snapshots were still being written after 30 seconds");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---------------------------------------------------------------- writing

    private void write(List<Due<Change>> due, SnapshotKind kind) {
        long now = System.currentTimeMillis();
        for (Due<Change> item : due) {
            Change change = item.value();
            Tag encoded = BackpackContents.CODEC
                    .encodeStart(server.registryAccess().createSerializationContext(NbtOps.INSTANCE), change.contents())
                    .resultOrPartial(error -> MCFloatingBackpack.LOGGER.error(
                            "Could not encode the contents of backpack {} for a snapshot: {}", item.uuid(), error))
                    .orElse(null);
            if (encoded == null) {
                continue;
            }
            int slots = (int) change.contents().items().stream().filter(stack -> !stack.isEmpty()).count();
            SnapshotMeta meta = new SnapshotMeta(item.uuid(), change.player(), change.playerName(), change.capacity(), now, slots, kind, "", "");
            writer.execute(() -> writeOne(meta, encoded));
        }
    }

    /** On the writer thread. */
    private void writeOne(SnapshotMeta meta, Tag contents) {
        try {
            HashKey key = new HashKey(meta.uuid(), meta.kind());
            int hash = contents.hashCode();
            Integer known = lastHash.get(key);
            if (known == null) {
                for (SnapshotMeta version : store.versions(meta.uuid())) {
                    if (version.kind() == meta.kind()) {
                        known = store.readContents(meta.uuid(), version.time()).map(Tag::hashCode).orElse(null);
                        break;
                    }
                }
            }
            if (known != null && known == hash) {
                lastHash.put(key, hash);
                return;
            }
            long time = store.write(meta, contents);
            lastHash.put(key, hash);
            if (totalBytes != null) {
                totalBytes += store.sizeOfSnapshot(meta.uuid(), time);
            }
            enforceLimit();
        } catch (Exception e) {
            MCFloatingBackpack.LOGGER.error("Could not write a snapshot of backpack {}: {}", meta.uuid(), e.toString());
        }
    }

    /** On the writer thread: if the snapshots together take more than the limit, deletes the oldest timed ones, then the oldest change ones. */
    private void enforceLimit() {
        long max = settings.maxTotalBytes();
        if (totalBytes != null && totalBytes <= max) {
            return;
        }
        List<SnapshotCapacity.File> files = store.allFiles();
        long total = 0;
        for (SnapshotCapacity.File file : files) {
            total += file.bytes();
        }
        totalBytes = total;
        if (total <= max) {
            return;
        }
        SnapshotCapacity.Result result = SnapshotCapacity.victims(files, max);
        long freed = 0;
        for (SnapshotCapacity.File victim : result.victims()) {
            long bytes = store.deleteIfKnown(victim.uuid(), victim.time());
            if (bytes >= 0) {
                freed += bytes;
            }
        }
        totalBytes = total - freed;
        if (totalBytes > max && capacityWarning.tryAcquire(System.currentTimeMillis())) {
            MCFloatingBackpack.LOGGER.warn(
                    "The backpack snapshots take {} MB, over the limit of {} MB, and what is left cannot be deleted for this (the newest snapshot of each backpack and the ones from before an update are kept)",
                    totalBytes / (1024 * 1024), settings.maxTotalMb());
        }
    }
}
