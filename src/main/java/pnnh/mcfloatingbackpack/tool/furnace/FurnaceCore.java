package pnnh.mcfloatingbackpack.tool.furnace;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 055: the state of a furnace as the engine works on it: the three slots (input, fuel, output), the four counters a vanilla furnace keeps
 * ({@code litTime}, {@code litDuration}, {@code cookProgress}, {@code cookTotal}) and how many of each recipe have been smelted since the experience
 * was last taken. Mutable; {@link FurnaceEngine} changes it. It has no clock: the caller knows up to which game time the state is worked out.
 */
public final class FurnaceCore<S> {
    public S input;
    public S fuel;
    public S output;
    public int litTime;
    public int litDuration;
    public int cookProgress;
    public int cookTotal;
    /** Recipe id to how many were smelted and have not been paid out as experience yet. */
    public final Map<String, Integer> recipesUsed = new LinkedHashMap<>();

    public FurnaceCore(S input, S fuel, S output) {
        this.input = input;
        this.fuel = fuel;
        this.output = output;
    }

    public FurnaceCore<S> copy(FurnaceRules<S> rules) {
        FurnaceCore<S> core = new FurnaceCore<>(
                rules.withCount(input, rules.count(input)), rules.withCount(fuel, rules.count(fuel)), rules.withCount(output, rules.count(output)));
        core.litTime = litTime;
        core.litDuration = litDuration;
        core.cookProgress = cookProgress;
        core.cookTotal = cookTotal;
        core.recipesUsed.putAll(recipesUsed);
        return core;
    }
}
