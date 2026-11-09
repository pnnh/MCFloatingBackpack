package pnnh.mcfloatingbackpack.network;

import java.util.ArrayList;
import java.util.List;

import io.netty.handler.codec.DecoderException;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.tags.ItemTags;
import pnnh.mcfloatingbackpack.inventory.tags.TagBook;
import pnnh.mcfloatingbackpack.inventory.tags.TagRequest;
import pnnh.mcfloatingbackpack.inventory.tags.TagRequest.Op;
import pnnh.mcfloatingbackpack.inventory.trash.TagProtection;
import pnnh.mcfloatingbackpack.item.ModTags;
import pnnh.mcfloatingbackpack.inventory.tags.TagRules;
import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * 018: client asks to add a tag to, or remove it from, the item categories in the selected main-area slots. The
 * request carries slot indices and the tag, never item ids: the server reads the items from the backpack's real contents.
 * Any check that fails drops the whole request and changes nothing.
 */
public record BackpackTagPayload(byte op, int[] slots, String tag) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<BackpackTagPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "tag"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BackpackTagPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeByte(payload.op());
                buf.writeVarInt(payload.slots().length);
                for (int slot : payload.slots()) {
                    buf.writeVarInt(slot);
                }
                buf.writeUtf(payload.tag(), TagRules.MAX_LENGTH);
            },
            buf -> {
                byte op = buf.readByte();
                int count = buf.readVarInt();
                if (count < 0 || count > TagRequest.MAX_SLOTS) {
                    throw new DecoderException("tag request slot count out of range: " + count);
                }
                int[] slots = new int[count];
                for (int i = 0; i < count; i++) {
                    slots[i] = buf.readVarInt();
                }
                return new BackpackTagPayload(op, slots, buf.readUtf(TagRules.MAX_LENGTH));
            });

    public static BackpackTagPayload of(Op op, int[] slots, String tag) {
        return new BackpackTagPayload(op.toByte(), slots, tag);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(BackpackTagPayload payload, IPayloadContext context) {
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
            // The tag manager only works in the function area: no tool there, no request.
            if (!hasTagManager(backpack)) {
                return;
            }
            Op op = Op.fromByte(payload.op());
            if (!TagRequest.validate(op, payload.slots(), payload.tag(), backpack.size())) {
                return;
            }
            List<ResourceLocation> categories = categoriesOf(backpack, payload.slots());
            if (categories.isEmpty()) {
                return;
            }
            // 022: the trash tag may not be put on a protected item; the whole request is refused and the client is
            // told what the tags really are.
            if (op == Op.ADD && TagProtection.blocks(payload.tag(), protectedCategories(backpack, payload.slots()))) {
                PacketDistributor.sendToPlayer(player, new BackpackTagsPayload(backpack.tags()));
                return;
            }
            TagBook<ResourceLocation> book = backpack.tags().toBook();
            TagBook<ResourceLocation> next =
                    op == Op.ADD ? book.addOrNull(categories, payload.tag()) : book.remove(categories, payload.tag());
            if (next == null || next.asMap().size() > ItemTags.MAX_CATEGORIES) {
                return;
            }
            backpack.setTags(ItemTags.of(next));
            ref.saveInventory(player, backpack);
            PacketDistributor.sendToPlayer(player, new BackpackTagsPayload(backpack.tags()));
        });
    }

    private static boolean hasTagManager(BackpackInventory backpack) {
        for (int i = 0; i < backpack.size(PanelArea.BOTTOM); i++) {
            if (backpack.getStackInSlot(PanelArea.BOTTOM, i).is(ModItems.TAG_MANAGER.get())) {
                return true;
            }
        }
        return false;
    }

    /** How many distinct item categories in the named main slots are on the protection list. */
    private static int protectedCategories(BackpackInventory backpack, int[] slots) {
        List<ResourceLocation> seen = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (stack.isEmpty() || !stack.is(ModTags.NEVER_TRASH)) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!seen.contains(id)) {
                seen.add(id);
            }
        }
        return seen.size();
    }

    /** The distinct item categories in the named main slots; empty slots are skipped. */
    private static List<ResourceLocation> categoriesOf(BackpackInventory backpack, int[] slots) {
        List<ResourceLocation> out = new ArrayList<>();
        for (int slot : slots) {
            ItemStack stack = backpack.getStackInSlot(slot);
            if (stack.isEmpty()) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
