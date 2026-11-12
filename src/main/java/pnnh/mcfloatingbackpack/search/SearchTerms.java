package pnnh.mcfloatingbackpack.search;

import java.util.List;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.api.SearchTermsProvider;
import pnnh.mcfloatingbackpack.compat.CompatConfig;

/**
 * 043: the game's one {@link ProviderRegistry} of search terms providers (see {@code BackpackSearchApi}). Common code, so another mod
 * can register from its constructor on a dedicated server too; nothing ever asks there. A provider is used only while its mod's level in
 * {@code [compat] modLevels} is FULL (not listed counts as FULL): GENERIC and OFF both mean "ignore its terms".
 */
public final class SearchTerms {
    private static final ProviderRegistry<ItemStack> REGISTRY = new ProviderRegistry<>(
            StackKey::of,
            stack -> stack.copyWithCount(1),
            modId -> CompatConfig.policy().specificAdaptation(modId),
            new ProviderRegistry.WarnSink() {
                @Override
                public void warn(String message) {
                    MCFloatingBackpack.LOGGER.warn(message);
                }

                @Override
                public void info(String message) {
                    MCFloatingBackpack.LOGGER.info(message);
                }

                @Override
                public void debug(String message) {
                    MCFloatingBackpack.LOGGER.debug(message);
                }
            });

    private SearchTerms() {
    }

    /** The facade's registration: the checks of the contract, then the registry. */
    public static void register(String modId, SearchTermsProvider provider) {
        if (!ProviderIds.isValid(modId)) {
            throw new IllegalArgumentException("modId must be a mod id (a-z, 0-9 and _), got: " + modId);
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider must not be null");
        }
        REGISTRY.register(modId, provider::termsFor);
    }

    /** Whether any provider would be asked now; when false the search is exactly what it was without providers. */
    public static boolean hasActive() {
        return REGISTRY.hasActive();
    }

    /** The extra searchable texts (lower-case) the active providers give for this item. */
    public static List<String> termsFor(ItemStack stack) {
        return REGISTRY.termsFor(stack);
    }

    /** The resources were reloaded (a language switch, F3+T, a resource pack): the providers' terms may have changed. */
    public static void onResourcesReloaded() {
        REGISTRY.clearCaches();
    }
}
