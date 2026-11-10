package pnnh.mcfloatingbackpack.version;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 039: compares the version of this mod on the client with the one on the server. Two versions are the same when their MAJOR.MINOR
 * are the same (while the major is 0 the minor is the level at which rules, data or the network protocol change, see
 * docs/plan/release-process.md) and their pre-release parts rank the same; the PATCH (a bug fix only) and the build metadata
 * ({@code +abc}) take no part. A string that is not a semantic version cannot be compared. Pure logic with no Minecraft types.
 */
public final class ModVersions {
    /** The outcome, seen from the client. */
    public enum Result {
        MATCH,
        CLIENT_OLDER,
        CLIENT_NEWER,
        UNCOMPARABLE
    }

    /** A parsed version; the build metadata is dropped. */
    public record Parsed(BigInteger major, BigInteger minor, BigInteger patch, List<String> prerelease) {
        public Parsed {
            prerelease = List.copyOf(prerelease);
        }
    }

    private ModVersions() {
    }

    /**
     * {@code MAJOR.MINOR[.PATCH][-pre.release][+build]}, with an optional leading {@code v}; the patch is 0 when it is left out.
     * Empty when the string is null, empty or not of that shape.
     */
    public static Optional<Parsed> parse(String version) {
        if (version == null) {
            return Optional.empty();
        }
        String text = version.trim();
        if (text.startsWith("v") || text.startsWith("V")) {
            text = text.substring(1);
        }
        int plus = text.indexOf('+');
        if (plus >= 0) {
            if (!validIdentifiers(text.substring(plus + 1))) {
                return Optional.empty();
            }
            text = text.substring(0, plus);
        }
        List<String> prerelease = List.of();
        int dash = text.indexOf('-');
        if (dash >= 0) {
            String pre = text.substring(dash + 1);
            if (!validIdentifiers(pre)) {
                return Optional.empty();
            }
            prerelease = List.of(pre.split("\\.", -1));
            text = text.substring(0, dash);
        }
        String[] core = text.split("\\.", -1);
        if (core.length < 2 || core.length > 3) {
            return Optional.empty();
        }
        BigInteger[] numbers = new BigInteger[3];
        numbers[2] = BigInteger.ZERO;
        for (int i = 0; i < core.length; i++) {
            if (!isNumeric(core[i])) {
                return Optional.empty();
            }
            numbers[i] = new BigInteger(core[i]);
        }
        return Optional.of(new Parsed(numbers[0], numbers[1], numbers[2], prerelease));
    }

    /** Whether the client's version is the same as the server's, older or newer; or whether they cannot be told. */
    public static Result compare(String clientVersion, String serverVersion) {
        Optional<Parsed> client = parse(clientVersion);
        Optional<Parsed> server = parse(serverVersion);
        if (client.isEmpty() || server.isEmpty()) {
            return Result.UNCOMPARABLE;
        }
        Parsed c = client.get();
        Parsed s = server.get();
        int byMajor = c.major().compareTo(s.major());
        if (byMajor != 0) {
            return byMajor < 0 ? Result.CLIENT_OLDER : Result.CLIENT_NEWER;
        }
        int byMinor = c.minor().compareTo(s.minor());
        if (byMinor != 0) {
            return byMinor < 0 ? Result.CLIENT_OLDER : Result.CLIENT_NEWER;
        }
        int byPre = comparePrerelease(c.prerelease(), s.prerelease());
        if (byPre != 0) {
            return byPre < 0 ? Result.CLIENT_OLDER : Result.CLIENT_NEWER;
        }
        return Result.MATCH;
    }

    /** Semantic versioning's precedence of pre-release parts: none ranks above any; otherwise identifier by identifier. */
    private static int comparePrerelease(List<String> a, List<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return a.isEmpty() == b.isEmpty() ? 0 : (a.isEmpty() ? 1 : -1);
        }
        int common = Math.min(a.size(), b.size());
        for (int i = 0; i < common; i++) {
            int byIdentifier = compareIdentifier(a.get(i), b.get(i));
            if (byIdentifier != 0) {
                return byIdentifier;
            }
        }
        return Integer.compare(a.size(), b.size());
    }

    private static int compareIdentifier(String a, String b) {
        boolean aNumeric = isNumeric(a);
        boolean bNumeric = isNumeric(b);
        if (aNumeric && bNumeric) {
            return new BigInteger(a).compareTo(new BigInteger(b));
        }
        if (aNumeric != bNumeric) {
            return aNumeric ? -1 : 1; // a number ranks below letters
        }
        return Integer.signum(a.compareTo(b));
    }

    private static boolean validIdentifiers(String text) {
        if (text.isEmpty()) {
            return false;
        }
        List<String> parts = new ArrayList<>(List.of(text.split("\\.", -1)));
        for (String part : parts) {
            if (part.isEmpty()) {
                return false;
            }
            for (int i = 0; i < part.length(); i++) {
                char c = part.charAt(i);
                boolean ok = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '-';
                if (!ok) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isNumeric(String text) {
        if (text.isEmpty() || text.length() > 18) {
            return false;
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) < '0' || text.charAt(i) > '9') {
                return false;
            }
        }
        return true;
    }
}
