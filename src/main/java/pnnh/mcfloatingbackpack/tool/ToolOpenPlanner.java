package pnnh.mcfloatingbackpack.tool;

/**
 * 054: what the server does with an "open tool" request. Nothing the client says is believed: the server works out for itself whether the
 * backpack the B key opens has the tool in its right area and which of the mobile tools' menus (if any) the player has open.
 * <ul>
 * <li>A tool this version does not know, or one the backpack does not have in its right area: ignore (a menu of that kind that is open is
 * closed by its own validity check on the next tick, so there is nothing to do here).
 * <li>The menu of that very tool is open: close it (clicking the tool again folds it up, like the pages of the other tools).
 * <li>Otherwise open it. Whatever else is open (a chest, the other mobile tool's menu) is closed by opening the new one.
 * </ul>
 * Pure logic with no Minecraft types, so the whole table can be tested.
 */
public final class ToolOpenPlanner {
    public enum Decision { OPEN, CLOSE, IGNORE }

    private ToolOpenPlanner() {
    }

    /**
     * @param tool        the tool the request names, or null if the byte was not known
     * @param inRightArea whether the backpack the B key opens has that tool in its right area
     * @param openMenuTool the tool whose menu the player has open now, or null if no menu of ours is open
     */
    public static Decision decide(MobileTool tool, boolean inRightArea, MobileTool openMenuTool) {
        if (tool == null || !inRightArea) {
            return Decision.IGNORE;
        }
        return openMenuTool == tool ? Decision.CLOSE : Decision.OPEN;
    }
}
