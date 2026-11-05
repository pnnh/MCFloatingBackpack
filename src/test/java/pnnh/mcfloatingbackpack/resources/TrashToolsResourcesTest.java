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

/** 022: the language entries of the dump and delete tools, the confirmation dialog and the protection hints. */
class TrashToolsResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");

    private static String read(String file) throws IOException {
        return Files.readString(ROOT.resolve(file), StandardCharsets.UTF_8);
    }

    private static Set<String> keys(String json) {
        Set<String> keys = new TreeSet<>();
        Matcher matcher = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (matcher.find()) {
            keys.add(matcher.group(1));
        }
        return keys;
    }

    @Test
    void bothLanguagesHaveTheSameKeysIncludingTheNewOnes() throws IOException {
        String en = read("en_us.json");
        String zh = read("zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");
        List<String> required = List.of(
                "key.mcfloatingbackpack.delete_to_trash",
                "trash.mcfloatingbackpack.icon.dump",
                "trash.mcfloatingbackpack.dump.tooltip",
                "trash.mcfloatingbackpack.icon.delete",
                "trash.mcfloatingbackpack.delete.tooltip.disabled",
                "trash.mcfloatingbackpack.delete.tooltip.enabled",
                "trash.mcfloatingbackpack.confirm.text",
                "trash.mcfloatingbackpack.confirm.yes",
                "trash.mcfloatingbackpack.confirm.no",
                "message.mcfloatingbackpack.trash.nothing_to_dump",
                "message.mcfloatingbackpack.trash.changed",
                "tag.mcfloatingbackpack.protected");
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
    }

    @Test
    void theEnglishTitleIsShortSoTheIconsFit() throws IOException {
        assertTrue(read("en_us.json").contains("\"trash.mcfloatingbackpack.title\": \"Trash\""));
    }

    @Test
    void theConfirmTextTakesTheNumberOfGroups() throws IOException {
        assertTrue(Pattern.compile("\"trash\\.mcfloatingbackpack\\.confirm\\.text\"\\s*:\\s*\"[^\"]*%s[^\"]*\"")
                .matcher(read("zh_cn.json")).find());
        assertTrue(Pattern.compile("\"trash\\.mcfloatingbackpack\\.confirm\\.text\"\\s*:\\s*\"[^\"]*%s[^\"]*\"")
                .matcher(read("en_us.json")).find());
    }
}
