package pnnh.mcfloatingbackpack.client.panel.pickup;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklist;

/**
 * 024: what the pickup blacklist page shows: the tags, whether the auto pickup is there to edit them, and what the text
 * box's drop-down may offer. Pure logic with no Minecraft types, so it can be tested.
 */
public final class PickupPanelModel {
    private final List<String> tags;
    private final boolean hasTool;

    private PickupPanelModel(List<String> tags, boolean hasTool) {
        this.tags = tags;
        this.hasTool = hasTool;
    }

    /** @param hasTool whether an auto pickup is in the left area (adding needs it) */
    public static PickupPanelModel of(List<String> tags, boolean hasTool) {
        return new PickupPanelModel(List.copyOf(tags), hasTool);
    }

    public List<String> tags() {
        return tags;
    }

    public int count() {
        return tags.size();
    }

    public int max() {
        return PickupBlacklist.MAX_TAGS;
    }

    public boolean isFull() {
        return tags.size() >= PickupBlacklist.MAX_TAGS;
    }

    public boolean canAdd() {
        return hasTool && !isFull();
    }

    /**
     * What the text box's drop-down starts from: the preset tags first, then the tags this backpack has put on items,
     * each once, and nothing that is already on the blacklist.
     */
    public static List<String> candidates(
            Collection<String> presetIds, Collection<String> usedTags, Collection<String> blacklist) {
        List<String> out = new ArrayList<>();
        for (String id : presetIds) {
            if (!blacklist.contains(id) && !out.contains(id)) {
                out.add(id);
            }
        }
        for (String tag : usedTags) {
            if (!blacklist.contains(tag) && !out.contains(tag)) {
                out.add(tag);
            }
        }
        return out;
    }
}
