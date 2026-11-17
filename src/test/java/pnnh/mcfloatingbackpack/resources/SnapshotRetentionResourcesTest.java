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

/** 057: the language keys of the snapshot notices and window, and the config's comments. Plain text checks. */
class SnapshotRetentionResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static TreeSet<String> keys(String json) {
        TreeSet<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static final List<String> NEW_KEYS = List.of(
            "message.mcfloatingbackpack.snapshot.upgraded", "message.mcfloatingbackpack.snapshot.downgraded",
            "message.mcfloatingbackpack.snapshot.changed", "message.mcfloatingbackpack.snapshot.disabled", "message.mcfloatingbackpack.snapshot.lost",
            "gui.mcfloatingbackpack.snapshot.button.kind", "gui.mcfloatingbackpack.snapshot.kind.all", "gui.mcfloatingbackpack.snapshot.kind.timed",
            "gui.mcfloatingbackpack.snapshot.kind.change", "gui.mcfloatingbackpack.snapshot.kind.upgrade",
            "gui.mcfloatingbackpack.snapshot.kind.mark.timed", "gui.mcfloatingbackpack.snapshot.kind.mark.change",
            "gui.mcfloatingbackpack.snapshot.kind.mark.upgrade", "gui.mcfloatingbackpack.snapshot.version.to",
            "gui.mcfloatingbackpack.snapshot.version.row_plain", "gui.mcfloatingbackpack.snapshot.version.none_of_kind",
            "gui.mcfloatingbackpack.snapshot.result.restored_lost", "command.mcfloatingbackpack.restore.lost");

    private static long placeholders(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        assertTrue(m.find(), key);
        return Pattern.compile("%s").matcher(m.group(1)).results().count();
    }

    @Test
    void bothLanguagesHaveEveryNewKeyAndTheSameKeys() throws IOException {
        String en = read(LANG.resolve("en_us.json"));
        String zh = read(LANG.resolve("zh_cn.json"));
        assertEquals(keys(en), keys(zh));
        for (String key : NEW_KEYS) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
    }

    @Test
    void theNoticesTakeTheArgumentsTheCodeGivesThem() throws IOException {
        String en = read(LANG.resolve("en_us.json"));
        String zh = read(LANG.resolve("zh_cn.json"));
        for (String json : new String[] {en, zh}) {
            for (String key : List.of("upgraded", "downgraded", "changed")) {
                assertEquals(3, placeholders(json, "message.mcfloatingbackpack.snapshot." + key), key);
            }
            assertEquals(2, placeholders(json, "message.mcfloatingbackpack.snapshot.disabled"));
            assertEquals(1, placeholders(json, "message.mcfloatingbackpack.snapshot.lost"));
            assertEquals(3, placeholders(json, "gui.mcfloatingbackpack.snapshot.result.restored_lost"));
            assertEquals(1, placeholders(json, "gui.mcfloatingbackpack.snapshot.button.kind"));
            assertEquals(2, placeholders(json, "gui.mcfloatingbackpack.snapshot.version.row_plain"));
            assertEquals(1, placeholders(json, "gui.mcfloatingbackpack.snapshot.version.to"));
            assertEquals(5, placeholders(json, "command.mcfloatingbackpack.info.version"));
        }
    }

    @Test
    void theConfigSaysWhatTheRisksOfSwitchingItOffAre() throws IOException {
        String config = read(SRC.resolve("snapshot/SnapshotConfig.java"));
        assertTrue(config.contains("builder.push(\"snapshots\")"));
        assertTrue(config.contains(".define(\"enabled\", true)"));
        assertTrue(config.contains("\"maxTotalMb\""));
        assertTrue(config.contains("WARNING"));
        assertTrue(config.contains("only backup"));
        assertTrue(config.contains("Allowed: 128 - 4096"));
        assertTrue(read(SRC.resolve("diag/DiagConfig.java")).contains("SnapshotConfig.define(BUILDER)"));
    }
}
