package pnnh.mcfloatingbackpack.pipe;

import pnnh.mcfloatingbackpack.tank.FluidKind;

/**
 * 049/050: the five pipes. A pipe takes in and pours out one liquid of the backpack's tank for it. What counts as that liquid in the world
 * is told by fluid tags (the caller reads them): water, lava, and for the oil any fluid in {@code #c:crude_oil}; milk and honey have no fluid in
 * the world (a cow, a beehive give them), so a milk or honey pipe has no block form and nothing to place. Every pipe can also fill an empty
 * container (050): a bucket, or for honey a glass bottle. Pure logic, no Minecraft types.
 */
public enum PipeKind {
    WATER(FluidKind.WATER, true, Container.BUCKET),
    OIL(FluidKind.OIL, true, Container.BUCKET),
    LAVA(FluidKind.LAVA, true, Container.BUCKET),
    MILK(FluidKind.MILK, false, Container.BUCKET),
    HONEY(FluidKind.HONEY, false, Container.GLASS_BOTTLE);

    /** What an action fills: an empty bucket, or an empty glass bottle (honey). */
    public enum Container { BUCKET, GLASS_BOTTLE }

    private final FluidKind fluid;
    private final boolean blockForm;
    private final Container emptyContainer;

    PipeKind(FluidKind fluid, boolean blockForm, Container emptyContainer) {
        this.fluid = fluid;
        this.blockForm = blockForm;
        this.emptyContainer = emptyContainer;
    }

    /** How much one action moves: a bucket, or a bottle for honey (250 mB). */
    public int unitMb() {
        return fluid.perUseMb();
    }

    /** Whether the liquid exists as a block in the world, which a pipe can take from and pour out as a source block (water, oil, lava). */
    public boolean hasBlockForm() {
        return blockForm;
    }

    /** The empty container a discharge uses up. */
    public Container emptyContainer() {
        return emptyContainer;
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
