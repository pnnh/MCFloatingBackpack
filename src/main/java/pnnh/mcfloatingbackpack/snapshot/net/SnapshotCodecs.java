package pnnh.mcfloatingbackpack.snapshot.net;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;

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

    static void writeVersions(FriendlyByteBuf buf, List<VersionRow> versions) {
        buf.writeVarInt(versions.size());
        for (VersionRow version : versions) {
            buf.writeVarLong(version.time());
            buf.writeVarInt(version.slots());
            buf.writeUtf(version.ownerName(), SnapshotRequestRules.MAX_NAME);
            buf.writeVarInt(version.capacity());
        }
    }

    static List<VersionRow> readVersions(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > SnapshotRequestRules.MAX_VERSIONS) {
            throw new DecoderException("snapshot version count out of range: " + count);
        }
        List<VersionRow> versions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            versions.add(new VersionRow(
                    buf.readVarLong(), buf.readVarInt(), buf.readUtf(SnapshotRequestRules.MAX_NAME), buf.readVarInt()));
        }
        return versions;
    }
}
