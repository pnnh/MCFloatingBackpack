package pnnh.mcfloatingbackpack.inventory.identity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.StreamCodec;

/**
 * 026: how a {@link BackpackIdentity} is stored on a stack and sent to the client. Kept apart from the record so the record
 * loads without Minecraft on the class path (the tests). Fields other than {@code uuid} (an earlier draft also stored an id and a
 * revision) are ignored when reading.
 */
public final class BackpackIdentityCodecs {
    public static final Codec<BackpackIdentity> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(UUIDUtil.CODEC.fieldOf("uuid").forGetter(BackpackIdentity::uuid))
            .apply(instance, BackpackIdentity::new));

    public static final StreamCodec<ByteBuf, BackpackIdentity> STREAM_CODEC =
            UUIDUtil.STREAM_CODEC.map(BackpackIdentity::new, BackpackIdentity::uuid);

    private BackpackIdentityCodecs() {
    }
}
