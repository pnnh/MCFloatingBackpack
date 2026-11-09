package pnnh.mcfloatingbackpack.snapshot;

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
import pnnh.mcfloatingbackpack.snapshot.SnapshotScheduler.Due;

/**
 * 030: keeps a snapshot of each backpack that is used, outside the world. The server's main thread notes that a backpack changed
 * (it keeps the backpack's contents object, which is never changed afterwards, so this costs a map entry), and when the
 * backpack has been quiet for a while, or its player leaves, or the server stops, or a command is about to read the files, it
 * encodes the contents on the main thread and hands the encoded data to one background thread, which writes the file. A write that
 * fails is logged and goes no further: a snapshot is a backup, never something the game depends on.
 *
 * <p>The background thread skips a snapshot whose contents are the same as the backpack's newest one (compared by hash). It
 * is the only user of {@link #lastHash}. Everything else here runs on the server's main thread.
 */
public final class SnapshotService {
    private static SnapshotService current;

    /** What waits for a snapshot: the contents as they were last saved, who used the backpack, and its capacity. */
    private record Change(BackpackContents contents, UUID player, String playerName, int capacity) {
    }

    private final MinecraftServer server;
    private final SnapshotStore store;
    private final SnapshotScheduler<Change> scheduler = new SnapshotScheduler<>();
    /** Backpacks seen this run: a backpack's first use gets a baseline snapshot even if its contents never change. */
    private final Set<UUID> seen = new HashSet<>();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "mcfloatingbackpack-snapshots");
        thread.setDaemon(true);
        return thread;
    });
    /** Hash of the contents of each backpack's newest snapshot; only the writer thread touches it. */
    private final Map<UUID, Integer> lastHash = new HashMap<>();

    private SnapshotService(MinecraftServer server, SnapshotStore store) {
        this.server = server;
        this.store = store;
    }

    // ---------------------------------------------------------------- the server's one service

    public static void start(MinecraftServer server) {
        stop();
        current = new SnapshotService(server, new SnapshotStore(SnapshotStore.rootFor(server)));
        MCFloatingBackpack.LOGGER.info("Backpack snapshots go to {}", current.store.root());
    }

    public static void stop() {
        SnapshotService service = current;
        current = null;
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

    // ---------------------------------------------------------------- noting changes (main thread)

    /** A backpack was used: the first time this run, make sure it has a snapshot of what it holds now. */
    public void noteUse(ServerPlayer player, UUID backpack, int capacity, BackpackContents contents) {
        if (contents != null && seen.add(backpack)) {
            mark(player, backpack, capacity, contents);
        }
    }

    /** A backpack was saved with new contents. */
    public void noteSaved(ServerPlayer player, UUID backpack, int capacity, BackpackContents contents) {
        if (contents != null) {
            seen.add(backpack);
            mark(player, backpack, capacity, contents);
        }
    }

    private void mark(ServerPlayer player, UUID backpack, int capacity, BackpackContents contents) {
        scheduler.mark(
                backpack,
                new Change(contents, player.getUUID(), player.getGameProfile().getName(), capacity),
                System.currentTimeMillis());
    }

    /** Called about once a second: writes what has been quiet long enough. */
    public void tick() {
        if (scheduler.isEmpty()) {
            return;
        }
        write(scheduler.due(System.currentTimeMillis()));
    }

    /** A player left: write that player's waiting changes now. */
    public void onLogout(UUID player) {
        write(scheduler.drainWhere(change -> change.player().equals(player)));
    }

    /** Writes everything that waits and waits (a little) for the files to be there: for the commands that read them. */
    public void flushAndWait() {
        write(scheduler.drainAll());
        try {
            writer.submit(() -> { }).get(10, TimeUnit.SECONDS);
        } catch (Exception e) {
            MCFloatingBackpack.LOGGER.warn("Gave up waiting for the backpack snapshots to be written: {}", e.toString());
        }
    }

    /** A backpack's snapshots were deleted by a command: its next snapshot must be written even if the contents are the same. */
    public void forget(UUID backpack) {
        writer.execute(() -> lastHash.remove(backpack));
    }

    private void close() {
        write(scheduler.drainAll());
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

    private void write(List<Due<Change>> due) {
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
            SnapshotMeta meta = new SnapshotMeta(item.uuid(), change.player(), change.playerName(), change.capacity(), now, slots);
            writer.execute(() -> writeOne(meta, encoded));
        }
    }

    /** On the writer thread. */
    private void writeOne(SnapshotMeta meta, Tag contents) {
        try {
            int hash = contents.hashCode();
            Integer known = lastHash.get(meta.uuid());
            if (known == null) {
                List<SnapshotMeta> versions = store.versions(meta.uuid());
                if (!versions.isEmpty()) {
                    known = store.readContents(meta.uuid(), versions.get(0).time()).map(Tag::hashCode).orElse(null);
                }
            }
            if (known != null && known == hash) {
                lastHash.put(meta.uuid(), hash);
                return;
            }
            store.write(meta, contents, SnapshotPolicy.KEEP);
            lastHash.put(meta.uuid(), hash);
        } catch (Exception e) {
            MCFloatingBackpack.LOGGER.error("Could not write a snapshot of backpack {}: {}", meta.uuid(), e.toString());
        }
    }
}
