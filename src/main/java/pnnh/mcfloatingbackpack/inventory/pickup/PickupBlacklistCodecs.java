package pnnh.mcfloatingbackpack.inventory.pickup;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import pnnh.mcfloatingbackpack.inventory.tags.TagRules;

/**
 * 024: how a {@link PickupBlacklist} is stored (data component, cleaned on read so foreign data cannot break the limit)
 * and sent (a list of at most 64 strings of at most 16 characters; a longer one fails to decode). Kept apart from the
 * record so the record loads without Minecraft on the class path.
 */
public final class PickupBlacklistCodecs {
    public static final Codec<PickupBlacklist> CODEC =
            Codec.STRING.listOf().xmap(PickupBlacklist::new, PickupBlacklist::tags);

    public static final StreamCodec<ByteBuf, PickupBlacklist> STREAM_CODEC = ByteBufCodecs.stringUtf8(TagRules.MAX_LENGTH)
            .apply(ByteBufCodecs.list(PickupBlacklist.MAX_TAGS))
            .map(PickupBlacklist::new, PickupBlacklist::tags);

    private PickupBlacklistCodecs() {
    }
}
