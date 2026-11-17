package pnnh.mcfloatingbackpack.tool.furnace;

import java.util.Map;

/** 055: a small furnace world for the tests: a few items, three recipes, four fuels, a bucket that stays behind. */
final class FakeFurnaceRules implements FurnaceRules<FakeFurnaceRules.Stack> {
    record Stack(String item, int count) {
    }

    static final Stack EMPTY = new Stack("", 0);
    static final FakeFurnaceRules RULES = new FakeFurnaceRules();

    private static final Map<String, Recipe<Stack>> RECIPES = Map.of(
            "iron_ore", new Recipe<>("smelt_iron", 200, new Stack("iron_ingot", 1), 0.7F),
            "sand", new Recipe<>("smelt_glass", 100, new Stack("glass", 1), 0.1F),
            "wet_sponge", new Recipe<>("smelt_sponge", 50, new Stack("sponge", 1), 0.0F),
            "ore_pair", new Recipe<>("smelt_pair", 30, new Stack("ingot_pair", 2), 1.0F));
    private static final Map<String, Integer> FUELS = Map.of("coal", 1600, "stick", 100, "lava_bucket", 20000, "plank", 300);

    static Stack s(String item, int count) {
        return count <= 0 ? EMPTY : new Stack(item, count);
    }

    @Override
    public Recipe<Stack> recipeFor(Stack input) {
        return input.count() <= 0 ? null : RECIPES.get(input.item());
    }

    @Override
    public int burnTime(Stack fuel) {
        return fuel.count() <= 0 ? 0 : FUELS.getOrDefault(fuel.item(), 0);
    }

    @Override
    public Stack remainder(Stack fuel) {
        return fuel.item().equals("lava_bucket") ? new Stack("bucket", 1) : EMPTY;
    }

    @Override
    public Stack afterSmelt(Stack input, Stack fuel) {
        return input.item().equals("wet_sponge") && fuel.item().equals("bucket") && fuel.count() > 0 ? new Stack("water_bucket", 1) : fuel;
    }

    @Override
    public Stack empty() {
        return EMPTY;
    }

    @Override
    public boolean isEmpty(Stack stack) {
        return stack.count() <= 0;
    }

    @Override
    public int count(Stack stack) {
        return stack.count();
    }

    @Override
    public Stack withCount(Stack stack, int count) {
        return count <= 0 ? EMPTY : new Stack(stack.item(), count);
    }

    @Override
    public boolean same(Stack a, Stack b) {
        return a.item().equals(b.item());
    }

    @Override
    public int maxStack(Stack stack) {
        return switch (stack.item()) {
            case "lava_bucket", "water_bucket" -> 1;
            case "bucket", "ingot_pair" -> 16;
            default -> 64;
        };
    }
}
