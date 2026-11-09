package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.UUID;

/**
 * 026: the identity a backpack stack carries: an internal id. The id players see is worked out from it ({@link #id()}), so
 * the stack carries nothing else. Copies of a stack share the identity, and so the id. Pure logic: the codecs are in
 * {@link BackpackIdentityCodecs}.
 */
public record BackpackIdentity(UUID uuid) {
    public BackpackIdentity {
        if (uuid == null) {
            throw new IllegalArgumentException("uuid");
        }
    }

    /** The id players see, such as {@code K7M2}. */
    public String id() {
        return BackpackIds.of(uuid);
    }
}
