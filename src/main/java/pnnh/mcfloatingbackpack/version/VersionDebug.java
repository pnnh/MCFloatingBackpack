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
 *   <li>{@code legacyClient=true}: the server treats every player as a client without the version check (the plain text for them).
 * </ul>
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
}
