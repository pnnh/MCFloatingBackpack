package pnnh.mcfloatingbackpack.pipe;

import pnnh.mcfloatingbackpack.tank.FluidKind;

/**
 * 049: the three pipes (the milk and honey pipes come with 050). A pipe takes in and pours out one liquid of the backpack's tank for it. What
 * counts as that liquid in the world is told by fluid tags (the caller reads them): water, lava, and for the oil any fluid in
 * {@code #c:crude_oil}. Pure logic, no Minecraft types.
 */
public enum PipeKind {
    WATER(FluidKind.WATER),
    OIL(FluidKind.OIL),
    LAVA(FluidKind.LAVA);

    private final FluidKind fluid;

    PipeKind(FluidKind fluid) {
        this.fluid = fluid;
    }

    /** The liquid of the tank this pipe works with. */
    public FluidKind fluid() {
        return fluid;
    }

    /** The item id (without the mod namespace): {@code <liquid>_pipe}. */
    public String itemId() {
        return fluid.id() + "_pipe";
    }

    /**
     * Which pipe a fluid belongs to, from the tags it is in; {@code null} when none. A fluid in more than one of the three tags goes to the
     * first of water, lava, oil.
     */
    public static PipeKind classify(boolean inWater, boolean inLava, boolean inCrudeOil) {
        if (inWater) {
            return WATER;
        }
        if (inLava) {
            return LAVA;
        }
        return inCrudeOil ? OIL : null;
    }

    /** Whether this pipe takes a fluid that is in these tags. */
    public boolean accepts(boolean inWater, boolean inLava, boolean inCrudeOil) {
        return classify(inWater, inLava, inCrudeOil) == this;
    }
}
