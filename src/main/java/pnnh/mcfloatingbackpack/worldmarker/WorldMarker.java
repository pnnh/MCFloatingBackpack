package pnnh.mcfloatingbackpack.worldmarker;

import java.io.IOException;
import java.io.StringReader;
import java.util.Properties;

/**
 * 041: the marker a world carries, {@code <world>/mcfloatingbackpack/world_marker.properties}: the highest data revision of any
 * version of the mod that has touched the world ({@code data_revision}, the only field the guard acts on) and, for the message only,
 * which version wrote it last ({@code mod_version}).
 *
 * <p>The text is {@code key=value} so a player can edit it by hand: deleting the file, or lowering {@code data_revision}, accepts the
 * downgrade for good. Pure logic with no Minecraft types.
 */
public record WorldMarker(int dataRevision, String modVersion) {
    public static final String KEY_REVISION = "data_revision";
    public static final String KEY_VERSION = "mod_version";

    private static final String HEADER = "# MCFloatingBackpack world marker. Delete this file, or lower data_revision, to accept the downgrade risk.";

    public WorldMarker {
        if (dataRevision < 0) {
            throw new IllegalArgumentException("data_revision must not be negative: " + dataRevision);
        }
        if (modVersion != null) {
            modVersion = modVersion.strip();
            if (modVersion.isEmpty()) {
                modVersion = null;
            }
        }
    }

    /** Reads marker text: {@link MarkerRead.Valid}, or {@link MarkerRead.Corrupt} when {@code data_revision} is missing, not a whole number or negative. */
    public static MarkerRead parse(String text) {
        if (text == null) {
            return new MarkerRead.Corrupt("no content");
        }
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(text));
        } catch (IOException | IllegalArgumentException malformed) {
            return new MarkerRead.Corrupt("malformed: " + malformed.getMessage());
        }
        String raw = properties.getProperty(KEY_REVISION);
        if (raw == null) {
            return new MarkerRead.Corrupt(KEY_REVISION + " is missing");
        }
        int revision;
        try {
            revision = Integer.parseInt(raw.strip());
        } catch (NumberFormatException notANumber) {
            return new MarkerRead.Corrupt(KEY_REVISION + " is not a whole number: " + raw.strip());
        }
        if (revision < 0) {
            return new MarkerRead.Corrupt(KEY_REVISION + " is negative: " + revision);
        }
        return new MarkerRead.Valid(new WorldMarker(revision, properties.getProperty(KEY_VERSION)));
    }

    /** The text to write: a comment line, {@code data_revision}, and {@code mod_version} when there is one. */
    public String toText() {
        StringBuilder text = new StringBuilder(HEADER).append('\n').append(KEY_REVISION).append('=').append(dataRevision).append('\n');
        if (modVersion != null) {
            text.append(KEY_VERSION).append('=').append(escape(modVersion)).append('\n');
        }
        return text.toString();
    }

    /** Escapes what {@link Properties#load} would read differently: the backslash, and line breaks (a version has none; they are dropped). */
    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\r", "").replace("\n", "");
    }
}
