package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 030: every message of the snapshot commands has a key in both languages, and its English fallback (what a server console, which
 * has no language file of this mod, prints) is the same as the English language file. Plain text checks.
 */
class SnapshotCommandResourcesTest {
    private static final Path SOURCE = Path.of("src/main/java/pnnh/mcfloatingbackpack/snapshot/SnapshotCommands.java");
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final String PREFIX = "command.mcfloatingbackpack.";

    private static Map<String, String> messagesInCode() throws IOException {
        String code = Files.readString(SOURCE, StandardCharsets.UTF_8);
        Matcher matcher = Pattern.compile("msg\\(\\s*\"([a-z.]+)\"\\s*,\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(code);
        Map<String, String> found = new LinkedHashMap<>();
        while (matcher.find()) {
            found.put(PREFIX + matcher.group(1), matcher.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
        }
        return found;
    }

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
    void everyMessageInTheCodeIsInBothLanguagesAndTheEnglishFallbackMatches() throws IOException {
        Map<String, String> code = messagesInCode();
        assertTrue(code.size() >= 20, "found only " + code.size() + " messages in the code");
        Map<String, String> en = language("en_us.json");
        Map<String, String> zh = language("zh_cn.json");
        for (Map.Entry<String, String> message : code.entrySet()) {
            assertTrue(en.containsKey(message.getKey()), "en_us misses " + message.getKey());
            assertTrue(zh.containsKey(message.getKey()), "zh_cn misses " + message.getKey());
            assertEquals(message.getValue(), en.get(message.getKey()), "the English fallback of " + message.getKey());
            assertFalse(zh.get(message.getKey()).isBlank());
        }
    }

    @Test
    void theLanguageFilesHaveNoCommandKeyTheCodeDoesNotUse() throws IOException {
        Map<String, String> code = messagesInCode();
        for (String file : new String[] {"en_us.json", "zh_cn.json"}) {
            for (String key : language(file).keySet()) {
                if (key.startsWith(PREFIX)) {
                    assertTrue(code.containsKey(key), file + " has an unused key " + key);
                }
            }
        }
    }

    @Test
    void thePlaceholdersOfBothLanguagesAgree() throws IOException {
        Map<String, String> en = language("en_us.json");
        Map<String, String> zh = language("zh_cn.json");
        Pattern placeholder = Pattern.compile("%s");
        for (String key : messagesInCode().keySet()) {
            long inEnglish = placeholder.matcher(en.get(key)).results().count();
            long inChinese = placeholder.matcher(zh.get(key)).results().count();
            assertEquals(inEnglish, inChinese, "placeholders of " + key);
        }
    }
}
