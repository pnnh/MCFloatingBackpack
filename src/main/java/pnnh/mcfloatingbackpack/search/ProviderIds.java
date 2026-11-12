package pnnh.mcfloatingbackpack.search;

import java.util.regex.Pattern;

/** 043: what counts as a mod id for registering a provider: the same shape {@code CompatPolicy} accepts, so {@code modLevels} can name it. */
public final class ProviderIds {
    private static final Pattern MOD_ID = Pattern.compile("[a-z0-9_]+");

    private ProviderIds() {
    }

    public static boolean isValid(String modId) {
        return modId != null && MOD_ID.matcher(modId).matches();
    }
}
