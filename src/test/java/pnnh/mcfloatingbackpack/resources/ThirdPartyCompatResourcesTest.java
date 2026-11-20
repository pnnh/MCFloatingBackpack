package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 058: the language keys of the third-party notices, and the server config's comments. Plain text checks. */
class ThirdPartyCompatResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static final List<String> KEYS = List.of(
            "message.mcfloatingbackpack.compat.untested", "message.mcfloatingbackpack.compat.state.off",
            "message.mcfloatingbackpack.compat.state.forced", "message.mcfloatingbackpack.compat.verdict.below",
            "message.mcfloatingbackpack.compat.verdict.above", "message.mcfloatingbackpack.compat.verdict.gap",
            "message.mcfloatingbackpack.compat.verdict.unknown", "message.mcfloatingbackpack.compat.failed");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String value(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        assertTrue(m.find(), key);
        return m.group(1);
    }

    /** How many different arguments the text uses, by position ({@code %1$s}) or in order ({@code %s}). */
    private static int arguments(String text) {
        TreeSet<Integer> positions = new TreeSet<>();
        int next = 1;
        Matcher m = Pattern.compile("%(?:(\\d+)\\$)?s").matcher(text);
        while (m.find()) {
            positions.add(m.group(1) != null ? Integer.parseInt(m.group(1)) : next++);
        }
        return positions.isEmpty() ? 0 : positions.last();
    }

    @Test
    void bothLanguagesHaveEveryKey() throws IOException {
        String en = read(LANG.resolve("en_us.json"));
        String zh = read(LANG.resolve("zh_cn.json"));
        for (String key : KEYS) {
            assertTrue(en.contains("\"" + key + "\""), "en_us " + key);
            assertTrue(zh.contains("\"" + key + "\""), "zh_cn " + key);
        }
    }

    @Test
    void bothLanguagesUseTheSameNumberOfArguments() throws IOException {
        String en = read(LANG.resolve("en_us.json"));
        String zh = read(LANG.resolve("zh_cn.json"));
        for (String key : KEYS) {
            assertEquals(arguments(value(en, key)), arguments(value(zh, key)), key);
        }
    }

    @Test
    void theNoticesPassAsManyArgumentsAsTheTextsTake() throws IOException {
        String en = read(LANG.resolve("en_us.json"));
        assertEquals(6, arguments(value(en, "message.mcfloatingbackpack.compat.untested")));
        assertEquals(2, arguments(value(en, "message.mcfloatingbackpack.compat.failed")));
        String notices = read(SRC.resolve("compat/ThirdPartyNotices.java"));
        // untested: feature, state, subject, installed, verdict, ranges
        assertTrue(notices.contains("finding.entry().displayName(),") && notices.contains("finding.subjectName(),")
                && notices.contains("finding.installedVersion(),") && notices.contains("CompatLogText.ranges(finding.ranges())"));
        assertTrue(notices.contains("\"message.mcfloatingbackpack.compat.failed\", name, cause.getClass().getSimpleName()"));
    }

    @Test
    void everyKeyOfTheVerdictAndStateSwitchesExists() throws IOException {
        String notices = read(SRC.resolve("compat/ThirdPartyNotices.java"));
        for (String key : List.of("compat.state.forced", "compat.state.off")) {
            assertTrue(notices.contains("message.mcfloatingbackpack." + key), key);
        }
        for (String verdict : List.of("below", "above", "gap", "unknown")) {
            assertTrue(notices.contains("\"" + verdict + "\"") || notices.contains("-> \"" + verdict + "\""), verdict);
            assertTrue(read(LANG.resolve("en_us.json")).contains("message.mcfloatingbackpack.compat.verdict." + verdict), verdict);
        }
    }

    @Test
    void theForcedTextsAdviseABackupInBothLanguages() throws IOException {
        assertTrue(value(read(LANG.resolve("en_us.json")), "message.mcfloatingbackpack.compat.state.forced").contains("back up"));
        assertTrue(value(read(LANG.resolve("zh_cn.json")), "message.mcfloatingbackpack.compat.state.forced").contains("备份"));
    }

    @Test
    void theConfigCommentWarnsAboutTheRiskAndAdvisesABackup() throws IOException {
        String config = read(SRC.resolve("compat/ThirdPartyConfig.java"));
        assertTrue(config.contains("WARNING"));
        assertTrue(config.contains("crash of the server"));
        assertTrue(config.contains("backup of the world"));
        assertTrue(config.contains("set it back to false"));
        assertTrue(config.contains("reopened"));
        assertTrue(config.contains(".define(\"allowUntestedVersions\", false)"), "off by default");
        assertTrue(config.contains("builder.push(\"compat\")"));
        assertTrue(read(SRC.resolve("diag/DiagConfig.java")).contains("ThirdPartyConfig.define(BUILDER)"));
    }

    @Test
    void aForcedRowIsSaidToBeForcedInTheLog() throws IOException {
        assertTrue(read(SRC.resolve("compat/CompatLogText.java")).contains("FORCED ON by allowUntestedVersions"));
    }
}
