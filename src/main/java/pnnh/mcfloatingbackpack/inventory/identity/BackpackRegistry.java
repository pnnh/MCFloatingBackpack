package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.UnaryOperator;

import com.mojang.serialization.DataResult;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.identity.RegistryRecord.State;

/**
 * 026: the registry, an index of the floating backpacks that have been used, kept in the world's save data so it is saved, and rolled back,
 * together with the players' data. Each record carries a shadow copy of the backpack's contents (the same
 * {@link BackpackContents} object the item holds, so it costs nothing in memory; it is only encoded once more when the world
 * is saved). That is what a later backup reads: the backpacks themselves are scattered over inventories, chests and
 * unloaded chunks, and the only code that changes their contents is ours.
 *
 * <p>Whoever stores a {@code BackpackContents} here (or on the item) must not change it afterwards. Only the server's main
 * thread may use this class. Saving encodes only the records that changed since they were last encoded; loading skips a
 * record it cannot read (or whose internal id is taken) and goes on, so one bad record never stops a world from
 * loading.
 */
public final class BackpackRegistry extends SavedData {
    public static final String NAME = "mcfloatingbackpack_registry";
    private static final int VERSION = 1;

    private final RegistryIndex index = new RegistryIndex();
    private final Map<UUID, BackpackContents> shadow = new HashMap<>();
    /** The encoded form of each record, kept until the record changes. */
    private final Map<UUID, CompoundTag> encoded = new HashMap<>();

    public static BackpackRegistry get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(BackpackRegistry::new, BackpackRegistry::load), NAME);
    }

    public RegistryIndex index() {
        return index;
    }

    public BackpackContents shadowOf(UUID uuid) {
        return shadow.get(uuid);
    }

    /** Registers a new backpack with the contents it has now. */
    public void register(RegistryRecord record, BackpackContents contents) {
        index.register(record);
        if (contents != null) {
            shadow.put(record.uuid(), contents);
        }
        markChanged(record.uuid());
    }

    public void setShadow(UUID uuid, BackpackContents contents) {
        if (contents == null) {
            shadow.remove(uuid);
        } else {
            shadow.put(uuid, contents);
        }
        markChanged(uuid);
    }

    public boolean update(UUID uuid, UnaryOperator<RegistryRecord> change) {
        boolean done = index.update(uuid, change);
        if (done) {
            markChanged(uuid);
        }
        return done;
    }

    /** The record changed (or its shadow did): forget its encoded form and have the world save the registry. */
    public void markChanged(UUID uuid) {
        encoded.remove(uuid);
        setDirty();
    }

    // ---------------------------------------------------------------- save and load

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag records = new ListTag();
        for (RegistryRecord record : index.records()) {
            CompoundTag one = encoded.get(record.uuid());
            if (one == null) {
                one = encode(record, shadow.get(record.uuid()), registries);
                encoded.put(record.uuid(), one);
            }
            records.add(one);
        }
        tag.putInt("version", VERSION);
        tag.put("records", records);
        return tag;
    }

    private static CompoundTag encode(RegistryRecord record, BackpackContents contents, HolderLookup.Provider registries) {
        CompoundTag one = new CompoundTag();
        one.putUUID("uuid", record.uuid());
        one.putUUID("owner", record.owner());
        one.putInt("capacity", record.capacity());
        one.putString("state", record.state().name());
        one.putLong("created", record.createdAt());
        one.putLong("touched", record.touchedAt());
        if (contents != null) {
            DataResult<Tag> result =
                    BackpackContents.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), contents);
            result.resultOrPartial(error -> MCFloatingBackpack.LOGGER.error(
                            "Could not save the contents of backpack {}: {}", record.uuid(), error))
                    .ifPresent(encodedContents -> one.put("contents", encodedContents));
        }
        return one;
    }

    public static BackpackRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        BackpackRegistry registry = new BackpackRegistry();
        if (tag.getInt("version") > VERSION) {
            MCFloatingBackpack.LOGGER.warn(
                    "The backpack registry was written by a newer version ({}); reading what can be read", tag.getInt("version"));
        }
        ListTag records = tag.getList("records", Tag.TAG_COMPOUND);
        int skipped = 0;
        for (int i = 0; i < records.size(); i++) {
            try {
                CompoundTag one = records.getCompound(i);
                RegistryRecord record = new RegistryRecord(
                        one.getUUID("uuid"),
                        one.getUUID("owner"),
                        one.getInt("capacity"),
                        State.valueOf(one.getString("state")),
                        one.getLong("created"),
                        one.getLong("touched"));
                registry.index.register(record);
                if (one.contains("contents")) {
                    BackpackContents.CODEC
                            .parse(registries.createSerializationContext(NbtOps.INSTANCE), one.get("contents"))
                            .resultOrPartial(error -> MCFloatingBackpack.LOGGER.warn(
                                    "Could not read the contents of backpack {}: {}", record.uuid(), error))
                            .ifPresent(contents -> registry.shadow.put(record.uuid(), contents));
                }
            } catch (RuntimeException e) {
                skipped++;
                MCFloatingBackpack.LOGGER.warn("Skipping unreadable backpack record #{}: {}", i, e.toString());
            }
        }
        if (skipped > 0) {
            MCFloatingBackpack.LOGGER.error("The backpack registry had {} unreadable record(s) out of {}", skipped, records.size());
        }
        return registry;
    }
}
