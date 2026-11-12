package pnnh.mcfloatingbackpack.api;

import java.util.Collection;

import net.minecraft.world.item.ItemStack;

/**
 * Gives the floating backpack's search box more text to find an item by: the item's name in other languages, its pinyin, and so on.
 * Register one with {@link BackpackSearchApi#registerSearchTermsProvider}. The full contract (limits, errors, when it is called) is in
 * {@code docs/api/search-terms.md}.
 *
 * <p>What you may rely on: you are called on the client's render thread, only while the player has typed a query, with a non-empty
 * stack of count 1 that is a copy (do not rely on the count; changing the copy does nothing). What is asked of you: be fast (no I/O,
 * no waiting, no world access; answer from a table you built beforehand). The answer is cached per item and its data components until
 * the resources reload, so it must depend only on those.
 *
 * <p>What comes back: {@code null} or empty means "nothing more". Each term is lower-cased and then looked for as a substring of the
 * query's words. Blank terms are ignored, a term over 128 characters is ignored as a whole, only the first 64 terms of one answer are
 * used. A term with a space can only be found by a quoted query, so prefer terms without spaces. The terms only add: they cannot
 * change what the player sees, the order of the results or what a query means.
 */
@FunctionalInterface
public interface SearchTermsProvider {
    /** More texts this item can be found by; {@code null} or empty for none. */
    Collection<String> termsFor(ItemStack stack);
}
