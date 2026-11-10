package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 035: the extension packs, the items that add a row when they sit in a tool area. This is the one place that knows which
 * item is a pack of which category; with nothing registered (the case until the pack items exist) it recognises nothing, so
 * the rows of every backpack are reconciled down to none and the game is as it was before the category extensions.
 */
public final class ExtensionPacks {
    private static final List<Function<ItemStack, String>> RESOLVERS = new CopyOnWriteArrayList<>();
    /** 038: the resolvers of the tier a pack belongs to (only the universal extensions have one). */
    private static final List<Function<ItemStack, String>> TIER_RESOLVERS = new CopyOnWriteArrayList<>();

    private ExtensionPacks() {
    }

    /** Adds a resolver: the category id of a pack, or {@code null} for a stack that is not one of its packs. */
    public static void register(Function<ItemStack, String> resolver) {
        RESOLVERS.add(resolver);
    }

    /**
     * 038: adds a resolver of the tier a pack belongs to ({@code "leather"} ... {@code "netherite"}), or {@code null} for a stack that
     * is not a tiered pack. Only the universal extensions are tiered; they count only in a backpack of their own tier.
     */
    public static void registerTier(Function<ItemStack, String> resolver) {
        TIER_RESOLVERS.add(resolver);
    }

    /** 038: the tier of a tiered pack (a universal extension), or {@code null} for any other stack. */
    public static String tierOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Function<ItemStack, String> resolver : TIER_RESOLVERS) {
            String tier = resolver.apply(stack);
            if (tier != null) {
                return tier;
            }
        }
        return null;
    }

    /** The category of a pack, or {@code null} when the stack is not a pack. */
    public static String categoryOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Function<ItemStack, String> resolver : RESOLVERS) {
            String category = resolver.apply(stack);
            if (category != null) {
                return category;
            }
        }
        return null;
    }

    /**
     * How many packs of each category the stacks hold, a stack counting as one pack whatever its count (packs stack to
     * one; a bigger count is logged and still counted once).
     */
    public static Map<String, Integer> countPlaced(Iterable<ItemStack> stacks) {
        Map<String, Integer> placed = new HashMap<>();
        for (ItemStack stack : stacks) {
            String category = categoryOf(stack);
            if (category == null) {
                continue;
            }
            if (stack.getCount() > 1) {
                MCFloatingBackpack.LOGGER.warn("An extension pack stack of {} holds {} items; counted as one", category, stack.getCount());
            }
            placed.merge(category, 1, Integer::sum);
        }
        return placed;
    }

    /** True when nothing is registered, so that callers can skip the work. */
    public static boolean isEmpty() {
        return RESOLVERS.isEmpty();
    }
}
