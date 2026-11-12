package pnnh.mcfloatingbackpack.api;

import pnnh.mcfloatingbackpack.search.SearchTerms;

/**
 * The floating backpack's public API (version 1): add search terms to the backpack's search box. Safe to call on a dedicated server
 * (nothing is ever asked there). The contract is in {@code docs/api/search-terms.md}.
 *
 * <p>Depend on it the way an optional mod is depended on: {@code compileOnly}, an optional dependency on {@code mcfloatingbackpack} in
 * your {@code neoforge.mods.toml}, and touch this class only after {@code ModList.get().isLoaded("mcfloatingbackpack")}.
 */
public final class BackpackSearchApi {
    private static final int VERSION = 1;

    private BackpackSearchApi() {
    }

    /**
     * The version of this API, read at run time (a method on purpose: a constant would be inlined by the caller's compiler and a check
     * against it would always pass). Check {@code apiVersion() >= 1} before registering.
     */
    public static int apiVersion() {
        return VERSION;
    }

    /**
     * Registers the search terms provider of a mod. May be called from the mod's constructor, from {@code FMLClientSetupEvent} or later,
     * from any thread. A provider the same mod registered before is replaced.
     *
     * @param modId    your own mod id ({@code [a-z0-9_]+}); the player uses it in the {@code [compat] modLevels} setting to switch your
     *                 provider off (GENERIC or OFF)
     * @param provider the provider
     * @throws IllegalArgumentException if the mod id is not a mod id or the provider is null
     */
    public static void registerSearchTermsProvider(String modId, SearchTermsProvider provider) {
        SearchTerms.register(modId, provider);
    }
}
