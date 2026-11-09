package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 023: the five backpack recipes. Plain text checks: the test class path has no JSON library and no Minecraft classes, so
 * what the smithing table really does with them is checked in a client session (see the feature's quickstart).
 */
class BackpackRecipesResourcesTest {
    private static final Path RECIPES = Path.of("src/main/resources/data/mcfloatingbackpack/recipe");

    /** Tier, template, material block, the backpack that is upgraded and the one that comes out. */
    private record Upgrade(String file, String template, String addition, String base, String result) {
    }

    private static final Upgrade[] UPGRADES = {
        new Upgrade("upgrade_iron_floating_backpack", "minecraft:sentry_armor_trim_smithing_template",
                "minecraft:iron_block", "mcfloatingbackpack:leather_floating_backpack", "mcfloatingbackpack:iron_floating_backpack"),
        new Upgrade("upgrade_gold_floating_backpack", "minecraft:dune_armor_trim_smithing_template",
                "minecraft:gold_block", "mcfloatingbackpack:iron_floating_backpack", "mcfloatingbackpack:gold_floating_backpack"),
        new Upgrade("upgrade_diamond_floating_backpack", "minecraft:eye_armor_trim_smithing_template",
                "minecraft:diamond_block", "mcfloatingbackpack:gold_floating_backpack", "mcfloatingbackpack:diamond_floating_backpack"),
        new Upgrade("upgrade_netherite_floating_backpack", "minecraft:netherite_upgrade_smithing_template",
                "minecraft:netherite_block", "mcfloatingbackpack:diamond_floating_backpack", "mcfloatingbackpack:netherite_floating_backpack"),
    };

    private static String raw(String name) throws IOException {
        return Files.readString(RECIPES.resolve(name + ".json"), StandardCharsets.UTF_8);
    }

    private static String compact(String name) throws IOException {
        return raw(name).replaceAll("\\s+", "");
    }

    @Test
    void everyUpgradeNeedsItsTemplateItsBlockAndTheLowerBackpack() throws IOException {
        for (Upgrade upgrade : UPGRADES) {
            String recipe = compact(upgrade.file());
            assertTrue(recipe.contains("\"type\":\"mcfloatingbackpack:backpack_upgrade\""), upgrade.file());
            assertTrue(recipe.contains("\"template\":{\"item\":\"" + upgrade.template() + "\"}"), upgrade.file() + " template");
            assertTrue(recipe.contains("\"base\":{\"item\":\"" + upgrade.base() + "\"}"), upgrade.file() + " base");
            assertTrue(recipe.contains("\"addition\":{\"item\":\"" + upgrade.addition() + "\"}"), upgrade.file() + " addition");
            assertTrue(recipe.contains("\"result\":{\"id\":\"" + upgrade.result() + "\",\"count\":1}"), upgrade.file() + " result");
            assertTrue(!recipe.contains("sophisticated") && !recipe.contains("travelersbackpack"), upgrade.file());
        }
    }

    @Test
    void theFourTemplatesAreAllDifferent() {
        Set<String> templates = new HashSet<>();
        for (Upgrade upgrade : UPGRADES) {
            templates.add(upgrade.template());
        }
        assertEquals(UPGRADES.length, templates.size());
    }

    @Test
    void everyMaterialIsABlock() {
        for (Upgrade upgrade : UPGRADES) {
            assertTrue(upgrade.addition().endsWith("_block"), upgrade.file());
        }
    }

    @Test
    void theNetheriteTierUsesTheVanillaUpgradeTemplateAndTheOthersUseArmorTrims() {
        assertEquals("minecraft:netherite_upgrade_smithing_template", UPGRADES[3].template());
        for (int i = 0; i < 3; i++) {
            assertTrue(UPGRADES[i].template().endsWith("_armor_trim_smithing_template"), UPGRADES[i].file());
        }
    }

    @Test
    void leatherBackpackNeedsTwoIronIngots() throws IOException {
        String raw = raw("leather_floating_backpack");
        // The pattern rows contain meaningful spaces, so it is checked before the whitespace is stripped.
        assertTrue(Pattern.compile("\"pattern\"\\s*:\\s*\\[\\s*\"LSL\"\\s*,\\s*\"LCL\"\\s*,\\s*\"ILI\"\\s*\\]").matcher(raw).find());
        String recipe = raw.replaceAll("\\s+", "");
        // 026: the same shaped recipe, as the mod's own type that stamps the result with an unregistered identity.
        assertTrue(recipe.contains("\"type\":\"mcfloatingbackpack:backpack_crafting\""));
        assertTrue(recipe.contains("\"L\":{\"item\":\"minecraft:leather\"}"));
        assertTrue(recipe.contains("\"S\":{\"item\":\"minecraft:string\"}"));
        assertTrue(recipe.contains("\"C\":{\"item\":\"minecraft:chest\"}"));
        assertTrue(recipe.contains("\"I\":{\"item\":\"minecraft:iron_ingot\"}"));
        assertTrue(recipe.contains("\"id\":\"mcfloatingbackpack:leather_floating_backpack\""));
        assertTrue(recipe.contains("\"count\":1"));
    }
}
