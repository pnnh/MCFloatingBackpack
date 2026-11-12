package pnnh.mcfloatingbackpack.search;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

/**
 * 043: how one provider has behaved this session. An exception is counted; the first exception of each class is worth a warning
 * (never one per call, a broken provider would flood the log); after {@link SearchTermsLimits#MAX_ERRORS} exceptions it is switched
 * off for the session. Returning too much or too long terms is warned once per kind and does not count towards switching off.
 * Not thread-safe: the owner synchronizes. No Minecraft types.
 */
public final class ProviderHealth {
    /** Ways a provider's return value can be unreasonable (not an error, the safe part of it is still used). */
    public enum Violation {
        TOO_MANY,
        TOO_LONG
    }

    /** What the caller should log after an exception. */
    public record ErrorOutcome(boolean warnThisClass, boolean switchedOffNow) {
    }

    private int errors;
    private boolean disabled;
    private final Set<Class<?>> warnedClasses = new HashSet<>();
    private final Set<Violation> warnedViolations = EnumSet.noneOf(Violation.class);

    public ErrorOutcome recordError(Class<?> exceptionClass) {
        errors++;
        boolean warn = warnedClasses.add(exceptionClass);
        boolean off = !disabled && errors >= SearchTermsLimits.MAX_ERRORS;
        if (off) {
            disabled = true;
        }
        return new ErrorOutcome(warn, off);
    }

    /** Whether this kind of violation has not been warned about yet (and remembers that it now has been). */
    public boolean recordViolation(Violation violation) {
        return warnedViolations.add(violation);
    }

    public boolean isDisabled() {
        return disabled;
    }

    public int errors() {
        return errors;
    }
}
