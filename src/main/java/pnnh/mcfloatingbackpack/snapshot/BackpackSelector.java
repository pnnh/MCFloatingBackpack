package pnnh.mcfloatingbackpack.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import pnnh.mcfloatingbackpack.inventory.identity.BackpackIds;

/**
 * 030: what a player types to name a backpack: its four-character id (any case), its full UUID, or the start of its UUID (six
 * characters or more). An id can belong to several backpacks (it is worked out from the UUID and is not unique), so
 * {@link #resolve} returns every match and the caller decides what to do with more than one. Pure logic.
 */
public final class BackpackSelector {
    /** How short a UUID prefix may be: shorter ones match too many backpacks to mean anything. */
    public static final int MIN_PREFIX = 6;

    private static final Pattern PREFIX = Pattern.compile("[0-9a-f-]{" + MIN_PREFIX + ",36}");

    public enum Kind {
        ID,
        UUID_FULL,
        UUID_PREFIX,
        INVALID
    }

    /** {@code text} is normalised: an id in capitals, a UUID or prefix in lower case. */
    public record Parsed(Kind kind, String text) {
    }

    private BackpackSelector() {
    }

    public static Parsed parse(String input) {
        if (input == null) {
            return new Parsed(Kind.INVALID, "");
        }
        String text = input.trim();
        String upper = text.toUpperCase(Locale.ROOT);
        if (BackpackIds.isValid(upper)) {
            return new Parsed(Kind.ID, upper);
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.length() == 36) {
            try {
                return new Parsed(Kind.UUID_FULL, UUID.fromString(lower).toString());
            } catch (IllegalArgumentException notAUuid) {
                return new Parsed(Kind.INVALID, text);
            }
        }
        if (PREFIX.matcher(lower).matches()) {
            return new Parsed(Kind.UUID_PREFIX, lower);
        }
        return new Parsed(Kind.INVALID, text);
    }

    /** Every backpack the selector names: none, one, or several (an id several backpacks share). */
    public static List<UUID> resolve(Iterable<UUID> uuids, Parsed parsed) {
        List<UUID> matches = new ArrayList<>();
        if (parsed.kind() == Kind.INVALID) {
            return matches;
        }
        for (UUID uuid : uuids) {
            boolean match = switch (parsed.kind()) {
                case ID -> BackpackIds.of(uuid).equals(parsed.text());
                case UUID_FULL -> uuid.toString().equals(parsed.text());
                case UUID_PREFIX -> uuid.toString().startsWith(parsed.text());
                case INVALID -> false;
            };
            if (match) {
                matches.add(uuid);
            }
        }
        return matches;
    }
}
