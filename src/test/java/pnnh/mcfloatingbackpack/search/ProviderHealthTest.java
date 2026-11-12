package pnnh.mcfloatingbackpack.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProviderHealthTest {
    @Test
    void theFirstExceptionOfAClassIsWarnedAboutAndTheNextOnesAreNot() {
        ProviderHealth health = new ProviderHealth();
        assertTrue(health.recordError(IllegalStateException.class).warnThisClass());
        assertFalse(health.recordError(IllegalStateException.class).warnThisClass());
        assertFalse(health.recordError(IllegalStateException.class).warnThisClass());
    }

    @Test
    void eachNewExceptionClassIsWarnedAboutOnce() {
        ProviderHealth health = new ProviderHealth();
        assertTrue(health.recordError(IllegalStateException.class).warnThisClass());
        assertTrue(health.recordError(NullPointerException.class).warnThisClass());
        assertFalse(health.recordError(NullPointerException.class).warnThisClass());
    }

    @Test
    void itIsSwitchedOffAtTheLimitAndSaidSoExactlyOnce() {
        ProviderHealth health = new ProviderHealth();
        int switchedOffNotices = 0;
        for (int i = 1; i <= SearchTermsLimits.MAX_ERRORS + 5; i++) {
            ProviderHealth.ErrorOutcome outcome = health.recordError(RuntimeException.class);
            if (outcome.switchedOffNow()) {
                switchedOffNotices++;
                assertEquals(SearchTermsLimits.MAX_ERRORS, i);
            }
            assertEquals(i >= SearchTermsLimits.MAX_ERRORS, health.isDisabled());
        }
        assertEquals(1, switchedOffNotices);
    }

    @Test
    void oneLessThanTheLimitIsStillOn() {
        ProviderHealth health = new ProviderHealth();
        for (int i = 0; i < SearchTermsLimits.MAX_ERRORS - 1; i++) {
            health.recordError(RuntimeException.class);
        }
        assertFalse(health.isDisabled());
    }

    @Test
    void aViolationIsWarnedAboutOncePerKindAndNeverSwitchesOff() {
        ProviderHealth health = new ProviderHealth();
        assertTrue(health.recordViolation(ProviderHealth.Violation.TOO_MANY));
        assertFalse(health.recordViolation(ProviderHealth.Violation.TOO_MANY));
        assertTrue(health.recordViolation(ProviderHealth.Violation.TOO_LONG));
        for (int i = 0; i < 100; i++) {
            health.recordViolation(ProviderHealth.Violation.TOO_MANY);
        }
        assertFalse(health.isDisabled());
        assertEquals(0, health.errors());
    }
}
