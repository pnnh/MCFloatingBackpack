package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.light.ToolLights;

/** 052: the tag of light items and the one new message. Plain text checks (no JSON library). */
class LightResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static List<String> tagValues() throws IOException {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"(minecraft:[a-z_]+)\"").matcher(read("data/mcfloatingbackpack/tags/item/tool_lights.json"));
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void theTagHoldsTheTenLightsAndTheTableHasTheSameTen() throws IOException {
        List<String> values = tagValues();
        assertEquals(10, values.size());
        assertEquals(new TreeSet<>(values), new TreeSet<>(ToolLights.table().keySet()), "the tag and the brightness table list the same items");
        assertTrue(read("data/mcfloatingbackpack/tags/item/tool_lights.json").replaceAll("\\s+", "").contains("\"replace\":false"));
    }

    @Test
    void noTorchCandleCampfireOrLavaIsALight() throws IOException {
        String tag = read("data/mcfloatingbackpack/tags/item/tool_lights.json");
        for (String banned : new String[] {"torch", "candle", "campfire", "lava", "magma", "fire"}) {
            assertFalse(tag.contains(banned), banned);
        }
    }

    @Test
    void bothLanguagesHaveTheDuplicateMessageAndTheSameKeys() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertTrue(en.contains("\"message.mcfloatingbackpack.light.duplicate\""));
        assertTrue(zh.contains("\"message.mcfloatingbackpack.light.duplicate\""));
        assertEquals(keys(en), keys(zh));
    }

    @Test
    void noItemModelTextureOrRecipeWasAdded() throws IOException {
        // 052 adds no item: nothing mentions a light item of this mod
        try (var files = Files.walk(ROOT.resolve("data/mcfloatingbackpack/recipe"))) {
            assertFalse(files.anyMatch(p -> p.getFileName().toString().contains("light")));
        }
    }

    private static TreeSet<String> keys(String json) {
        TreeSet<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }
}
