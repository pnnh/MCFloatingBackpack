package pnnh.mcfloatingbackpack.inventory.tags;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * 025: what a tag text box's drop-down starts from before the typed text narrows it: the preset tags first, then the tags
 * this backpack has put on items, each once, and nothing in {@code exclude} (the filter offers everything; the detail page
 * leaves out the tags the item already has). Pure logic.
 */
public final class TagCandidates {
    private TagCandidates() {
    }

    public static List<String> of(Collection<String> presetIds, Collection<String> usedTags, Collection<String> exclude) {
        List<String> out = new ArrayList<>();
        for (String id : presetIds) {
            if (!exclude.contains(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        for (String tag : usedTags) {
            if (!exclude.contains(tag) && !out.contains(tag)) {
                out.add(tag);
            }
        }
        return out;
    }
}
