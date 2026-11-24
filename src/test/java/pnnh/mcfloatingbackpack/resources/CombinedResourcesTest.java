package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** 065: what the five combined backpacks need to exist: models, icons, names, recipes, and the slot tags that let them be worn. */
class CombinedResourcesTest {
    private static final String[] TIERS = {"leather", "iron", "gold", "diamond", "netherite"};
    private static final Path ASSETS = Path.of("src/main/resources/assets/mcfloatingbackpack");
    private static final Path DATA = Path.of("src/main/resources/data");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(read(path)).getAsJsonObject();
    }

    @Test
    void everyTierHasAModelAnIconAndAName() throws IOException {
        for (String tier : TIERS) {
            String id = tier + "_combined_backpack";
            JsonObject model = json(ASSETS.resolve("models/item/" + id + ".json"));
            assertEquals("minecraft:item/generated", model.get("parent").getAsString(), id);
            assertEquals(
                    "mcfloatingbackpack:item/" + id, model.getAsJsonObject("textures").get("layer0").getAsString(), id);
            assertTrue(Files.exists(ASSETS.resolve("textures/item/" + id + ".png")), id + " icon");
            for (String lang : new String[] {"en_us", "zh_cn"}) {
                assertTrue(read(ASSETS.resolve("lang/" + lang + ".json")).contains("\"item.mcfloatingbackpack." + id + "\""), lang + " " + id);
            }
        }
    }

    @Test
    void theCraftingRecipesAreTheCombinedTypeAndTakeTheTiersOwnBackpackInTheMiddle() throws IOException {
        for (String tier : TIERS) {
            JsonObject recipe = json(DATA.resolve("mcfloatingbackpack/recipe/combined_" + tier + "_backpack.json"));
            assertEquals("mcfloatingbackpack:combined_crafting", recipe.get("type").getAsString(), tier);
            assertEquals(
                    "mcfloatingbackpack:" + tier + "_combined_backpack",
                    recipe.getAsJsonObject("result").get("id").getAsString(),
                    tier);
            assertEquals(
                    "mcfloatingbackpack:" + tier + "_floating_backpack",
                    recipe.getAsJsonObject("key").getAsJsonObject("B").get("item").getAsString(),
                    tier);
            assertEquals("MBM", recipe.getAsJsonArray("pattern").get(1).getAsString(), tier);
        }
    }

    @Test
    void theUpgradesGoFromOneCombinedTierToTheNextInTheSmithingTable() throws IOException {
        for (int i = 1; i < TIERS.length; i++) {
            JsonObject recipe = json(DATA.resolve("mcfloatingbackpack/recipe/upgrade_" + TIERS[i] + "_combined_backpack.json"));
            assertEquals("minecraft:smithing_transform", recipe.get("type").getAsString(), TIERS[i]);
            assertEquals(
                    "mcfloatingbackpack:" + TIERS[i - 1] + "_combined_backpack",
                    recipe.getAsJsonObject("base").get("item").getAsString(),
                    TIERS[i]);
            assertEquals(
                    "mcfloatingbackpack:" + TIERS[i] + "_combined_backpack",
                    recipe.getAsJsonObject("result").get("id").getAsString(),
                    TIERS[i]);
        }
    }

    @Test
    void theDedicatedSlotsTakeTheCombinedBackpacksButTheBackAndCharmSlotsDoNot() throws IOException {
        for (String loader : new String[] {"curios", "accessories"}) {
            String dedicated = read(DATA.resolve(loader + "/tags/item/floating_backpack.json"));
            String back = read(DATA.resolve(loader + "/tags/item/back.json"));
            String charm = read(DATA.resolve(loader + "/tags/item/charm.json"));
            for (String tier : TIERS) {
                String id = "mcfloatingbackpack:" + tier + "_combined_backpack";
                assertTrue(dedicated.contains(id), loader + " dedicated " + tier);
                assertFalse(back.contains("combined_backpack"), loader + " back");
                assertFalse(charm.contains("combined_backpack"), loader + " charm");
            }
        }
    }

    @Test
    void theCombinedRecipeTypeIsRegisteredAndOnlyTakesEmptyBackpacks() throws IOException {
        String recipes = read(Path.of("src/main/java/pnnh/mcfloatingbackpack/inventory/recipe/ModRecipes.java"));
        assertTrue(recipes.contains("\"combined_crafting\""));
        String recipe = read(Path.of("src/main/java/pnnh/mcfloatingbackpack/inventory/recipe/CombinedCraftingRecipe.java"));
        assertTrue(recipe.contains("isEmptyBackpack(stack)") && recipe.contains("return false;"));
    }
}
