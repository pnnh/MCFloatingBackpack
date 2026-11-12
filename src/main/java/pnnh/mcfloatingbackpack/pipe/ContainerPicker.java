package pnnh.mcfloatingbackpack.pipe;

import java.util.function.IntPredicate;

/**
 * 050: where a discharge takes its empty container from: the cells of the backpack's main area first, then the player's inventory (the
 * hotbar first). Positions are numbered in that order: {@code 0 .. mainSize-1} are the backpack's main cells, {@code mainSize ..} the
 * inventory. Pure logic, no Minecraft types.
 */
public final class ContainerPicker {
    private ContainerPicker() {
    }

    /** The first position whose stack matches, or -1 if none does. */
    public static int firstMatch(int mainSize, int inventorySize, IntPredicate matches) {
        int total = Math.max(0, mainSize) + Math.max(0, inventorySize);
        for (int position = 0; position < total; position++) {
            if (matches.test(position)) {
                return position;
            }
        }
        return -1;
    }

    /** Whether the position is a cell of the backpack's main area (not the inventory). */
    public static boolean isInBackpack(int position, int mainSize) {
        return position >= 0 && position < mainSize;
    }

    /** The index within the inventory of a position that is not in the backpack. */
    public static int inventoryIndex(int position, int mainSize) {
        return position - mainSize;
    }
}
