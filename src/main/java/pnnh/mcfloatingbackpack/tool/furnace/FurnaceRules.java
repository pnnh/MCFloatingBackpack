package pnnh.mcfloatingbackpack.tool.furnace;

/**
 * 055: what the furnace engine needs to know from the outside, over an abstract stack type {@code S} (an {@code ItemStack} in the game, a
 * small record in the tests): the smelting recipe of an input, the burn time of a fuel, what a fuel leaves behind, and how stacks are told
 * apart and counted. Pure logic with no Minecraft types.
 */
public interface FurnaceRules<S> {
    /** A smelting recipe as the engine sees it: its id (for the experience), the ticks it takes, what it makes (already assembled) and the experience of one. */
    record Recipe<S>(String id, int cookTime, S result, float experience) {
    }

    /** The recipe of an input stack, or null if it has none. */
    Recipe<S> recipeFor(S input);

    /** How many ticks one unit of this fuel burns; 0 if it is no fuel. */
    int burnTime(S fuel);

    /** What takes the place of the whole fuel stack when a unit of it is used up (a lava bucket leaves a bucket); empty if it leaves nothing. */
    S remainder(S fuel);

    /** The fuel slot after the input has been smelted (a wet sponge smelted next to an empty bucket makes it a water bucket); usually the fuel itself. */
    S afterSmelt(S input, S fuel);

    S empty();

    boolean isEmpty(S stack);

    int count(S stack);

    /** The same stack with another count (a copy); a count of 0 or less gives the empty stack. */
    S withCount(S stack, int count);

    /** Same item and same components (counts are not compared). */
    boolean same(S a, S b);

    /** The most one stack of this kind can hold. */
    int maxStack(S stack);
}
