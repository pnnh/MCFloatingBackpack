package pnnh.mcfloatingbackpack.compat;

import java.util.Arrays;
import java.util.Optional;

/**
 * 058: a third-party mod's version as the numbers at the front of its version string, for checking it against the ranges this mod was
 * verified with ({@link ThirdPartyTable}). Lenient on purpose: mods write {@code 1.11.2}, {@code 9.5.1+1.21.1}, {@code 1.1.0-beta.54+1.21.1}
 * or {@code 21.1.22}. The first digit starts it, digits joined by single dots go on, and everything from the first other character on
 * (a pre-release tag, build metadata, letters, {@code ..}) is ignored. A version with no digit, or with a number that does not fit a
 * {@code long}, cannot be judged ({@link #parse} is empty). Missing trailing parts count as 0 when comparing. Pure logic.
 *
 * <p>Not {@code snapshot.VersionOrder}: that one reads every number in the text, which would turn {@code 1.1.0-beta.54+1.21.1} into seven
 * parts and misplace it against a range.
 */
public final class ThirdPartyVersion implements Comparable<ThirdPartyVersion> {
    private final long[] parts;

    private ThirdPartyVersion(long[] parts) {
        this.parts = parts;
    }

    /** A version made of these numbers (at least one, none negative). */
    public static ThirdPartyVersion of(long... parts) {
        if (parts.length == 0) {
            throw new IllegalArgumentException("a version needs at least one number");
        }
        for (long part : parts) {
            if (part < 0) {
                throw new IllegalArgumentException("a version number cannot be negative");
            }
        }
        return new ThirdPartyVersion(parts.clone());
    }

    /** @return the version, or empty when the text has no usable number in it */
    public static Optional<ThirdPartyVersion> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        int length = text.length();
        int i = 0;
        while (i < length && !isDigit(text.charAt(i))) {
            i++;
        }
        if (i == length) {
            return Optional.empty();
        }
        long[] parts = new long[4];
        int count = 0;
        while (true) {
            int start = i;
            while (i < length && isDigit(text.charAt(i))) {
                i++;
            }
            long value;
            try {
                value = Long.parseLong(text.substring(start, i));
            } catch (NumberFormatException tooBig) {
                return Optional.empty();
            }
            if (count == parts.length) {
                parts = Arrays.copyOf(parts, count * 2);
            }
            parts[count++] = value;
            if (i + 1 < length && text.charAt(i) == '.' && isDigit(text.charAt(i + 1))) {
                i++;
            } else {
                break;
            }
        }
        return Optional.of(new ThirdPartyVersion(Arrays.copyOf(parts, count)));
    }

    private static boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public int size() {
        return parts.length;
    }

    @Override
    public int compareTo(ThirdPartyVersion other) {
        int length = Math.max(parts.length, other.parts.length);
        for (int i = 0; i < length; i++) {
            long a = i < parts.length ? parts[i] : 0L;
            long b = i < other.parts.length ? other.parts[i] : 0L;
            if (a != b) {
                return a < b ? -1 : 1;
            }
        }
        return 0;
    }

    /** Equal when they compare equal ({@code 1.11} and {@code 1.11.0} are the same version). */
    @Override
    public boolean equals(Object other) {
        return other instanceof ThirdPartyVersion version && compareTo(version) == 0;
    }

    @Override
    public int hashCode() {
        int end = parts.length;
        while (end > 1 && parts[end - 1] == 0L) {
            end--;
        }
        return Arrays.hashCode(Arrays.copyOf(parts, end));
    }

    /** The numbers joined by dots, padded with zeros to at least three ({@code 1.11} shows as {@code 1.11.0}). */
    @Override
    public String toString() {
        StringBuilder out = new StringBuilder();
        int shown = Math.max(3, parts.length);
        for (int i = 0; i < shown; i++) {
            if (i > 0) {
                out.append('.');
            }
            out.append(i < parts.length ? parts[i] : 0L);
        }
        return out.toString();
    }
}
