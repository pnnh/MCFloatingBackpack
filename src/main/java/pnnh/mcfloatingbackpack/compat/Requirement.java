package pnnh.mcfloatingbackpack.compat;

import java.util.List;

/** 058: another mod that must also be in range for a {@link ThirdPartyCompat} to be on (the mods its classes belong to). Pure logic. */
public record Requirement(String modId, String displayName, List<VersionRange> supported) {
    public Requirement {
        supported = List.copyOf(supported);
    }
}
