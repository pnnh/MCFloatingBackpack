package pnnh.mcfloatingbackpack.compat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 020: what is wrong with the player's config, queued here until the client can show it in the player's own chat (the
 * log is not where a player looks). Entries are added from wherever the config is read (possibly the file-watcher
 * thread) and drained on the client thread once the player is in a world. No Minecraft types, so it can be tested.
 */
public final class ConfigNotices {
    /** A chat line: a language key and its one argument (an entry, a mod id or a file name; may be empty). */
    public record Notice(String key, String arg) {
    }

    /** Most notices shown at once; the rest are summed up in one "and N more" line. */
    public static final int MAX_SHOWN = 5;

    static final String PREFIX = "message.mcfloatingbackpack.config.";

    /** A file name pattern of the backups NeoForge makes of a config it could not read or had to correct. */
    private static final String BACKUP_START = "mcfloatingbackpack-client";
    private static final String BACKUP_END = ".toml.bak";

    private static final ConcurrentLinkedQueue<Notice> QUEUE = new ConcurrentLinkedQueue<>();

    private ConfigNotices() {
    }

    /** One entry of {@code modLevels} was ignored or corrected. */
    public static void warning(CompatPolicy.Warning warning) {
        QUEUE.add(new Notice(PREFIX + warning.problem().name().toLowerCase(java.util.Locale.ROOT), warning.entry()));
    }

    /** NeoForge could not read (or had to correct) the config file and put the player's version aside as {@code backupName}. */
    public static void fileProblem(String backupName) {
        QUEUE.add(new Notice(PREFIX + "file", backupName));
    }

    public static boolean hasPending() {
        return !QUEUE.isEmpty();
    }

    /** Takes what is queued: identical notices once, at most {@link #MAX_SHOWN} lines, then "and N more". */
    public static List<Notice> drain() {
        Set<Notice> unique = new LinkedHashSet<>();
        for (Notice notice = QUEUE.poll(); notice != null; notice = QUEUE.poll()) {
            unique.add(notice);
        }
        List<Notice> all = new ArrayList<>(unique);
        if (all.size() <= MAX_SHOWN) {
            return all;
        }
        List<Notice> shown = new ArrayList<>(all.subList(0, MAX_SHOWN));
        shown.add(new Notice(PREFIX + "more", String.valueOf(all.size() - MAX_SHOWN)));
        return shown;
    }

    /** Forgets everything queued (tests). */
    static void clear() {
        QUEUE.clear();
    }

    /**
     * The backups of this mod's client config that were made within the last {@code windowMillis}: a backup that fresh means
     * the file the player just saved was broken (a syntax error, or values of the wrong kind) and was set aside.
     *
     * @param modifiedByName file name to last-modified time (millis) of the files in the config folder
     */
    public static List<String> freshBackups(Map<String, Long> modifiedByName, long nowMillis, long windowMillis) {
        List<String> fresh = new ArrayList<>();
        for (Map.Entry<String, Long> file : modifiedByName.entrySet()) {
            String name = file.getKey();
            if (name.startsWith(BACKUP_START)
                    && name.endsWith(BACKUP_END)
                    && nowMillis - file.getValue() <= windowMillis
                    && nowMillis - file.getValue() >= -windowMillis) {
                fresh.add(name);
            }
        }
        java.util.Collections.sort(fresh);
        return fresh;
    }
}
