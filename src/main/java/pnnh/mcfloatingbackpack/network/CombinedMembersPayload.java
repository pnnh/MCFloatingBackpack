package pnnh.mcfloatingbackpack.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.panel.combined.ClientCombinedState;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedCandidates;
import pnnh.mcfloatingbackpack.inventory.combined.CombinedMembers;

/**
 * 065: the table of backpacks a combined backpack connects, as the server worked it out: one entry for each tab, which one is selected, and
 * how many were left out and why. Sent when the panel is opened or a tab is chosen, and afterwards only when the table changes (checked once
 * a second); {@code allEpoch} (066) is 0 on a single backpack's tab, and on the "all" tab the epoch the member syncs that follow are made with ({@code selected} then means nothing); {@code active = false} says the combined panel is over (the panel goes on as the ordinary one, or closes).
 */
public record CombinedMembersPayload(
        boolean active, int allEpoch, int combinedTier, int selected, int skippedTier, int skippedNewer, int skippedOverCap, List<Entry> members)
        implements CustomPacketPayload {
    /** One tab: where the backpack is (a {@link BackpackRef}), the identity it had (if any), its tier and the name given in an anvil. */
    public record Entry(int sourceOrdinal, int invIndex, Optional<UUID> identity, int tier, String name) {
        public BackpackRef ref() {
            BackpackRef.Source[] sources = BackpackRef.Source.values();
            BackpackRef.Source source = sourceOrdinal >= 0 && sourceOrdinal < sources.length ? sources[sourceOrdinal] : BackpackRef.Source.INVENTORY;
            return new BackpackRef(source, invIndex);
        }
    }

    public static final CustomPacketPayload.Type<CombinedMembersPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "combined_members"));

    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<UUID>> IDENTITY = ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, CombinedMembersPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.active());
                buf.writeVarInt(payload.allEpoch());
                buf.writeByte(payload.combinedTier());
                buf.writeVarInt(payload.selected() + 1);
                buf.writeVarInt(payload.skippedTier());
                buf.writeVarInt(payload.skippedNewer());
                buf.writeVarInt(payload.skippedOverCap());
                buf.writeVarInt(payload.members().size());
                for (Entry entry : payload.members()) {
                    buf.writeVarInt(entry.sourceOrdinal());
                    buf.writeVarInt(entry.invIndex() + 1);
                    IDENTITY.encode(buf, entry.identity());
                    buf.writeByte(entry.tier());
                    buf.writeUtf(entry.name(), CombinedCandidates.MAX_NAME);
                }
            },
            buf -> {
                boolean active = buf.readBoolean();
                int allEpoch = buf.readVarInt();
                int tier = buf.readByte();
                int selected = buf.readVarInt() - 1;
                int skippedTier = buf.readVarInt();
                int skippedNewer = buf.readVarInt();
                int skippedOverCap = buf.readVarInt();
                int count = buf.readVarInt();
                if (count < 0 || count > CombinedMembers.MAX_MEMBERS) {
                    throw new DecoderException("combined members: too many entries: " + count);
                }
                List<Entry> members = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    int source = buf.readVarInt();
                    int slot = buf.readVarInt() - 1;
                    Optional<UUID> identity = IDENTITY.decode(buf);
                    int entryTier = buf.readByte();
                    String name = buf.readUtf(CombinedCandidates.MAX_NAME);
                    members.add(new Entry(source, slot, identity, entryTier, name));
                }
                return new CombinedMembersPayload(active, allEpoch, tier, selected, skippedTier, skippedNewer, skippedOverCap, members);
            });

    /** The combined panel is over. */
    public static CombinedMembersPayload inactive() {
        return new CombinedMembersPayload(false, 0, 0, -1, 0, 0, 0, List.of());
    }

    public boolean all() {
        return allEpoch > 0;
    }

    public static CombinedMembersPayload of(int combinedTier, int selected, CombinedMembers.Result<BackpackRef> result) {
        return of(combinedTier, selected, 0, result);
    }

    /** {@code allEpoch} 0 for a single tab ({@code selected} its place), else the "all" tab's epoch. */
    public static CombinedMembersPayload of(int combinedTier, int selected, int allEpoch, CombinedMembers.Result<BackpackRef> result) {
        List<Entry> entries = new ArrayList<>(result.members().size());
        for (CombinedMembers.Member<BackpackRef> member : result.members()) {
            entries.add(new Entry(
                    member.ref().source().ordinal(), member.ref().invIndex(), Optional.ofNullable(member.identity()), member.tier(), member.customName()));
        }
        return new CombinedMembersPayload(true, allEpoch, combinedTier, allEpoch > 0 ? -1 : selected, result.skippedTier(), result.skippedNewer(), result.skippedOverCap(), entries);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleClient(CombinedMembersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCombinedState.get().apply(payload));
    }
}
