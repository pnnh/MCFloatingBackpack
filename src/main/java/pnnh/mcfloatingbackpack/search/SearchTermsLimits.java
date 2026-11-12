package pnnh.mcfloatingbackpack.search;

/**
 * 043: the numbers of the search terms API (version 1). They are also written in
 * {@code specs/043-search-terms-provider-api/contracts/search-terms-api.md}; {@code SearchTermsContractTest} keeps the two equal.
 */
public final class SearchTermsLimits {
    /** The most terms one provider may return for one item; the rest are ignored. */
    public static final int MAX_TERMS = 64;
    /** The longest a single term may be; a longer term is ignored as a whole (never cut, a cut term would mislead). */
    public static final int MAX_TERM_LENGTH = 128;
    /** After this many exceptions a provider is switched off for the session. */
    public static final int MAX_ERRORS = 20;
    /** How many results one provider's cache keeps (least recently used first out). */
    public static final int CACHE_SIZE = 2048;

    private SearchTermsLimits() {
    }
}
