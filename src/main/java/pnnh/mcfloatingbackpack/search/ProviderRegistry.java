package pnnh.mcfloatingbackpack.search;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * 043: the search terms providers other mods registered, and the one place they are asked. {@code S} is what a provider is asked about
 * (an {@code ItemStack} in the game, a plain string in the tests), so this class has no Minecraft types.
 *
 * <p>Asking is safe by construction: a provider is only asked when {@code enabled} says its mod may be used (the {@code modLevels}
 * setting, applied at the time of asking, so a changed setting needs no cleanup), its answer is cached per provider by {@code keyOf}
 * (so one item costs one call per cache generation, which matters because the tool areas ask every frame), an exception costs only
 * that item (and is counted, see {@link ProviderHealth}) and what it returns is made safe by {@link TermsNormalizer}.
 */
public final class ProviderRegistry<S> {
    /** Where the registry says things (the game's log in production). */
    public interface WarnSink {
        void warn(String message);

        void info(String message);

        void debug(String message);
    }

    private static final class Entry<S> {
        final String modId;
        final Function<S, ? extends Collection<String>> fn;
        final TermsCache<Object, List<String>> cache = new TermsCache<>(SearchTermsLimits.CACHE_SIZE);
        final ProviderHealth health = new ProviderHealth();

        Entry(String modId, Function<S, ? extends Collection<String>> fn) {
            this.modId = modId;
            this.fn = fn;
        }
    }

    private final Map<String, Entry<S>> entries = new ConcurrentHashMap<>();
    private final Function<S, Object> keyOf;
    private final UnaryOperator<S> prepare;
    private final Predicate<String> enabled;
    private final WarnSink sink;

    public ProviderRegistry(Function<S, Object> keyOf, UnaryOperator<S> prepare, Predicate<String> enabled, WarnSink sink) {
        this.keyOf = keyOf;
        this.prepare = prepare;
        this.enabled = enabled;
        this.sink = sink;
    }

    /** Registers a provider for a mod; a provider the mod registered before is replaced (and its cache is gone). */
    public void register(String modId, Function<S, ? extends Collection<String>> fn) {
        Entry<S> previous = entries.put(modId, new Entry<>(modId, fn));
        if (previous != null) {
            sink.debug("Search terms provider of mod '" + modId + "' replaced");
        } else {
            sink.info("Search terms provider registered by mod '" + modId + "'");
        }
    }

    /** Whether any provider would be asked right now. When false the search does exactly what it did without providers. */
    public boolean hasActive() {
        for (Entry<S> entry : entries.values()) {
            if (isActive(entry)) {
                return true;
            }
        }
        return false;
    }

    private boolean isActive(Entry<S> entry) {
        boolean disabled;
        synchronized (entry) {
            disabled = entry.health.isDisabled();
        }
        return !disabled && enabled.test(entry.modId);
    }

    /** The extra searchable texts (lower-case, no duplicates) every active provider gives for one thing. */
    public List<String> termsFor(S subject) {
        List<String> single = null;
        LinkedHashSet<String> merged = null;
        Object key = null;
        for (Entry<S> entry : entries.values()) {
            if (!isActive(entry)) {
                continue;
            }
            if (key == null) {
                key = keyOf.apply(subject);
            }
            List<String> terms = termsOf(entry, subject, key);
            if (terms.isEmpty()) {
                continue;
            }
            if (single == null) {
                single = terms;
            } else {
                if (merged == null) {
                    merged = new LinkedHashSet<>(single);
                }
                merged.addAll(terms);
            }
        }
        if (merged != null) {
            return List.copyOf(merged);
        }
        return single == null ? List.of() : single;
    }

    private List<String> termsOf(Entry<S> entry, S subject, Object key) {
        synchronized (entry) {
            List<String> cached = entry.cache.get(key);
            if (cached != null) {
                return cached;
            }
            List<String> terms;
            try {
                Collection<String> raw = entry.fn.apply(prepare.apply(subject));
                TermsNormalizer.Result result = TermsNormalizer.normalize(raw);
                if (result.tooMany() && entry.health.recordViolation(ProviderHealth.Violation.TOO_MANY)) {
                    sink.warn("Search terms provider of mod '" + entry.modId + "' returned more than " + SearchTermsLimits.MAX_TERMS
                            + " terms for an item; the rest are ignored");
                }
                if (result.tooLong() && entry.health.recordViolation(ProviderHealth.Violation.TOO_LONG)) {
                    sink.warn("Search terms provider of mod '" + entry.modId + "' returned a term longer than "
                            + SearchTermsLimits.MAX_TERM_LENGTH + " characters; such terms are ignored");
                }
                terms = result.terms();
            } catch (RuntimeException | LinkageError error) {
                ProviderHealth.ErrorOutcome outcome = entry.health.recordError(error.getClass());
                if (outcome.warnThisClass()) {
                    sink.warn("Search terms provider of mod '" + entry.modId + "' threw " + error.getClass().getName() + ": "
                            + error.getMessage() + "; its terms for that item are ignored");
                }
                if (outcome.switchedOffNow()) {
                    sink.warn("Search terms provider of mod '" + entry.modId + "' failed " + SearchTermsLimits.MAX_ERRORS
                            + " times and is switched off for this session");
                }
                // cache the empty answer: an item that makes the provider fail must not be asked again every frame
                terms = List.of();
            }
            entry.cache.put(key, terms);
            return terms;
        }
    }

    /** Forgets every cached answer (the resources were reloaded, so the providers' terms may have changed). Health is kept. */
    public void clearCaches() {
        for (Entry<S> entry : entries.values()) {
            synchronized (entry) {
                entry.cache.clear();
            }
        }
    }
}
