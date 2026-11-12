package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.List;

/**
 * 042: whether one cell's searchable text contains a (lower-cased) piece of text. The query and the results only ever ask this
 * question, so a later way of matching (043: pinyin) only has to supply another implementation. No Minecraft types.
 */
@FunctionalInterface
public interface TextMatcher {
    boolean contains(String needleLowerCase);

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
