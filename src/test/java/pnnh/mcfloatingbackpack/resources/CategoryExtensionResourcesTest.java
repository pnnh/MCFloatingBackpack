package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

/** 036, 051: the category extension items (ten, then nine more)' models, textures, recipes and language keys. Plain text and pixel checks (no JSON library). */
class CategoryExtensionResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    /** contracts/recipes.md: the five cells (top, left, centre, right, bottom) of each recipe; a leading # is a tag. */
    private static final Map<String, List<String>> CORE = new LinkedHashMap<>();

    static {
        CORE.put("food", List.of("minecraft:barrel", "minecraft:bread", "minecraft:smoker", "minecraft:bread", "minecraft:golden_carrot"));
        CORE.put("tool", List.of("minecraft:iron_pickaxe", "minecraft:iron_axe", "minecraft:grindstone", "minecraft:iron_shovel", "minecraft:iron_hoe"));
        CORE.put("weapon", List.of("minecraft:iron_sword", "minecraft:arrow", "minecraft:shield", "minecraft:arrow", "minecraft:bow"));
        CORE.put("armor", List.of("minecraft:iron_helmet", "minecraft:leather", "minecraft:smithing_table", "minecraft:leather", "minecraft:iron_boots"));
        CORE.put("ore", List.of("minecraft:diamond", "minecraft:copper_ingot", "minecraft:blast_furnace", "minecraft:gold_ingot", "minecraft:lapis_lazuli"));
        CORE.put("crop", List.of("minecraft:iron_hoe", "minecraft:bone_meal", "minecraft:hay_block", "minecraft:bone_meal", "minecraft:water_bucket"));
        CORE.put("wood", List.of("minecraft:iron_axe", "#minecraft:logs", "minecraft:stonecutter", "#minecraft:logs", "#minecraft:logs"));
        CORE.put("potion", List.of("minecraft:nether_wart", "minecraft:glass_bottle", "minecraft:brewing_stand", "minecraft:glass_bottle", "minecraft:glowstone_dust"));
        CORE.put("redstone", List.of("minecraft:comparator", "minecraft:repeater", "minecraft:redstone_block", "minecraft:piston", "minecraft:redstone"));
        CORE.put("disc", List.of("minecraft:diamond", "minecraft:note_block", "minecraft:jukebox", "minecraft:lapis_lazuli", "#c:music_discs"));
        // 051
        CORE.put("building", List.of("minecraft:glass", "minecraft:stone_bricks", "minecraft:stonecutter", "minecraft:stone_bricks", "minecraft:terracotta"));
        CORE.put("natural", List.of("minecraft:grass_block", "minecraft:cobblestone", "minecraft:iron_shovel", "minecraft:cobblestone", "minecraft:sand"));
        CORE.put("plant", List.of("minecraft:sunflower", "#minecraft:leaves", "minecraft:composter", "#minecraft:leaves", "minecraft:lily_pad"));
        CORE.put("decor", List.of("minecraft:painting", "minecraft:lantern", "minecraft:loom", "minecraft:lantern", "minecraft:candle"));
        CORE.put("mobdrop", List.of("minecraft:bone", "minecraft:string", "minecraft:slime_block", "minecraft:string", "minecraft:ender_pearl"));
        CORE.put("storage", List.of("minecraft:chest", "minecraft:barrel", "minecraft:hopper", "minecraft:barrel", "minecraft:trapped_chest"));
        CORE.put("transport", List.of("minecraft:minecart", "minecraft:rail", "minecraft:oak_boat", "minecraft:rail", "minecraft:saddle"));
        CORE.put("enchant", List.of("minecraft:book", "minecraft:lapis_lazuli", "minecraft:enchanting_table", "minecraft:lapis_lazuli", "minecraft:bookshelf"));
        CORE.put("meat", List.of("minecraft:cooked_beef", "minecraft:cooked_cod", "minecraft:smoker", "minecraft:cooked_cod", "minecraft:cooked_chicken"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String compact(String relative) throws IOException {
        return read(relative).replaceAll("\\s+", "");
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
    void theTableCoversEveryCategory() {
        assertEquals(ExtensionCategories.IDS, new ArrayList<>(CORE.keySet()));
    }

    @Test
    void everyItemHasAFlatModelOfItsTexture() throws IOException {
        for (String category : ExtensionCategories.IDS) {
            String model = compact("assets/mcfloatingbackpack/models/item/" + category + "_extension.json");
            assertTrue(model.contains("\"parent\":\"minecraft:item/generated\""), category);
            assertTrue(model.contains("\"layer0\":\"mcfloatingbackpack:item/" + category + "_extension\""), category);
        }
    }

    private static BufferedImage texture(String category) throws IOException {
        BufferedImage image = ImageIO.read(ROOT.resolve("assets/mcfloatingbackpack/textures/item/" + category + "_extension.png").toFile());
        assertNotNull(image, category);
        return image;
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
    void everyTextureIs16x16WithRealContent() throws IOException {
        for (String category : ExtensionCategories.IDS) {
            byte[] png = Files.readAllBytes(ROOT.resolve("assets/mcfloatingbackpack/textures/item/" + category + "_extension.png"));
            assertEquals(16, java.nio.ByteBuffer.wrap(png, 16, 4).getInt(), category);
            assertEquals(16, java.nio.ByteBuffer.wrap(png, 20, 4).getInt(), category);
            assertEquals(8, png[24], category + " bit depth");
            assertEquals(6, png[25], category + " must be RGBA");
            BufferedImage image = texture(category);
            long opaque = pixels(image).stream().filter(argb -> (argb >>> 24) == 255).count();
            assertTrue(opaque >= 120, category + " has only " + opaque + " opaque pixels");
        }
    }

    @Test
    void theTexturesAndTheOlderToolIconsAreAllDifferent() throws IOException {
        List<List<Integer>> all = new ArrayList<>();
        for (String category : ExtensionCategories.IDS) {
            all.add(pixels(texture(category)));
        }
        for (String older : List.of("auto_pickup", "trash_can", "tag_manager")) {
            all.add(pixels(ImageIO.read(ROOT.resolve("assets/mcfloatingbackpack/textures/item/" + older + ".png").toFile())));
        }
        assertEquals(all.size(), new HashSet<>(all).size(), "two icons are the same picture");
    }

    private record Recipe(String pattern, Map<String, String> key) {
    }

    private static Recipe recipe(String category) throws IOException {
        String json = compact("data/mcfloatingbackpack/recipe/" + category + "_extension.json");
        assertTrue(json.contains("\"type\":\"minecraft:crafting_shaped\""), category);
        assertTrue(json.contains("\"result\":{\"count\":1,\"id\":\"mcfloatingbackpack:" + category + "_extension\"}"), category);
        Matcher pattern = Pattern.compile("\"pattern\":\\[\"([^\"]{3})\",\"([^\"]{3})\",\"([^\"]{3})\"\\]").matcher(json);
        assertTrue(pattern.find(), category + " pattern must be three rows of three");
        String rows = pattern.group(1) + "/" + pattern.group(2) + "/" + pattern.group(3);
        Map<String, String> key = new LinkedHashMap<>();
        Matcher entry = Pattern.compile("\"([A-Z])\":\\{\"(item|tag)\":\"([^\"]+)\"\\}").matcher(json);
        while (entry.find()) {
            key.put(entry.group(1), ("tag".equals(entry.group(2)) ? "#" : "") + entry.group(3));
        }
        return new Recipe(rows, key);
    }

    @Test
    void everyRecipeIsTheIronFrameWithTheFiveCoreMaterials() throws IOException {
        for (String category : ExtensionCategories.IDS) {
            Recipe recipe = recipe(category);
            String[] rows = recipe.pattern().split("/");
            // four corners are the iron ingot
            for (String corner : new String[] {rows[0].substring(0, 1), rows[0].substring(2), rows[2].substring(0, 1), rows[2].substring(2)}) {
                assertEquals("minecraft:iron_ingot", recipe.key().get(corner), category + " corner");
            }
            // the five other cells, top, left, centre, right, bottom
            List<String> cells = List.of(
                    recipe.key().get(rows[0].substring(1, 2)),
                    recipe.key().get(rows[1].substring(0, 1)),
                    recipe.key().get(rows[1].substring(1, 2)),
                    recipe.key().get(rows[1].substring(2)),
                    recipe.key().get(rows[2].substring(1, 2)));
            assertEquals(CORE.get(category), cells, category);
            // every key is used, and no mod's item is named
            for (Map.Entry<String, String> entry : recipe.key().entrySet()) {
                assertTrue(recipe.pattern().contains(entry.getKey()), category + " unused key " + entry.getKey());
                assertTrue(entry.getValue().startsWith("minecraft:") || entry.getValue().startsWith("#minecraft:")
                        || entry.getValue().startsWith("#c:"), category + ": " + entry.getValue());
            }
        }
    }

    @Test
    void noTwoRecipesAreTheSame() throws IOException {
        Set<String> seen = new HashSet<>();
        for (String category : ExtensionCategories.IDS) {
            Recipe recipe = recipe(category);
            // compare what is in the cells, not the letters used for them
            String[] rows = recipe.pattern().split("/");
            StringBuilder cells = new StringBuilder();
            for (String row : rows) {
                for (char c : row.toCharArray()) {
                    cells.append(recipe.key().get(String.valueOf(c))).append(';');
                }
            }
            assertTrue(seen.add(cells.toString()), category + " has the same recipe as another");
        }
    }

    @Test
    void bothLanguagesHaveTheNamesTheTooltipAndTheLimitMessage() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh), "the two language files must have the same keys");
        List<String> required = new ArrayList<>(List.of(
                "item.mcfloatingbackpack.category_extension.tooltip", "message.mcfloatingbackpack.limit.extension_rows"));
        for (String category : ExtensionCategories.IDS) {
            required.add("item.mcfloatingbackpack." + category + "_extension");
        }
        for (String key : required) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
        // the tooltip fills in the category's name
        assertTrue(en.contains("category_extension.tooltip\": \"Put it in a function area of the floating backpack to add a row that only stores %s\""));
        assertTrue(zh.contains("放进浮动背包的功能区，增加一行只能存 %s 的格子"));
        assertTrue(zh.contains("\"item.mcfloatingbackpack.food_extension\": \"食物扩展\""));
        assertTrue(zh.contains("\"item.mcfloatingbackpack.disc_extension\": \"唱片扩展\""));
        assertTrue(zh.contains("\"item.mcfloatingbackpack.meat_extension\": \"肉与鱼扩展\""));
        assertTrue(en.contains("\"item.mcfloatingbackpack.meat_extension\": \"Meat & Fish Extension\""));
    }

    @Test
    void theItemsAreRegisteredInTheFixedOrderAndTheRulesAreTold() throws IOException {
        String items = Files.readString(SRC.resolve("item/ModItems.java"), StandardCharsets.UTF_8);
        assertTrue(items.contains("for (String category : ExtensionCategories.IDS)"));
        assertTrue(items.contains("category + \"_extension\""));
        assertTrue(items.contains("CATEGORY_EXTENSIONS.values()"));
        assertTrue(items.indexOf("output.accept(AUTO_PICKUP.get())") < items.indexOf("CATEGORY_EXTENSIONS.values()"),
                "the extensions follow the auto pickup in the creative tab");
        String main = Files.readString(SRC.resolve("MCFloatingBackpack.java"), StandardCharsets.UTF_8);
        assertTrue(main.contains("ExtensionPacks.register("));
        assertTrue(main.contains("instanceof CategoryExtensionItem"));
        String item = Files.readString(SRC.resolve("item/CategoryExtensionItem.java"), StandardCharsets.UTF_8);
        assertTrue(item.contains("stacksTo(1)"));
        assertFalse(item.isEmpty());
    }
}
