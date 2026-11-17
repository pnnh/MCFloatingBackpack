package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;

import pnnh.mcfloatingbackpack.snapshot.SnapshotKind;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;

/** 031: how the window's rows travel. A list longer than its limit is a bad packet. */
final class SnapshotCodecs {
    private SnapshotCodecs() {
    }

    static void writeUuid(FriendlyByteBuf buf, UUID uuid) {
        UUIDUtil.STREAM_CODEC.encode(buf, uuid);
    }

    static UUID readUuid(FriendlyByteBuf buf) {
        return UUIDUtil.STREAM_CODEC.decode(buf);
    }

    static void writeRows(FriendlyByteBuf buf, List<Row> rows) {
        buf.writeVarInt(rows.size());
        for (Row row : rows) {
            writeUuid(buf, row.uuid());
            writeUuid(buf, row.owner());
            buf.writeUtf(row.ownerName(), SnapshotRequestRules.MAX_NAME);
            buf.writeVarInt(row.capacity());
            buf.writeVarLong(row.newestTime());
            buf.writeVarInt(row.slots());
            buf.writeVarInt(row.versions());
        }
    }

    static List<Row> readRows(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > SnapshotRequestRules.MAX_PAGE_SIZE) {
            throw new DecoderException("snapshot row count out of range: " + count);
        }
        List<Row> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(new Row(
                    readUuid(buf),
                    readUuid(buf),
                    buf.readUtf(SnapshotRequestRules.MAX_NAME),
                    buf.readVarInt(),
                    buf.readVarLong(),
                    buf.readVarInt(),
                    buf.readVarInt()));
        }
        return rows;
    }

    /** The longest mod version that travels (a longer one is cut). */
    public static final int MAX_VERSION_TEXT = 32;

    static void writeVersions(FriendlyByteBuf buf, List<VersionRow> versions) {
        buf.writeVarInt(versions.size());
        for (VersionRow version : versions) {
            buf.writeVarLong(version.time());
            buf.writeVarInt(version.slots());
            buf.writeUtf(version.ownerName(), SnapshotRequestRules.MAX_NAME);
            buf.writeVarInt(version.capacity());
            buf.writeByte(version.kind());
            String to = version.toVersion();
            buf.writeUtf(to.length() > MAX_VERSION_TEXT ? to.substring(0, MAX_VERSION_TEXT) : to, MAX_VERSION_TEXT);
        }
    }

    static List<VersionRow> readVersions(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > SnapshotRequestRules.MAX_PAGE_SIZE) {
            throw new DecoderException("snapshot version count out of range: " + count);
        }
        List<VersionRow> versions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long time = buf.readVarLong();
            int slots = buf.readVarInt();
            String owner = buf.readUtf(SnapshotRequestRules.MAX_NAME);
            int capacity = buf.readVarInt();
            byte kind = buf.readByte();
            if (SnapshotKind.fromByte(kind) == null) {
                throw new DecoderException("unknown snapshot kind: " + kind);
            }
            versions.add(new VersionRow(time, slots, owner, capacity, kind, buf.readUtf(MAX_VERSION_TEXT)));
        }
        return versions;
    }
}
