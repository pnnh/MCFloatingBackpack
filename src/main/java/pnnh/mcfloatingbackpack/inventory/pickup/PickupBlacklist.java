package pnnh.mcfloatingbackpack.inventory.pickup;

import java.util.ArrayList;
import java.util.List;

import pnnh.mcfloatingbackpack.inventory.tags.TagRules;

/**
 * 024: the pickup blacklist of one backpack: tag names (018 rules: 1 to 16 visible ASCII characters, no space, no capital
 * letter) of which an item category carrying any is left on the ground by the auto pickup. At most {@link #MAX_TAGS},
 * no duplicates, in the order they were added. Immutable; every change returns a new value, and a refused change returns
 * {@code null} (the caller treats it as "nothing changed"). Pure Java so it can be unit-tested; the codecs that store and
 * send it are in {@link PickupBlacklistCodecs}. Belongs to the backpack, not to the auto pickup item.
 */
public record PickupBlacklist(List<String> tags) {
    /** Most tags a blacklist may hold. */
    public static final int MAX_TAGS = 64;

    public static final PickupBlacklist EMPTY = new PickupBlacklist(List.of());

    /** Foreign data made safe: invalid and duplicate tags dropped, at most {@link #MAX_TAGS} kept, order kept. */
    public PickupBlacklist {
        tags = List.copyOf(sanitize(tags));
    }

    public static List<String> sanitize(List<String> raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) {
            return out;
        }
        for (String tag : raw) {
            if (TagRules.isValid(tag) && !out.contains(tag) && out.size() < MAX_TAGS) {
                out.add(tag);
            }
        }
        return out;
    }

    public int size() {
        return tags.size();
    }

    public boolean isFull() {
        return tags.size() >= MAX_TAGS;
    }

    public boolean contains(String tag) {
        return tags.contains(tag);
    }

    /**
     * @return this if the tag is already listed (adding twice is not an error); {@code null} if the tag is not valid or
     *     the list is full; otherwise the list with the tag appended
     */
    public PickupBlacklist add(String tag) {
        if (!TagRules.isValid(tag)) {
            return null;
        }
        if (tags.contains(tag)) {
            return this;
        }
        if (isFull()) {
            return null;
        }
        List<String> next = new ArrayList<>(tags);
        next.add(tag);
        return new PickupBlacklist(next);
    }

    /** @return this if the tag is not listed, otherwise the list without it */
    public PickupBlacklist remove(String tag) {
        if (!tags.contains(tag)) {
            return this;
        }
        List<String> next = new ArrayList<>(tags);
        next.remove(tag);
        return new PickupBlacklist(next);
    }

    /** Whether any of an item category's tags (from the backpack's tag book) is on the blacklist. */
    public boolean matches(List<String> tagsOfItem) {
        if (tagsOfItem == null || tags.isEmpty()) {
            return false;
        }
        for (String tag : tagsOfItem) {
            if (tags.contains(tag)) {
                return true;
            }
        }
        return false;
    }
}
