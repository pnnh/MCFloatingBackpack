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

import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;

/** 018: the tag manager's recipe, model and language entries. Plain text checks: the test class path has no JSON library. */
class TagManagerResourcesTest {
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
    void recipeIsTheDesignedThreeByThree() throws IOException {
        String recipe = read("data/mcfloatingbackpack/recipe/tag_manager.json").replaceAll("\\s+", "");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"pattern\":[\"SPS\",\"LBL\",\"PIP\"]"));
        assertTrue(recipe.contains("\"S\":{\"tag\":\"c:strings\"}"));
        assertTrue(recipe.contains("\"P\":{\"item\":\"minecraft:paper\"}"));
        assertTrue(recipe.contains("\"L\":{\"tag\":\"c:leathers\"}"));
        assertTrue(recipe.contains("\"B\":{\"item\":\"minecraft:book\"}"));
        assertTrue(recipe.contains("minecraft:ink_sac") && recipe.contains("minecraft:black_dye"));
        assertTrue(recipe.contains("\"id\":\"mcfloatingbackpack:tag_manager\""));
        assertTrue(recipe.contains("\"count\":1"));
        assertTrue(!recipe.contains("sophisticated") && !recipe.contains("travelersbackpack"));
    }

    @Test
    void modelIsAFlatItem() throws IOException {
        String model = read("assets/mcfloatingbackpack/models/item/tag_manager.json").replaceAll("\\s+", "");
        assertTrue(model.contains("\"parent\":\"minecraft:item/generated\""));
        assertTrue(model.contains("\"layer0\":\"mcfloatingbackpack:item/tag_manager\""));
    }

    @Test
    void bothLanguagesHaveTheSameTagKeys() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");

        List<String> required = new java.util.ArrayList<>(List.of(
                "item.mcfloatingbackpack.tag_manager",
                "tag.mcfloatingbackpack.none",
                "tag.mcfloatingbackpack.hint.select",
                "tag.mcfloatingbackpack.hint.select_tooltip",
                "tag.mcfloatingbackpack.status",
                "tag.mcfloatingbackpack.full",
                "tag.mcfloatingbackpack.full.batch",
                "tag.mcfloatingbackpack.add.placeholder"));
        for (String id : PresetTags.ids()) {
            required.add(PresetTags.langKey(id));
        }
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
    }

    @Test
    void theIconIsA16x16Png() throws IOException {
        byte[] png = Files.readAllBytes(ROOT.resolve("assets/mcfloatingbackpack/textures/item/tag_manager.png"));
        // PNG signature, then IHDR: width and height are the big-endian ints at bytes 16 and 20.
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        int width = java.nio.ByteBuffer.wrap(png, 16, 4).getInt();
        int height = java.nio.ByteBuffer.wrap(png, 20, 4).getInt();
        assertEquals(16, width);
        assertEquals(16, height);
    }
}
