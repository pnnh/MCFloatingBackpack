package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;

/**
 * 038: the five universal extensions' models, textures, recipes and language keys, and the wiring that no pure test can see (the
 * item classes need Minecraft). Plain text and pixel checks (no JSON library).
 */
class UniversalExtensionResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final List<String> TIERS = List.of("leather", "iron", "gold", "diamond", "netherite");

    /** The five core materials of each recipe: top, left, centre, right, bottom (contracts/items.md; the recipes are a draft). */
    private static final Map<String, List<String>> CORE = Map.of(
            "leather", List.of("minecraft:chest", "minecraft:leather", "minecraft:hopper", "minecraft:leather", "minecraft:string"),
            "iron", List.of("minecraft:chest", "minecraft:iron_ingot", "minecraft:hopper", "minecraft:iron_ingot", "minecraft:iron_ingot"),
            "gold", List.of("minecraft:chest", "minecraft:gold_ingot", "minecraft:hopper", "minecraft:gold_ingot", "minecraft:gold_ingot"),
            "diamond", List.of("minecraft:diamond", "minecraft:chest", "minecraft:hopper", "minecraft:chest", "minecraft:diamond"),
            "netherite",
            List.of("minecraft:netherite_ingot", "minecraft:ender_chest", "minecraft:shulker_shell", "minecraft:ender_chest", "minecraft:diamond"));

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String compact(String relative) throws IOException {
        return read(relative).replaceAll("\\s+", "");
    }

    private static Set<String> keys(String json) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static BufferedImage texture(String name) throws IOException {
        return ImageIO.read(ROOT.resolve("assets/mcfloatingbackpack/textures/item/" + name + ".png").toFile());
    }

    private static List<Integer> pixels(BufferedImage image) {
        List<Integer> out = new ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.add(image.getRGB(x, y));
            }
        }
        return out;
    }

    @Test
    void everyTierHasAFlatModelPointingAtItsTexture() throws IOException {
        for (String tier : TIERS) {
            String json = compact("assets/mcfloatingbackpack/models/item/" + tier + "_general_extension.json");
            assertTrue(json.contains("\"parent\":\"minecraft:item/generated\""), tier);
            assertTrue(json.contains("\"layer0\":\"mcfloatingbackpack:item/" + tier + "_general_extension\""), tier);
        }
    }

    @Test
    void everyTextureIs16x16RgbaWithRealContent() throws IOException {
        for (String tier : TIERS) {
            byte[] png = Files.readAllBytes(ROOT.resolve("assets/mcfloatingbackpack/textures/item/" + tier + "_general_extension.png"));
            assertEquals(16, java.nio.ByteBuffer.wrap(png, 16, 4).getInt(), tier);
            assertEquals(16, java.nio.ByteBuffer.wrap(png, 20, 4).getInt(), tier);
            assertEquals(6, png[25], tier + " must be RGBA");
            long opaque = pixels(texture(tier + "_general_extension")).stream().filter(argb -> (argb >>> 24) == 255).count();
            assertTrue(opaque >= 120, tier + " has only " + opaque + " opaque pixels");
        }
    }

    @Test
    void theFiveTexturesAreDifferentFromEachOtherAndFromEveryOlderIcon() throws IOException {
        List<List<Integer>> all = new ArrayList<>();
        for (String tier : TIERS) {
            all.add(pixels(texture(tier + "_general_extension")));
        }
        for (String category : ExtensionCategories.IDS) {
            all.add(pixels(texture(category + "_extension")));
        }
        for (String older : List.of("auto_pickup", "trash_can", "tag_manager")) {
            all.add(pixels(texture(older)));
        }
        assertEquals(all.size(), new HashSet<>(all).size(), "two icons are the same picture");
    }

    private record Recipe(String pattern, Map<String, String> key) {
    }

    private static Recipe recipe(String name) throws IOException {
        String json = compact("data/mcfloatingbackpack/recipe/" + name + ".json");
        assertTrue(json.contains("\"type\":\"minecraft:crafting_shaped\""), name);
        assertTrue(json.contains("\"result\":{\"count\":1,\"id\":\"mcfloatingbackpack:" + name + "\"}"), name);
        Matcher pattern = Pattern.compile("\"pattern\":\\[\"([^\"]{3})\",\"([^\"]{3})\",\"([^\"]{3})\"\\]").matcher(json);
        assertTrue(pattern.find(), name + " pattern must be three rows of three");
        String rows = pattern.group(1) + "/" + pattern.group(2) + "/" + pattern.group(3);
        Map<String, String> key = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("\"([A-Z])\":\\{\"(item|tag)\":\"([^\"]+)\"\\}").matcher(json);
        while (entry.find()) {
            key.put(entry.group(1), ("tag".equals(entry.group(2)) ? "#" : "") + entry.group(3));
        }
        return new Recipe(rows, key);
    }

    private static String cellsOf(Recipe recipe) {
        StringBuilder cells = new StringBuilder();
        for (String row : recipe.pattern().split("/")) {
            for (char c : row.toCharArray()) {
                cells.append(recipe.key().get(String.valueOf(c))).append(';');
            }
        }
        return cells.toString();
    }

    @Test
    void everyRecipeIsTheIronFrameWithTheFiveCoreMaterialsOfItsTier() throws IOException {
        for (String tier : TIERS) {
            Recipe recipe = recipe(tier + "_general_extension");
            String[] rows = recipe.pattern().split("/");
            for (String corner : new String[] {rows[0].substring(0, 1), rows[0].substring(2), rows[2].substring(0, 1), rows[2].substring(2)}) {
                assertEquals("minecraft:iron_ingot", recipe.key().get(corner), tier + " corner");
            }
            List<String> cells = List.of(
                    recipe.key().get(rows[0].substring(1, 2)),
                    recipe.key().get(rows[1].substring(0, 1)),
                    recipe.key().get(rows[1].substring(1, 2)),
                    recipe.key().get(rows[1].substring(2)),
                    recipe.key().get(rows[2].substring(1, 2)));
            assertEquals(CORE.get(tier), cells, tier);
            for (Map.Entry<String, String> entry : recipe.key().entrySet()) {
                assertTrue(recipe.pattern().contains(entry.getKey()), tier + " unused key " + entry.getKey());
                assertTrue(entry.getValue().startsWith("minecraft:"), tier + ": only vanilla items, not " + entry.getValue());
            }
        }
    }

    @Test
    void noTwoRecipesAreTheSameAndNoneIsACategoryExtensionsRecipe() throws IOException {
        Set<String> seen = new HashSet<>();
        for (String category : ExtensionCategories.IDS) {
            assertTrue(seen.add(cellsOf(recipe(category + "_extension"))), category);
        }
        for (String tier : TIERS) {
            assertTrue(seen.add(cellsOf(recipe(tier + "_general_extension"))), tier + " has the same recipe as another");
        }
    }

    @Test
    void bothLanguagesHaveTheNamesTheTooltipTheTierNamesAndTheCategoryName() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");
        List<String> required = new ArrayList<>(List.of(
                "item.mcfloatingbackpack.universal_extension.tooltip",
                "category.mcfloatingbackpack.general",
                "panel.mcfloatingbackpack.row.tooltip.general"));
        for (String tier : TIERS) {
            required.add("item.mcfloatingbackpack." + tier + "_general_extension");
            required.add("tier.mcfloatingbackpack." + tier);
        }
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
        assertTrue(en.contains("\"item.mcfloatingbackpack.universal_extension.tooltip\": \"Adds a row that takes any item. Only works in a %s backpack"));
        assertTrue(zh.contains("\"item.mcfloatingbackpack.universal_extension.tooltip\": \"增加一行可以放任何物品的格子。只对%s背包生效"));
        assertTrue(zh.contains("\"panel.mcfloatingbackpack.row.tooltip.general\": \"通用行：可以放任何物品\""));
    }

    @Test
    void theItemsAreRegisteredInTheTabAndTheResolversAreWired() throws IOException {
        String items = source("item/ModItems.java");
        assertTrue(items.contains("UNIVERSAL_EXTENSIONS"));
        assertTrue(items.contains("\"_general_extension\""));
        assertTrue(items.contains("for (DeferredHolder<Item, UniversalExtensionItem> extension : UNIVERSAL_EXTENSIONS.values())"));
        String main = source("MCFloatingBackpack.java");
        assertTrue(main.contains("ExtensionPacks.registerTier("));
        assertTrue(main.contains("universal.tierId()"));
        String item = source("item/UniversalExtensionItem.java");
        assertTrue(item.contains("super(ExtensionCategories.GENERAL, properties);"));
    }

    @Test
    void theRulesTheServerRunsAreWired() throws IOException {
        String inventory = source("inventory/BackpackInventory.java");
        assertTrue(inventory.contains("PlacedPacks.count("));
        assertTrue(inventory.contains("return List.of(new int[0], all, new int[0]);"));
        String packRules = source("inventory/extension/ExtensionPackRules.java");
        assertTrue(packRules.contains("ExtensionPacks.tierOf(incoming) != null"));
        // a universal extension is never refused: that check comes before the rows are counted
        assertTrue(packRules.indexOf("ExtensionPacks.tierOf(incoming) != null") < packRules.indexOf("ExtensionRules.fits("));
        String matcher = source("inventory/extension/ItemCategoryMatcher.java");
        assertTrue(matcher.contains("ExtensionCategories.GENERAL.equals(category)"));
        assertTrue(matcher.indexOf("ExtensionCategories.GENERAL.equals(category)") < matcher.indexOf("KEYS.get(category)"));
        // the tool area rules are untouched: a universal extension is not limited by them
        assertTrue(!source("inventory/ToolAreaRules.java").contains("general"));
    }
}
