package pnnh.mcfloatingbackpack.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.inventory.tags.TagBook;
import pnnh.mcfloatingbackpack.inventory.tags.TagOverviewRequest;
import pnnh.mcfloatingbackpack.inventory.tags.TagOverviewRequest.Op;
import pnnh.mcfloatingbackpack.inventory.tags.TagOverviewRules;
import pnnh.mcfloatingbackpack.inventory.tags.TagRules;
import pnnh.mcfloatingbackpack.item.ModTags;

/**
 * 025: client asks to add a tag to, remove a tag from, or clear all tags of one item category of the player's active
 * backpack, from the tag overview. Unlike 018's {@link BackpackTagPayload} it names the category by its registry id, since
 * the item may not be in the backpack at all. The server decides everything: the function area must hold a tag manager,
 * the id must be a well-formed registry id, whether the item is still in the game and whether it is protected (022) are
 * worked out here, and {@link TagOverviewRules} applies the rest. Any check that fails drops the whole request and changes
 * nothing; either way the client is sent what the tags really are.
 */
public record BackpackTagOverviewPayload(byte op, String itemId, String tag) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackTagOverviewPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "tag_overview"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackTagOverviewPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.op());
                buf.writeUtf(payload.itemId(), TagOverviewRequest.MAX_ITEM_ID_LENGTH);
                buf.writeUtf(payload.tag(), TagRules.MAX_LENGTH);
            },
            buf -> new BackpackTagOverviewPayload(
                    buf.readByte(), buf.readUtf(TagOverviewRequest.MAX_ITEM_ID_LENGTH), buf.readUtf(TagRules.MAX_LENGTH)));

    public static BackpackTagOverviewPayload of(Op op, ResourceLocation item, String tag) {
        return new BackpackTagOverviewPayload(op.toByte(), item.toString(), tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackTagOverviewPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            BackpackRef ref = BackpackRef.resolve(player);
            if (ref == null || !ref.stillValid(player)) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            // 026: every request into a backpack goes through the registry first.
            BackpackInventory backpack = BackpackGate.acquire(player, ref).orElse(null);
            if (backpack == null) {
                BackpackNetwork.sendSession(player, false, null);
                return;
            }
            Op op = Op.fromByte(payload.op());
            ResourceLocation key = TagOverviewRequest.validate(op, payload.itemId(), payload.tag())
                    ? ResourceLocation.tryParse(payload.itemId())
                    : null;
            // The overview only works with a tag manager in the function area: no tool there, no request.
            if (key != null && backpack.hasTagManager()) {
                boolean registered = BuiltInRegistries.ITEM.containsKey(key);
                boolean isProtected = registered && BuiltInRegistries.ITEM.get(key).getDefaultInstance().is(ModTags.NEVER_TRASH);
                TagBook<ResourceLocation> book = backpack.tags().toBook();
                TagBook<ResourceLocation> next =
                        TagOverviewRules.apply(book, op, key, payload.tag(), registered, isProtected);
                if (next != null && !next.asMap().equals(book.asMap()) && next.asMap().size() <= ItemTags.MAX_CATEGORIES) {
                    backpack.setTags(ItemTags.of(next));
                    ref.saveInventory(player, backpack);
                }
            }
            // Success and refusal alike: the client shows what the server has.
            PacketDistributor.sendToPlayer(player, new BackpackTagsPayload(backpack.tags()));
        });
    }
}
