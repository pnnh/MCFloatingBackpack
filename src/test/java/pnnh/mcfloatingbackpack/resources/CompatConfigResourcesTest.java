package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 020: the config screen's and the hotkey message's language entries exist in both languages. */
class CompatConfigResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");

    private static Set<String> keys(String file) throws IOException {
        String json = Files.readString(LANG.resolve(file), StandardCharsets.UTF_8);
        Set<String> keys = new TreeSet<>();
        Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    @Test
    void theConfigScreenAndMessageKeysExistInBothLanguages() throws IOException {
        List<String> required = List.of(
                "message.mcfloatingbackpack.panel_off",
                "mcfloatingbackpack.configuration.title",
                "mcfloatingbackpack.configuration.compat",
                "mcfloatingbackpack.configuration.compat.tooltip",
                "config.mcfloatingbackpack.modLevels",
                "config.mcfloatingbackpack.modLevels.tooltip");
        for (String file : List.of("en_us.json", "zh_cn.json")) {
            Set<String> keys = keys(file);
            for (String key : required) {
                assertTrue(keys.contains(key), file + " missing " + key);
            }
        }
        assertEquals(keys("en_us.json"), keys("zh_cn.json"), "the two language files must have the same keys");
    }

    @Test
    void theConfigOptionPointsAtTheTranslationKeyThatExists() throws IOException {
        String source = Files.readString(
                Path.of("src/main/java/pnnh/mcfloatingbackpack/compat/CompatConfig.java"), StandardCharsets.UTF_8);
        assertTrue(source.contains("\"config.mcfloatingbackpack.modLevels\""));
        assertTrue(keys("en_us.json").contains("config.mcfloatingbackpack.modLevels"));
    }
}
