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

/** 024: the auto pickup's recipe, model, icon and language entries. Plain text checks: the test class path has no JSON library. */
class AutoPickupResourcesTest {
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
    void recipeIsTheDesignedMidGameShape() throws IOException {
        String raw = read("data/mcfloatingbackpack/recipe/auto_pickup.json");
        assertTrue(Pattern.compile("\"pattern\"\\s*:\\s*\\[\\s*\"IEI\"\\s*,\\s*\"RHR\"\\s*,\\s*\"IDI\"\\s*\\]").matcher(raw).find());
        String recipe = raw.replaceAll("\\s+", "");
        assertTrue(recipe.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(recipe.contains("\"I\":{\"item\":\"minecraft:iron_ingot\"}"));
        assertTrue(recipe.contains("\"E\":{\"item\":\"minecraft:ender_pearl\"}"));
        assertTrue(recipe.contains("\"R\":{\"item\":\"minecraft:redstone\"}"));
        assertTrue(recipe.contains("\"H\":{\"item\":\"minecraft:hopper\"}"));
        assertTrue(recipe.contains("\"D\":{\"item\":\"minecraft:diamond\"}"));
        assertTrue(recipe.contains("\"id\":\"mcfloatingbackpack:auto_pickup\""));
        assertTrue(recipe.contains("\"count\":1"));
        assertTrue(!recipe.contains("sophisticated") && !recipe.contains("travelersbackpack"));
    }

    @Test
    void modelIsAFlatItem() throws IOException {
        String model = read("assets/mcfloatingbackpack/models/item/auto_pickup.json").replaceAll("\\s+", "");
        assertTrue(model.contains("\"parent\":\"minecraft:item/generated\""));
        assertTrue(model.contains("\"layer0\":\"mcfloatingbackpack:item/auto_pickup\""));
    }

    @Test
    void bothLanguagesHaveTheSameAutoPickupKeys() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");
        List<String> required = List.of(
                "item.mcfloatingbackpack.auto_pickup",
                "pickup.mcfloatingbackpack.title",
                "pickup.mcfloatingbackpack.status",
                "pickup.mcfloatingbackpack.none",
                "pickup.mcfloatingbackpack.full",
                "message.mcfloatingbackpack.limit.auto_pickup");
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
        assertTrue(en.contains("\"item.mcfloatingbackpack.auto_pickup\": \"Auto Pickup\""));
        assertTrue(zh.contains("\"item.mcfloatingbackpack.auto_pickup\": \"自动拾取\""));
    }

    @Test
    void theIconIsA16x16Png() throws IOException {
        byte[] png = Files.readAllBytes(ROOT.resolve("assets/mcfloatingbackpack/textures/item/auto_pickup.png"));
        assertEquals((byte) 0x89, png[0]);
        assertEquals('P', png[1]);
        assertEquals(16, ByteBuffer.wrap(png, 16, 4).getInt());
        assertEquals(16, ByteBuffer.wrap(png, 20, 4).getInt());
    }
}
