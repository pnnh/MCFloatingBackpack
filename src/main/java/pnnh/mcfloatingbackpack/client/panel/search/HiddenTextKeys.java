package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.List;

/**
 * 048: which language entries the words about hidden content use, depending on what is hidden: only items in hidden rows (037: the original
 * words), only liquids with no tank, or both. Shared by the yellow dot's hint and the summary of {@code /hidden}. The keys are given without
 * the {@code panel.<modid>} prefix. No Minecraft types.
 */
public final class HiddenTextKeys {
    private HiddenTextKeys() {
    }

    /** The heading: the original one for items only, its own for liquids only, a joint one for both. */
    public static String title(boolean items, boolean liquids) {
        if (items && liquids) {
            return ".hidden.mixed.title";
        }
        return liquids ? ".hidden.liquid.title" : ".hidden.title";
    }

    /** The closing hints, in the order they are written: where to get the items back, then where to get the liquids back. */
    public static List<String> hints(boolean items, boolean liquids) {
        if (items && liquids) {
            return List.of(".hidden.hint", ".hidden.liquid.hint");
        }
        return List.of(liquids ? ".hidden.liquid.hint" : ".hidden.hint");
    }
}
