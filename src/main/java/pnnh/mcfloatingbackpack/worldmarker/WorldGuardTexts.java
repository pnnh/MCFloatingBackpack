package pnnh.mcfloatingbackpack.worldmarker;

/**
 * 041: the words of the guard. The refusal happens while the server starts, when there is no client and so no language to follow, and it
 * shows as a log and a crash report, both plain text: so every text is one fixed English-and-Chinese string, not a language key. Pure
 * strings with no Minecraft types. The texts are in {@code specs/041-world-downgrade-guard/contracts/texts.md}.
 */
public final class WorldGuardTexts {
    /** The parameter that accepts the risk for one run. */
    public static final String ACCEPT_PARAMETER = "-Dmcfloatingbackpack.acceptDowngrade=true";

    private WorldGuardTexts() {
    }

    /** The crash report's description: the first line the player sees when a single player game is refused. */
    public static String crashTitle(int stored, int current) {
        return "MCFloatingBackpack: this world was saved by a newer data revision (" + stored + " > " + current
                + "); refusing to load it / 此存档由更新的数据修订保存（" + stored + " > " + current + "），拒绝加载";
    }

    /** The explanation: in the log as an ERROR and as the crash report's message. */
    public static String refuseDetail(int stored, String markerVersion, int current, String modVersion, String markerPath) {
        String was = markerVersion == null ? "unknown" : markerVersion;
        String wasZh = markerVersion == null ? "未知" : markerVersion;
        String now = modVersion == null ? "unknown" : modVersion;
        String nowZh = modVersion == null ? "未知" : modVersion;
        return "\n[English]\n"
                + "This world was last saved by MCFloatingBackpack " + was + " (data revision " + stored + "); this is " + now
                + " (data revision " + current + ").\n"
                + "Loading it with an older version can silently drop backpack data, and the loss cannot be undone. "
                + "Nothing in the world was read or changed by this mod.\n"
                + "Update the mod, or back up the world first. To accept the risk anyway:\n"
                + "  1. one time: add the JVM argument " + ACCEPT_PARAMETER + "; or\n"
                + "  2. for good: delete " + markerPath + " (or lower data_revision in it).\n"
                + "\n[中文]\n"
                + "此存档最后由 MCFloatingBackpack " + wasZh + "（数据修订 " + stored + "）保存；当前是 " + nowZh
                + "（数据修订 " + current + "）。\n"
                + "用旧版本加载可能悄悄丢失背包数据，且无法找回。本模组没有读取或改动存档里的任何内容。\n"
                + "请升级模组，或先备份存档。若仍要接受风险：\n"
                + "  1. 一次性：加 JVM 参数 " + ACCEPT_PARAMETER + "；或\n"
                + "  2. 永久：删除 " + markerPath + "（或把其中的 data_revision 改低）。\n";
    }

    public static String acceptedWarn(int stored, int current) {
        return "MCFloatingBackpack: world marker revision " + stored + " is higher than this version's " + current + "; accepted by "
                + ACCEPT_PARAMETER + ", backpack data may be lost. / 存档标记修订 " + stored + " 高于当前版本的 " + current
                + "；已按启动参数放行，背包数据可能丢失。";
    }

    public static String corruptWarn(String markerPath, String reason) {
        return "MCFloatingBackpack: world marker " + markerPath + " is unreadable (" + reason
                + "); treating it as revision 0 and rewriting it. / 存档标记 " + markerPath + " 无法读取（" + reason + "）；按修订 0 处理并重写。";
    }

    public static String writeFailedWarn(String markerPath, String reason) {
        return "MCFloatingBackpack: could not write world marker " + markerPath + " (" + reason
                + "); the downgrade guard has no effect for this run. / 无法写入存档标记 " + markerPath + "（" + reason + "）；本次降级拦截不生效。";
    }

    /** @param previous the revision the marker had, or null when there was none */
    public static String markerSetInfo(int revision, Integer previous) {
        return "MCFloatingBackpack: world marker set to data revision " + revision + " (was " + (previous == null ? "none" : previous)
                + "). / 存档标记已设为数据修订 " + revision + "（原为 " + (previous == null ? "无" : previous) + "）。";
    }
}
