package pnnh.mcfloatingbackpack.snapshot;

/**
 * 031: what a request from a client may say. A request outside these limits changes nothing and is not answered. Pure logic.
 */
public final class SnapshotRequestRules {
    public static final int MAX_PAGE = 1000;
    public static final int MIN_PAGE_SIZE = 1;
    public static final int MAX_PAGE_SIZE = 16;
    /** The most versions of one backpack a request or a reply deals with (030 keeps five; old folders may hold more). */
    public static final int MAX_VERSIONS = 16;
    public static final int MAX_DAYS = 36_500;
    /** The longest player name. */
    public static final int MAX_NAME = 16;
    /** A backpack's main slots: the most a preview holds. */
    public static final int MAX_PREVIEW_SLOTS = 108;
    /** A preview whose encoded items are bigger than this is not sent (the game's limit for a packet to a client is about 1 MiB). */
    public static final int MAX_PREVIEW_BYTES = 900_000;

    private SnapshotRequestRules() {
    }

    public static boolean validPage(int page) {
        return page >= 1 && page <= MAX_PAGE;
    }

    public static boolean validPageSize(int size) {
        return size >= MIN_PAGE_SIZE && size <= MAX_PAGE_SIZE;
    }

    /** A version's number (from 1, newest first). */
    public static boolean validVersion(int version) {
        return version >= 1 && version <= MAX_VERSIONS;
    }

    /** The days of a trim: 0 (everything) up to a hundred years. */
    public static boolean validTrimDays(int days) {
        return days >= 0 && days <= MAX_DAYS;
    }

    /** The days of a list's inactive filter: -1 (no filter) or 1 and up. */
    public static boolean validFilterDays(int days) {
        return days == -1 || (days >= 1 && days <= MAX_DAYS);
    }

    /** A player name, or empty: at most 16 characters of letters, digits and underscores. */
    public static boolean validName(String name) {
        if (name == null || name.length() > MAX_NAME) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
