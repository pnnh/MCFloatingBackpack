package pnnh.mcfloatingbackpack.compat;

/**
 * 059: the level the server goes by for one mod: the stricter of the server config's level and the client config's level (the client's is
 * only there where the server can read it, a single player or LAN world; on a dedicated server the client config is not loaded and counts
 * as everything FULL, which changes nothing here). {@link CompatLevel} is declared from the loosest to the strictest, so the stricter one
 * is the one declared later. Pure logic.
 */
public final class EffectiveLevel {
    private EffectiveLevel() {
    }

    /** The stricter of the two (FULL, then GENERIC, then OFF); a missing level counts as FULL. */
    public static CompatLevel stricter(CompatLevel a, CompatLevel b) {
        CompatLevel left = a == null ? CompatLevel.FULL : a;
        CompatLevel right = b == null ? CompatLevel.FULL : b;
        return left.ordinal() >= right.ordinal() ? left : right;
    }

    /** Vanilla and a mod that is not listed in either are FULL ({@link CompatPolicy#levelOf} already says so). */
    public static CompatLevel effective(CompatPolicy server, CompatPolicy client, String modId) {
        CompatLevel fromServer = server == null ? CompatLevel.FULL : server.levelOf(modId);
        CompatLevel fromClient = client == null ? CompatLevel.FULL : client.levelOf(modId);
        return stricter(fromServer, fromClient);
    }
}
