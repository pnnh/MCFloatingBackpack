package pnnh.mcfloatingbackpack.tool.furnace;

/**
 * 055: the experience a furnace pays out when its output is taken, worked out the way vanilla does
 * ({@code AbstractFurnaceBlockEntity.createExperience}): the whole part of {@code count * experience}, plus one more with the probability of
 * its fractional part. The random number is passed in so the rule can be tested. Pure logic.
 */
public final class FurnaceExperience {
    private FurnaceExperience() {
    }

    /**
     * @param count      how many of the recipe were smelted
     * @param experience the experience of one
     * @param random     a number in [0, 1)
     */
    public static int orbs(int count, float experience, double random) {
        float total = (float) count * experience;
        int whole = (int) Math.floor(total);
        float fraction = total - (float) Math.floor(total);
        if (fraction != 0.0F && random < (double) fraction) {
            whole++;
        }
        return whole;
    }
}
