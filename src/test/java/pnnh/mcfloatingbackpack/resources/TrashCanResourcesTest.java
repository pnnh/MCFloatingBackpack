package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 021: the trash can's recipe, model, icon and language entries. Plain text checks: the test class path has no JSON library. */
class TrashCanResourcesTest {
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
    void recipeIsTheDesignedCross() throws IOException {
        String raw = read("data/mcfloatingbackpack/recipe/trash_can.json");
        // The pattern rows contain meaningful spaces, so it is checked before the whitespace is stripped.
        assertTrue(Pattern.compile("\"pattern\"\\s*:\\s*\\[\\s*\" I \"\\s*,\\s*\"ICI\"\\s*,\\s*\" I \"\\s*\\]").matcher(raw).find());
        String recipe = raw.replaceAll("\\s+", "");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"I\":{\"item\":\"minecraft:iron_ingot\"}"));
        assertTrue(recipe.contains("\"C\":{\"item\":\"minecraft:cauldron\"}"));
        assertTrue(recipe.contains("\"id\":\"mcfloatingbackpack:trash_can\""));
        assertTrue(recipe.contains("\"count\":1"));
        assertTrue(!recipe.contains("sophisticated") && !recipe.contains("travelersbackpack"));
    }

    @Test
    void modelIsAFlatItem() throws IOException {
        String model = read("assets/mcfloatingbackpack/models/item/trash_can.json").replaceAll("\\s+", "");
        assertTrue(model.contains("\"parent\":\"minecraft:item/generated\""));
        assertTrue(model.contains("\"layer0\":\"mcfloatingbackpack:item/trash_can\""));
    }

    @Test
    void bothLanguagesHaveTheSameTrashKeys() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");
        List<String> required = List.of(
                "item.mcfloatingbackpack.trash_can",
                "trash.mcfloatingbackpack.title",
                "message.mcfloatingbackpack.limit.trash_can",
                "message.mcfloatingbackpack.limit.tag_manager",
                "message.mcfloatingbackpack.trash.too_large");
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
        assertTrue(en.contains("\"item.mcfloatingbackpack.trash_can\": \"Trash Can\""));
        assertTrue(zh.contains("\"item.mcfloatingbackpack.trash_can\": \"垃圾桶\""));
    }

    @Test
    void theIconIsA16x16Png() throws IOException {
        byte[] png = Files.readAllBytes(ROOT.resolve("assets/mcfloatingbackpack/textures/item/trash_can.png"));
        // PNG signature, then IHDR: width and height are the big-endian ints at bytes 16 and 20.
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals(16, ByteBuffer.wrap(png, 16, 4).getInt());
        assertEquals(16, ByteBuffer.wrap(png, 20, 4).getInt());
    }
}
