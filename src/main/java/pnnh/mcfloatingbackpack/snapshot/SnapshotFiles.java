package pnnh.mcfloatingbackpack.snapshot;

import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import pnnh.mcfloatingbackpack.inventory.identity.BackpackIds;

/**
 * 030: the names of a backpack's snapshot folder (its UUID, a dash, and the id the player sees, so a player can tell which folder is
 * which backpack; only the UUID counts, whatever follows the dash is ignored, and a folder named by the UUID alone, as the first
 * version made them, is still the same backpack's) and of the snapshot files in it (the time in milliseconds, 057: the kind's suffix, and {@code .dat}).
 * Pure logic.
 */
public final class SnapshotFiles {
    public static final String EXTENSION = ".dat";

    private static final Pattern FOLDER = Pattern.compile(
            "([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12})(-.*)?");

    private SnapshotFiles() {
    }

    /** The folder a backpack's snapshots are written to: {@code <uuid>-<id>}. */
    public static String folderName(UUID uuid) {
        return uuid + "-" + BackpackIds.of(uuid);
    }

    /** @return the backpack a folder name stands for (the UUID it starts with), or null if the name is not one of ours */
    public static UUID parseFolder(String name) {
        if (name == null) {
            return null;
        }
        java.util.regex.Matcher matcher = FOLDER.matcher(name);
        if (!matcher.matches()) {
            return null;
        }
        return UUID.fromString(matcher.group(1));
    }

    /** The name of a snapshot file: the time, the kind's suffix and {@code .dat}. */
    public static String fileName(long time, SnapshotKind kind) {
        return time + kind.suffix() + EXTENSION;
    }

    /** @return the time a file name stands for, or -1 if it is not a snapshot file's name (a temporary file, a stray file, a suffix this version does not know) */
    public static long parseTime(String name) {
        String digits = digitsOf(name);
        if (digits == null) {
            return -1;
        }
        return Long.parseLong(digits);
    }

    /** @return the kind a file name stands for, or null if it is not a snapshot file's name of this version */
    public static SnapshotKind kindOf(String name) {
        if (digitsOf(name) == null) {
            return null;
        }
        String body = name.substring(0, name.length() - EXTENSION.length());
        String suffix = body.endsWith("-t") ? "-t" : body.endsWith("-u") ? "-u" : "";
        return SnapshotKind.fromSuffix(suffix);
    }

    /** The digits of the time in a name like {@code 123.dat}, {@code 123-t.dat} or {@code 123-u.dat}; null for anything else. */
    private static String digitsOf(String name) {
        if (name == null || !name.endsWith(EXTENSION)) {
            return null;
        }
        String digits = name.substring(0, name.length() - EXTENSION.length());
        if (digits.endsWith("-t") || digits.endsWith("-u")) {
            digits = digits.substring(0, digits.length() - 2);
        }
        if (digits.isEmpty() || digits.length() > 18) {
            return null;
        }
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') {
                return null;
            }
        }
        return digits;
    }

    /** The wanted time, or the next later one that no existing snapshot of the backpack uses, so a file is never overwritten. */
    public static long freeTime(long wanted, Set<Long> existing) {
        long time = wanted;
        while (existing.contains(time)) {
            time++;
        }
        return time;
    }
}
