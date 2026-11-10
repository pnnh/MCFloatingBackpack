package pnnh.mcfloatingbackpack.version;

/**
 * 039: what to say to the player for a version comparison, and the plain text for a client too old to have this feature. Pure
 * logic with no Minecraft types: the client turns {@link Notice} into its language keys; the text for an old client is written
 * out in both languages because that client has none of the keys.
 */
public final class VersionNotices {
    /** The kind of message; {@link #SILENT} means say nothing. */
    public enum Notice {
        SILENT,
        OLDER,
        NEWER,
        UNKNOWN
    }

    private VersionNotices() {
    }

    public static Notice noticeFor(ModVersions.Result result) {
        return switch (result) {
            case MATCH -> Notice.SILENT;
            case CLIENT_OLDER -> Notice.OLDER;
            case CLIENT_NEWER -> Notice.NEWER;
            case UNCOMPARABLE -> Notice.UNKNOWN;
        };
    }

    /** The two lines (Chinese, then English) the server sends to a client that has no version check; a missing version reads "unknown". */
    public static String legacyClientText(String serverVersion) {
        String version = serverVersion == null || serverVersion.isBlank() ? "未知 / unknown" : serverVersion;
        return "[MCFloatingBackpack] 你的浮动背包版本过旧（它没有版本检测功能），服务器版本是 " + version
                + "。请升级到与服务器相同的版本；升级前请先备份存档。\n"
                + "[MCFloatingBackpack] Your MCFloatingBackpack is too old (it has no version check); the server runs " + version
                + ". Please update to the server's version, and back up your world before updating.";
    }
}
