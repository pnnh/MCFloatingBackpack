package pnnh.mcfloatingbackpack.tool.furnace;

import pnnh.mcfloatingbackpack.tool.furnace.FurnaceRules.Recipe;

/**
 * 055: the smelting of a vanilla furnace, worked out for a furnace that is not a block. {@link #tickOnce} is
 * {@code AbstractFurnaceBlockEntity.serverTick} of Minecraft 1.21.1 moved over as it is (every rule and every quirk kept, only what writes the
 * lit state into the world left out). {@link #advance} does the same for any number of ticks at once: whole stretches in which nothing happens
 * but the counters count (a lit furnace cooking one item, a fuel burning down, the progress of a cooled furnace falling back) are jumped in one
 * step, and only the ticks on which something happens (an item is finished, a fuel is used up or lit, the output is full) are worked out one by
 * one. So the work is about the number of items and fuels, not the number of ticks; and the result is the very one that ticking that many times
 * would give (tests compare the two on thousands of random furnaces).
 *
 * <p>The same function serves the open menu (one tick at a time) and the catch-up when a furnace is opened again, so there is one smelting rule.
 * Pure logic with no Minecraft types; the stacks are never changed in place.
 */
public final class FurnaceEngine {
    /** {@code Container.getMaxStackSize()} of vanilla (99, a furnace block entity does not change it): a furnace's output never goes beyond it. */
    public static final int CONTAINER_MAX_STACK = 99;
    /** Cooking time vanilla uses when the input has no recipe. */
    public static final int DEFAULT_COOK_TIME = 200;

    private FurnaceEngine() {
    }

    /** What a furnace holds in one tick: the vanilla tick, without the block. */
    public static <S> void tickOnce(FurnaceCore<S> core, FurnaceRules<S> rules) {
        if (core.litTime > 0) {
            core.litTime--;
        }
        boolean hasFuel = !rules.isEmpty(core.fuel);
        boolean hasInput = !rules.isEmpty(core.input);
        if (core.litTime > 0 || (hasFuel && hasInput)) {
            Recipe<S> recipe = hasInput ? rules.recipeFor(core.input) : null;
            if (core.litTime <= 0 && canBurn(core, recipe, rules)) {
                core.litTime = rules.burnTime(core.fuel);
                core.litDuration = core.litTime;
                if (core.litTime > 0) {
                    S remainder = rules.remainder(core.fuel);
                    if (!rules.isEmpty(remainder)) {
                        core.fuel = remainder;
                    } else if (hasFuel) {
                        core.fuel = rules.withCount(core.fuel, rules.count(core.fuel) - 1);
                    }
                }
            }
            if (core.litTime > 0 && canBurn(core, recipe, rules)) {
                core.cookProgress++;
                if (core.cookProgress == core.cookTotal) {
                    core.cookProgress = 0;
                    core.cookTotal = totalCookTime(core, rules);
                    if (burn(core, recipe, rules)) {
                        core.recipesUsed.merge(recipe.id(), 1, Integer::sum);
                    }
                }
            } else {
                core.cookProgress = 0;
            }
        } else if (core.litTime <= 0 && core.cookProgress > 0) {
            core.cookProgress = Math.max(0, Math.min(core.cookProgress - 2, core.cookTotal));
        }
    }

    /** The same as {@code ticks} calls of {@link #tickOnce}, for any {@code ticks >= 0}. */
    public static <S> void advance(FurnaceCore<S> core, long ticks, FurnaceRules<S> rules) {
        long left = ticks;
        while (left > 0) {
            long skipped = skip(core, left, rules);
            if (skipped > 0) {
                left -= skipped;
                continue;
            }
            S input = core.input;
            S fuel = core.fuel;
            S output = core.output;
            int lit = core.litTime;
            int duration = core.litDuration;
            int progress = core.cookProgress;
            int total = core.cookTotal;
            tickOnce(core, rules);
            left--;
            // A tick that changed nothing changes nothing for ever after (the next tick starts from the same state under the same rules).
            // The stacks are replaced, never changed in place, so an unchanged reference is an unchanged stack.
            if (input == core.input && fuel == core.fuel && output == core.output && lit == core.litTime && duration == core.litDuration
                    && progress == core.cookProgress && total == core.cookTotal) {
                return;
            }
        }
    }

    /**
     * Jumps over ticks in which only the counters move; returns how many it jumped (0 if the next tick is one on which something happens, or
     * nothing can be jumped). Never jumps over a tick that finishes an item, uses up or lights a fuel.
     */
    private static <S> long skip(FurnaceCore<S> core, long left, FurnaceRules<S> rules) {
        boolean hasInput = !rules.isEmpty(core.input);
        if (core.litTime >= 2) {
            // Lit, and still lit after each of the next litTime - 1 ticks.
            long k = Math.min(left, core.litTime - 1L);
            Recipe<S> recipe = hasInput ? rules.recipeFor(core.input) : null;
            if (canBurn(core, recipe, rules)) {
                if (core.cookTotal > core.cookProgress) {
                    k = Math.min(k, (long) core.cookTotal - core.cookProgress - 1);
                }
                if (k <= 0) {
                    return 0;
                }
                core.litTime -= (int) k;
                core.cookProgress += (int) k;
                return k;
            }
            // Burning with nothing to cook (the output is full, the input has no recipe): the fuel burns away for nothing, the progress stays 0.
            core.litTime -= (int) k;
            core.cookProgress = 0;
            return k;
        }
        boolean hasFuel = !rules.isEmpty(core.fuel);
        if (core.litTime == 0 && core.cookProgress > 0 && core.cookProgress <= core.cookTotal && !(hasFuel && hasInput)) {
            // Not lit and nothing to light: the progress falls back by 2 a tick until it is 0.
            long k = Math.min(left, (core.cookProgress + 1L) / 2);
            core.cookProgress = (int) Math.max(0, core.cookProgress - 2 * k);
            return k;
        }
        return 0;
    }

    private static <S> boolean canBurn(FurnaceCore<S> core, Recipe<S> recipe, FurnaceRules<S> rules) {
        if (rules.isEmpty(core.input) || recipe == null) {
            return false;
        }
        S result = recipe.result();
        if (rules.isEmpty(result)) {
            return false;
        }
        if (rules.isEmpty(core.output)) {
            return true;
        }
        if (!rules.same(core.output, result)) {
            return false;
        }
        int sum = rules.count(core.output) + rules.count(result);
        return sum <= CONTAINER_MAX_STACK && sum <= rules.maxStack(core.output) ? true : sum <= rules.maxStack(result);
    }

    private static <S> boolean burn(FurnaceCore<S> core, Recipe<S> recipe, FurnaceRules<S> rules) {
        if (recipe == null || !canBurn(core, recipe, rules)) {
            return false;
        }
        S result = recipe.result();
        if (rules.isEmpty(core.output)) {
            core.output = rules.withCount(result, rules.count(result));
        } else if (rules.same(core.output, result)) {
            core.output = rules.withCount(core.output, rules.count(core.output) + rules.count(result));
        }
        core.fuel = rules.afterSmelt(core.input, core.fuel);
        core.input = rules.withCount(core.input, rules.count(core.input) - 1);
        return true;
    }

    private static <S> int totalCookTime(FurnaceCore<S> core, FurnaceRules<S> rules) {
        Recipe<S> recipe = rules.isEmpty(core.input) ? null : rules.recipeFor(core.input);
        return recipe == null ? DEFAULT_COOK_TIME : recipe.cookTime();
    }
}
