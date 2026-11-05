package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.CompatPolicy.Problem;
import pnnh.mcfloatingbackpack.compat.CompatPolicy.Warning;
import pnnh.mcfloatingbackpack.compat.ConfigNotices.Notice;

class ConfigNoticesTest {
    @BeforeEach
    @AfterEach
    void clear() {
        ConfigNotices.clear();
    }

    @Test
    void aWarningBecomesAChatNoticeWithItsEntry() {
        ConfigNotices.warning(new Warning(Problem.UNKNOWN_LEVEL, "x=HALF"));
        assertTrue(ConfigNotices.hasPending());
        assertEquals(
                List.of(new Notice("message.mcfloatingbackpack.config.unknown_level", "x=HALF")), ConfigNotices.drain());
        assertFalse(ConfigNotices.hasPending());
    }

    @Test
    void everyKindOfProblemHasItsOwnKey() {
        for (Problem problem : Problem.values()) {
            ConfigNotices.warning(new Warning(problem, "e"));
        }
        List<String> keys = ConfigNotices.drain().stream().map(Notice::key).toList();
        assertEquals(
                List.of(
                        "message.mcfloatingbackpack.config.malformed",
                        "message.mcfloatingbackpack.config.vanilla",
                        "message.mcfloatingbackpack.config.unknown_level",
                        "message.mcfloatingbackpack.config.duplicate"),
                keys);
    }

    @Test
    void identicalNoticesAreShownOnce() {
        ConfigNotices.fileProblem("a.toml.bak");
        ConfigNotices.fileProblem("a.toml.bak");
        assertEquals(1, ConfigNotices.drain().size());
    }

    @Test
    void manyNoticesAreCutAndSummedUp() {
        for (int i = 0; i < 8; i++) {
            ConfigNotices.warning(new Warning(Problem.MALFORMED, "bad" + i));
        }
        List<Notice> shown = ConfigNotices.drain();
        assertEquals(ConfigNotices.MAX_SHOWN + 1, shown.size());
        Notice last = shown.get(shown.size() - 1);
        assertEquals("message.mcfloatingbackpack.config.more", last.key());
        assertEquals("3", last.arg());
    }

    @Test
    void onlyFreshBackupsOfThisConfigCount() {
        long now = 1_000_000L;
        Map<String, Long> files = Map.of(
                "mcfloatingbackpack-client.toml", now,
                "mcfloatingbackpack-client-1.toml.bak", now - 2_000,
                "mcfloatingbackpack-client-2.toml.bak", now - 60_000,
                "othermod-client.toml.bak", now - 1_000,
                "mcfloatingbackpack-panel.properties", now);
        assertEquals(List.of("mcfloatingbackpack-client-1.toml.bak"), ConfigNotices.freshBackups(files, now, 10_000));
        assertEquals(List.of(), ConfigNotices.freshBackups(Map.of(), now, 10_000));
    }
}
