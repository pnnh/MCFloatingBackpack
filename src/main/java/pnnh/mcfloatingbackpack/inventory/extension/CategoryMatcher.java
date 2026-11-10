package pnnh.mcfloatingbackpack.inventory.extension;

/**
 * 035: whether a stack fits a category. In the game it is true when the item is in the data tag of that category (or
 * has the food / potion component) or the backpack's own tags (018) give the item the category's id; the two sources are
 * alternatives. Only asked when something is placed: what is already in a row is never taken out because this changed.
 */
@FunctionalInterface
public interface CategoryMatcher<T> {
    boolean matches(String category, T stack);
}
