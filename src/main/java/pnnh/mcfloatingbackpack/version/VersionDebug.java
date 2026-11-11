package pnnh.mcfloatingbackpack.version;

/**
 * 039: switches for trying the version check out in a development run (a single client is enough to see most cases). They are
 * system properties, all off when they are not given; a normal run never sets them. Given to the game with {@code -Pmcfb.<name>=<value>}
 * on {@code ./gradlew runClient} (build.gradle turns it into {@code -Dmcfloatingbackpack.debug.<name>}).
 *
 * <ul>
 *   <li>{@code checkOwner=true}: the server does not skip the owner of a single player game or a LAN world, so the handshake runs in a
 *       single player world too;
 *   <li>{@code serverVersion=X}: the server says its version is X;
 *   <li>{@code clientVersion=X}: the client takes its own version to be X (in the comparison and in what it reports);
 *   <li>{@code legacyClient=true}: the server treats every player as a client without the version check (the plain text for them);
 *   <li>{@code stampRevision=N}: 040, every backpack save stamps the data revision N instead of the current one (99 makes a backpack
 *       that looks as if a newer version had saved it, to try the downgrade protection out);
 *   <li>{@code worldMarkerRevision=N}: 041, the world marker is written with the data revision N instead of the current one (99 makes a
 *       world that the next start without the switch refuses); only when N is higher than the marker already is.
 * </ul>
 *
 * <p>{@link #acceptDowngrade()} is not a debug switch but the parameter a player gives, {@code -Dmcfloatingbackpack.acceptDowngrade=true},
 * to start a world whose marker is higher than this version (so it has no {@code debug.} in its name); in a development run it is
 * {@code -Pmcfb.acceptDowngrade=true}, which build.gradle turns into that property.
 */
public final class VersionDebug {
    private static final String PREFIX = "mcfloatingbackpack.debug.";

    private VersionDebug() {
    }

    public static boolean checkOwner() {
        return Boolean.getBoolean(PREFIX + "checkOwner");
    }

    public static boolean legacyClient() {
        return Boolean.getBoolean(PREFIX + "legacyClient");
    }

    /** The version the server says it has, or null when it is not overridden. */
    public static String serverVersionOverride() {
        return System.getProperty(PREFIX + "serverVersion");
    }

    /** The version the client says it has, or null when it is not overridden. */
    public static String clientVersionOverride() {
        return System.getProperty(PREFIX + "clientVersion");
    }

    /** 041: whether the player accepted the risk of starting a world that a newer data revision saved (for this run only). */
    public static boolean acceptDowngrade() {
        return Boolean.getBoolean("mcfloatingbackpack.acceptDowngrade");
    }

    /** 041: the data revision the world marker is written with instead of the current one, or null when it is not overridden (or not a number). */
    public static Integer worldMarkerRevisionOverride() {
        return parseInt(System.getProperty(PREFIX + "worldMarkerRevision"));
    }

    private static Integer parseInt(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }

    /** 040: the data revision every save stamps instead of the current one, or null when it is not overridden (or not a number). */
    public static Integer stampRevisionOverride() {
        String value = System.getProperty(PREFIX + "stampRevision");
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
