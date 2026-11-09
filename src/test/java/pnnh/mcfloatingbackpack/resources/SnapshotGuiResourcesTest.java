package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.snapshot.SnapshotResultText;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;

/**
 * 031: the snapshot window's texts: both languages have the same keys, every key the window uses and every result code has a text,
 * the placeholders agree. Plain text checks.
 */
class SnapshotGuiResourcesTest {
    private static final Path SCREEN = Path.of("src/main/java/pnnh/mcfloatingbackpack/client/snapshot/SnapshotScreen.java");
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final String PREFIX = "gui.mcfloatingbackpack.snapshot.";

    private static Map<String, String> language(String file) throws IOException {
        String json = Files.readString(LANG.resolve(file), StandardCharsets.UTF_8);
        Map<String, String> entries = new LinkedHashMap<>();
        Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        while (matcher.find()) {
            entries.put(matcher.group(1), matcher.group(2));
        }
        return entries;
    }

    @Test
    void bothLanguagesHaveTheSameWindowKeys() throws IOException {
        Map<String, String> en = language("en_us.json");
        Map<String, String> zh = language("zh_cn.json");
        for (String key : en.keySet()) {
            if (key.startsWith(PREFIX)) {
                assertTrue(zh.containsKey(key), "zh_cn misses " + key);
                assertFalse(zh.get(key).isBlank(), key);
            }
        }
        for (String key : zh.keySet()) {
            if (key.startsWith(PREFIX)) {
                assertTrue(en.containsKey(key), "en_us misses " + key);
            }
        }
    }

    @Test
    void everyKeyTheWindowAsksForExists() throws IOException {
        String code = Files.readString(SCREEN, StandardCharsets.UTF_8);
        Map<String, String> en = language("en_us.json");
        Matcher matcher = Pattern.compile("K \\+ \"([a-z_.]+)\"").matcher(code);
        int found = 0;
        while (matcher.find()) {
            String key = matcher.group(1);
            found++;
            if (key.endsWith(".")) {
                continue; // a prefix: tier. is checked per tier below
            }
            assertTrue(en.containsKey(PREFIX + key), "no text for " + PREFIX + key);
        }
        assertTrue(found > 20, "found only " + found + " keys in the window");
    }

    @Test
    void everyTierHasAName() throws IOException {
        Map<String, String> en = language("en_us.json");
        Map<String, String> zh = language("zh_cn.json");
        for (BackpackTier tier : BackpackTier.values()) {
            String key = PREFIX + "tier." + tier.name().toLowerCase(Locale.ROOT);
            assertTrue(en.containsKey(key), key);
            assertTrue(zh.containsKey(key), key);
        }
    }

    @Test
    void everyResultCodeHasATextWithTheRightNumberOfPlaceholders() throws IOException {
        Map<String, String> en = language("en_us.json");
        Map<String, String> zh = language("zh_cn.json");
        Pattern placeholder = Pattern.compile("%s");
        for (ResultCode code : ResultCode.values()) {
            String key = SnapshotResultText.key(code);
            assertTrue(en.containsKey(key), "en_us misses " + key);
            assertTrue(zh.containsKey(key), "zh_cn misses " + key);
            assertEquals(SnapshotResultText.placeholders(code), placeholder.matcher(en.get(key)).results().count(), "en " + key);
            assertEquals(SnapshotResultText.placeholders(code), placeholder.matcher(zh.get(key)).results().count(), "zh " + key);
        }
    }

    @Test
    void placeholdersOfAllWindowTextsAgreeBetweenLanguages() throws IOException {
        Map<String, String> en = language("en_us.json");
        Map<String, String> zh = language("zh_cn.json");
        Pattern placeholder = Pattern.compile("%s");
        for (String key : en.keySet()) {
            if (key.startsWith(PREFIX) && zh.containsKey(key)) {
                assertEquals(
                        placeholder.matcher(en.get(key)).results().count(),
                        placeholder.matcher(zh.get(key)).results().count(),
                        "placeholders of " + key);
            }
        }
    }

    @Test
    void theKeyAndTheTitleBarButtonHaveTexts() throws IOException {
        for (String file : new String[] {"en_us.json", "zh_cn.json"}) {
            Map<String, String> lang = language(file);
            assertTrue(lang.containsKey("key.mcfloatingbackpack.snapshots"), file);
            assertTrue(lang.containsKey("panel.mcfloatingbackpack.snapshot.tooltip"), file);
            assertTrue(lang.containsKey("command.mcfloatingbackpack.gui.console"), file);
        }
    }
}
