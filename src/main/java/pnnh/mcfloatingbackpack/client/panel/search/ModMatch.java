package pnnh.mcfloatingbackpack.client.panel.search;

/**
 * 044: {@code @mod}: whether an item belongs to the mod a query names. The namespace of the item's registry name ({@code minecraft},
 * {@code sophisticatedbackpacks}) or the mod's display name ("Sophisticated Backpacks") has to contain the needle. Everything is
 * lower case. No Minecraft types.
 */
public final class ModMatch {
    private ModMatch() {
    }

    public static boolean matches(String needleLowerCase, String namespaceLowerCase, String modNameLowerCase) {
        return namespaceLowerCase.contains(needleLowerCase) || modNameLowerCase.contains(needleLowerCase);
    }
}
