package pnnh.mcfloatingbackpack.inventory;

/**
 * What may be put into a tool area (016, FR-011). Every item is accepted for now; this is the one place the
 * server asks, so a later feature can limit certain tools to certain areas by changing it here.
 */
public final class ToolAreaRules {
    private ToolAreaRules() {
    }

    public static <T> boolean canPlace(PanelArea area, T stack) {
        return true;
    }
}
