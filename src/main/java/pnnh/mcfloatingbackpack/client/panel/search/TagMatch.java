package pnnh.mcfloatingbackpack.client.panel.search;

import java.util.List;

/**
 * 044: {@code #tag}: whether an item has a tag a query names. Two kinds of tags, the answer is "either":
 * <ul>
 * <li><b>item tags</b> ({@code minecraft:logs}, {@code c:ingots/iron}, other mods' tags), by their id: a needle with a colon has to be
 * the <em>start</em> of the whole id ({@code c:ingots} finds {@code c:ingots/iron}); a needle without one only has to be <em>in the
 * path</em>, the part after the colon ({@code logs} finds {@code minecraft:oak_logs});</li>
 * <li><b>the backpack's own tags</b> (018), which have no namespace: the needle has to be in the tag's id or in its name in the current
 * language ({@code trash} and the Chinese name of that preset find the same items). A needle with a colon names a namespace, which
 * these tags do not have, so it never finds them.</li>
 * </ul>
 * Everything is lower case. No Minecraft types.
 */
public final class TagMatch {
    /** One of the backpack's tags on an item: its stable id ("trash", or a custom tag as typed) and its name in the current language. */
    public record Entry(String idLowerCase, String labelLowerCase) {
    }

    private TagMatch() {
    }

    public static boolean itemTag(String needleLowerCase, List<String> tagIdsLowerCase) {
        boolean withNamespace = needleLowerCase.indexOf(':') >= 0;
        for (String id : tagIdsLowerCase) {
            if (withNamespace) {
                if (id.startsWith(needleLowerCase)) {
                    return true;
                }
            } else if (id.substring(id.indexOf(':') + 1).contains(needleLowerCase)) {
                return true;
            }
        }
        return false;
    }

    public static boolean backpackTag(String needleLowerCase, List<Entry> entries) {
        if (needleLowerCase.indexOf(':') >= 0) {
            return false;
        }
        for (Entry entry : entries) {
            if (entry.idLowerCase().contains(needleLowerCase) || entry.labelLowerCase().contains(needleLowerCase)) {
                return true;
            }
        }
        return false;
    }
}
