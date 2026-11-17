package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * 057: whether one mod version is older than another, for choosing the words of a notice (an update, a downgrade, or just a change).
 * Versions are compared by the numbers in them, left to right ({@code 0.10.0} is after {@code 0.9.5}); a version with no number in it
 * cannot be compared. Pure logic.
 */
public final class VersionOrder {
    private VersionOrder() {
    }

    /** @return negative if {@code a} is older than {@code b}, positive if newer, 0 if the numbers are the same; null if either has none */
    public static Integer compare(String a, String b) {
        List<Long> left = numbers(a);
        List<Long> right = numbers(b);
        if (left.isEmpty() || right.isEmpty()) {
            return null;
        }
        int length = Math.max(left.size(), right.size());
        for (int i = 0; i < length; i++) {
            long x = i < left.size() ? left.get(i) : 0L;
            long y = i < right.size() ? right.get(i) : 0L;
            if (x != y) {
                return x < y ? -1 : 1;
            }
        }
        return 0;
    }

    private static List<Long> numbers(String version) {
        List<Long> out = new ArrayList<>();
        if (version == null) {
            return out;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i <= version.length(); i++) {
            char c = i < version.length() ? version.charAt(i) : ' ';
            if (c >= '0' && c <= '9') {
                digits.append(c);
            } else if (digits.length() > 0) {
                String text = digits.length() > 15 ? digits.substring(0, 15) : digits.toString();
                out.add(Long.parseLong(text));
                digits.setLength(0);
            }
        }
        return out;
    }
}
