package pnnh.mcfloatingbackpack.inventory.identity;

import java.util.UUID;

/**
 * 026: the backpack ids players see (such as {@code K7M2}): four characters, the first a capital letter, the other three
 * capital letters or digits, never starting with a digit, with the characters that are easy to mix up left out (I, O, 0, 1).
 * That makes 24 x 32 x 32 x 32 = {@value #TOTAL} ids (030: it was three characters, 24,576 ids). An id is worked out from the
 * backpack's internal id ({@link #of}), so it needs no registry and two backpacks can by chance show the same id: it is a label
 * to tell your own backpacks apart, not a key.
 * Pure logic with no Minecraft types.
 */
public final class BackpackIds {
    /** The letters (A-Z without I and O) that may start an id. */
    public static final String FIRST = "ABCDEFGHJKLMNPQRSTUVWXYZ";

    /** What may follow: those letters and the digits 2 to 9. */
    public static final String REST = FIRST + "23456789";

    /** How many characters an id has. */
    public static final int LENGTH = 4;

    public static final int TOTAL = FIRST.length() * REST.length() * REST.length() * REST.length();

    private BackpackIds() {
    }

    public static boolean isValid(String id) {
        return index(id) >= 0;
    }

    /** The id's number from 0 to {@link #TOTAL} - 1, or -1 if it is not a valid id. */
    public static int index(String id) {
        if (id == null || id.length() != LENGTH) {
            return -1;
        }
        int a = FIRST.indexOf(id.charAt(0));
        int b = REST.indexOf(id.charAt(1));
        int c = REST.indexOf(id.charAt(2));
        int d = REST.indexOf(id.charAt(3));
        if (a < 0 || b < 0 || c < 0 || d < 0) {
            return -1;
        }
        return ((a * REST.length() + b) * REST.length() + c) * REST.length() + d;
    }

    public static String fromIndex(int index) {
        if (index < 0 || index >= TOTAL) {
            throw new IllegalArgumentException("id index out of range: " + index);
        }
        int n = REST.length();
        int d = index % n;
        int c = (index / n) % n;
        int b = (index / (n * n)) % n;
        int a = index / (n * n * n);
        return "" + FIRST.charAt(a) + REST.charAt(b) + REST.charAt(c) + REST.charAt(d);
    }

    /** The id of the backpack with this internal id: always the same for the same internal id, spread evenly over all ids. */
    public static String of(UUID uuid) {
        long x = mix(mix(uuid.getLeastSignificantBits()) ^ uuid.getMostSignificantBits());
        return fromIndex((int) Math.floorMod(x, (long) TOTAL));
    }

    /** A 64-bit mixer (the finalizer of MurmurHash3), so ids follow no pattern in the internal ids. */
    private static long mix(long x) {
        x ^= x >>> 33;
        x *= 0xff51afd7ed558ccdL;
        x ^= x >>> 33;
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= x >>> 33;
        return x;
    }
}
