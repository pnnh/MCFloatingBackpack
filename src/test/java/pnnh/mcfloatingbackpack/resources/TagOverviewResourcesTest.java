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

/** 025: the tag overview's language entries. Plain text checks: the test class path has no JSON library. */
class TagOverviewResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
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
    void bothLanguagesHaveTheSameOverviewKeys() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");
        List<String> required = List.of(
                "overview.mcfloatingbackpack.icon.open",
                "overview.mcfloatingbackpack.icon.close",
                "overview.mcfloatingbackpack.title",
                "overview.mcfloatingbackpack.empty",
                "overview.mcfloatingbackpack.filter.placeholder",
                "overview.mcfloatingbackpack.filter.none",
                "overview.mcfloatingbackpack.filter.clear",
                "overview.mcfloatingbackpack.detail.back",
                "overview.mcfloatingbackpack.detail.missing",
                "overview.mcfloatingbackpack.clear",
                "overview.mcfloatingbackpack.clear.confirm",
                "overview.mcfloatingbackpack.clear.tooltip");
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
        assertTrue(zh.contains("\"overview.mcfloatingbackpack.icon.open\": \"标签总览\""));
        assertTrue(en.contains("\"overview.mcfloatingbackpack.icon.open\": \"Tag overview\""));
    }
}
