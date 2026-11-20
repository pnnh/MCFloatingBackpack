package pnnh.mcfloatingbackpack.compat;

import java.util.List;

/**
 * 058: one row of {@link ThirdPartyTable}: a third-party mod this mod has active compatibility code for, and the versions that code was
 * verified with (several stretches, with gaps allowed: only versions that were really tried are listed). {@code also} are other mods that
 * have to be in their own ranges too. {@code variant} is reserved for a later time when different stretches need different code; it is
 * {@code null} now and nothing reads it. Pure logic.
 */
public record ThirdPartyCompat(String modId, String displayName, List<VersionRange> supported, List<Requirement> also, String variant) {
    public ThirdPartyCompat {
        supported = List.copyOf(supported);
        also = List.copyOf(also);
    }
}
