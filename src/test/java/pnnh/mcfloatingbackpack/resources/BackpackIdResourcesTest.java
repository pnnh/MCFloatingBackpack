package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

/** 026: the backpack id's language entries and recipe type. Plain text checks: the test class path has no JSON library. */
class BackpackIdResourcesTest {
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
    void bothLanguagesHaveTheSameBackpackIdKeys() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");
        List<String> required = List.of(
                "tooltip.mcfloatingbackpack.id",
                "tooltip.mcfloatingbackpack.id.none",
                "panel.mcfloatingbackpack.id.tooltip",
                "message.mcfloatingbackpack.id.tombstone");
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
        assertTrue(zh.contains("\"tooltip.mcfloatingbackpack.id\": \"编号：%s\""));
        assertTrue(en.contains("\"tooltip.mcfloatingbackpack.id\": \"ID: %s\""));
        // nothing is limited any more: the messages and the setting of the earlier draft are gone
        for (String gone : List.of("id.limit", "id.no_id", "id.duplicate", "id.readopt_changed", "id.pickup_blocked", "id.claim_limit", "maxBackpacksPerPlayer")) {
            assertFalse(en.contains(gone), "en_us still has " + gone);
            assertFalse(zh.contains(gone), "zh_cn still has " + gone);
        }
    }

    @Test
    void theLeatherBackpackRecipeUsesTheStampingCraftingType() throws IOException {
        String raw = read("data/mcfloatingbackpack/recipe/leather_floating_backpack.json");
        String recipe = raw.replaceAll("\\s+", "");
        assertTrue(recipe.contains("\"type\":\"mcfloatingbackpack:backpack_crafting\""));
        assertTrue(Pattern.compile("\"pattern\"\\s*:\\s*\\[\\s*\"LSL\"\\s*,\\s*\"LCL\"\\s*,\\s*\"ILI\"\\s*\\]").matcher(raw).find());
        assertTrue(recipe.contains("\"L\":{\"item\":\"minecraft:leather\"}"));
        assertTrue(recipe.contains("\"C\":{\"item\":\"minecraft:chest\"}"));
        assertTrue(recipe.contains("\"S\":{\"item\":\"minecraft:string\"}"));
        assertTrue(recipe.contains("\"I\":{\"item\":\"minecraft:iron_ingot\"}"));
        assertTrue(recipe.contains("\"id\":\"mcfloatingbackpack:leather_floating_backpack\""));
        assertTrue(recipe.contains("\"count\":1"));
        assertTrue(recipe.contains("\"category\":\"equipment\""));
    }
}
