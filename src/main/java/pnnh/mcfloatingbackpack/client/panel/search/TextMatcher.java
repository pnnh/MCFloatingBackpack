package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.List;

/**
 * 042: whether one cell's searchable text contains a (lower-cased) piece of text. The query and the results only ever ask this
 * question, so a later way of matching (043: pinyin) only has to supply another implementation. No Minecraft types.
 */
@FunctionalInterface
public interface TextMatcher {
    boolean contains(String needleLowerCase);

    /** 044: whether the item belongs to a mod the needle names ({@code @mod}). Nothing knows about mods unless it says so. */
    default boolean inMod(String needleLowerCase) {
        return false;
    }

    /** 044: whether the item has a tag the needle names ({@code #tag}). Nothing knows about tags unless it says so. */
    default boolean hasTag(String needleLowerCase) {
        return false;
    }

    /** Plain substring matching against already lower-cased texts: true when any of them contains the needle. */
    static TextMatcher ofTexts(List<String> lowerCaseTexts) {
        return needle -> {
            for (String text : lowerCaseTexts) {
                if (text.contains(needle)) {
                    return true;
                }
            }
            return false;
        };
    }
}
