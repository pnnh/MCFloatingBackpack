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
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;
import pnnh.mcfloatingbackpack.inventory.tags.PresetTags;

/** 035: the ten data tags of the category extensions and the presets that go with them. Plain text checks (no JSON library). */
class ExtensionTagsResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path TAGS = ROOT.resolve("data/mcfloatingbackpack/tags/item/extension");

    /** What spec 035 decision 4 puts into each category; the entries starting with # are tags. */
    private static final Map<String, List<String>> EXPECTED = Map.of(
            "food", List.of("#c:foods"),
            "tool", List.of("#minecraft:pickaxes", "#minecraft:axes", "#minecraft:shovels", "#minecraft:hoes", "#c:tools/shear",
                    "#c:tools/fishing_rod", "#c:tools/brush", "#c:tools/igniter", "#c:tools/wrench"),
            "weapon", List.of("#minecraft:swords", "#minecraft:arrows", "#c:tools/melee_weapon", "#c:tools/ranged_weapon",
                    "#c:tools/bow", "#c:tools/crossbow", "#c:tools/mace", "#c:tools/shield"),
            "armor", List.of("#c:armors"),
            "ore", List.of("#c:ores", "#c:raw_materials", "#c:ingots", "#c:nuggets", "#c:gems", "#c:dusts", "#minecraft:coals"),
            "crop", List.of("#c:crops", "#c:seeds", "#minecraft:saplings"),
            "wood", List.of("#minecraft:logs", "#minecraft:planks", "#c:stripped_logs", "#c:stripped_woods", "#c:rods/wooden",
                    "#minecraft:bamboo_blocks"),
            "potion", List.of("#c:potions"),
            "redstone", List.of("#c:dusts/redstone", "#c:storage_blocks/redstone"),
            "disc", List.of("#c:music_discs"));

    /** 051 (contracts/categories.md): the tag entries of the nine categories added later, in file order; their plain items are checked apart. */
    private static final Map<String, List<String>> EXPECTED_051 = Map.of(
            "building", List.of("#minecraft:stairs", "#minecraft:slabs", "#minecraft:walls", "#minecraft:fences", "#minecraft:fence_gates",
                    "#minecraft:doors", "#minecraft:trapdoors", "#minecraft:terracotta", "#minecraft:wool", "#c:bricks", "#c:glass_blocks",
                    "#c:glass_panes", "#c:concretes", "#c:concrete_powders", "#c:glazed_terracottas"),
            "natural", List.of("#c:stones", "#c:cobblestones", "#c:sands", "#c:gravels", "#c:netherracks", "#c:end_stones", "#c:obsidians",
                    "#minecraft:dirt", "#minecraft:sand"),
            "plant", List.of("#minecraft:flowers", "#minecraft:leaves", "#c:mushrooms"),
            "decor", List.of("#minecraft:candles", "#minecraft:banners", "#minecraft:beds", "#minecraft:wool_carpets", "#minecraft:signs",
                    "#minecraft:hanging_signs", "#minecraft:skulls", "#minecraft:decorated_pot_sherds", "#c:bookshelves", "#c:chains",
                    "#c:dyes"),
            "mobdrop", List.of("#c:bones", "#c:strings", "#c:gunpowders", "#c:leathers", "#c:feathers", "#c:slime_balls", "#c:ender_pearls",
                    "#c:eggs", "#c:nether_stars", "#c:rods/blaze", "#c:rods/breeze"),
            "storage", List.of("#c:chests", "#c:barrels", "#c:shulker_boxes", "#c:player_workstations/crafting_tables",
                    "#c:player_workstations/furnaces", "#c:villager_job_sites"),
            "transport", List.of("#minecraft:rails", "#minecraft:boats", "#minecraft:chest_boats"),
            "enchant", List.of(),
            "meat", List.of("#minecraft:meat", "#minecraft:fishes", "#c:foods/raw_meat", "#c:foods/cooked_meat", "#c:foods/raw_fish",
                    "#c:foods/cooked_fish"));

    private static List<String> expected(String id) {
        return EXPECTED.containsKey(id) ? EXPECTED.get(id) : EXPECTED_051.get(id);
    }

    /** 051: items that must be in the file by name (quickstart's spot-check table: at least five that belong, for the ones that need names). */
    private static final Map<String, List<String>> PLAIN_051 = Map.of(
            "natural", List.of("clay", "snow_block", "ice", "soul_sand", "sculk", "flint"),
            "plant", List.of("short_grass", "fern", "vine", "lily_pad", "cactus", "brain_coral_fan"),
            "decor", List.of("painting", "lantern", "item_frame", "flower_pot", "armor_stand", "campfire"),
            "mobdrop", List.of("ghast_tear", "phantom_membrane", "rabbit_foot", "ink_sac", "slime_block"),
            "storage", List.of("bundle"),
            "transport", List.of("minecart", "saddle", "lead", "elytra", "chest_minecart"),
            "enchant", List.of("book", "lapis_lazuli", "enchanting_table", "bookshelf", "experience_bottle", "anvil"),
            "building", List.of("polished_andesite", "smooth_stone", "chiseled_stone_bricks", "cut_sandstone", "quartz_block", "copper_block"));

    private static final List<String> REDSTONE_PARTS = List.of("repeater", "comparator", "piston", "sticky_piston", "observer",
            "lever", "hopper", "dropper", "dispenser", "redstone_lamp", "redstone_torch", "daylight_detector", "target");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void thereIsOneFilePerCategoryAndNothingElse() throws IOException {
        TreeSet<String> names = new TreeSet<>();
        try (Stream<Path> files = Files.list(TAGS)) {
            files.forEach(p -> names.add(p.getFileName().toString()));
        }
        TreeSet<String> expected = new TreeSet<>();
        for (String id : ExtensionCategories.IDS) {
            expected.add(id + ".json");
        }
        assertEquals(expected, names);
        TreeSet<String> described = new TreeSet<>(EXPECTED.keySet());
        described.addAll(EXPECTED_051.keySet());
        assertEquals(described, new TreeSet<>(ExtensionCategories.IDS));
    }

    @Test
    void everyFileAddsToTheTagAndMakesEveryReferenceOptional() throws IOException {
        for (String id : ExtensionCategories.IDS) {
            String json = read(TAGS.resolve(id + ".json"));
            String compact = json.replaceAll("\\s+", "");
            assertTrue(compact.contains("\"replace\":false"), id + " must not replace");
            // every tag entry is an object with required:false (a plain string would make a missing tag an error)
            Matcher plain = Pattern.compile("(?<!\"id\":)\"#[^\"]+\"").matcher(compact);
            assertTrue(!plain.find(), id + " has a tag reference that is not an object with an id: " + (plain.find(0) ? plain.group() : ""));
            Matcher entries = Pattern.compile("\\{\"id\":\"(#[^\"]+)\",\"required\":(true|false)\\}").matcher(compact);
            List<String> found = new ArrayList<>();
            while (entries.find()) {
                assertEquals("false", entries.group(2), id + ": " + entries.group(1));
                found.add(entries.group(1));
            }
            assertEquals(expected(id), found, id);
        }
    }

    @Test
    void redstoneAlsoListsItsPartsByName() throws IOException {
        String compact = read(TAGS.resolve("redstone.json")).replaceAll("\\s+", "");
        for (String part : REDSTONE_PARTS) {
            assertTrue(compact.contains("\"minecraft:" + part + "\""), part);
        }
    }

    @Test
    void theOtherFilesListNoPlainItems() throws IOException {
        for (String id : ExtensionCategories.IDS.subList(0, 10)) { // the first ten; the nine of 051 list items by name on purpose
            if (id.equals("redstone")) {
                continue;
            }
            String compact = read(TAGS.resolve(id + ".json")).replaceAll("\\s+", "");
            // "values":[{...},{...}] only: no bare "namespace:item" strings
            assertTrue(!Pattern.compile("[\\[,]\"[a-z_]+:[^\"]+\"").matcher(compact).find(), id);
        }
    }

    @Test
    void everyCategoryIsAPresetTagWithBothLanguages() throws IOException {
        String en = read(ROOT.resolve("assets/mcfloatingbackpack/lang/en_us.json"));
        String zh = read(ROOT.resolve("assets/mcfloatingbackpack/lang/zh_cn.json"));
        for (String id : ExtensionCategories.IDS) {
            assertTrue(PresetTags.isPreset(id), id + " must be a preset tag so that a manual tag with it is offered");
            assertTrue(en.contains("\"" + PresetTags.langKey(id) + "\""), "en_us missing " + id);
            assertTrue(zh.contains("\"" + PresetTags.langKey(id) + "\""), "zh_cn missing " + id);
        }
        assertEquals(21, PresetTags.ids().size());
        assertTrue(PresetTags.isPreset("plant"), "plant was a preset before it became a category (051)");
        assertTrue(ExtensionCategories.isKnown("plant"));
    }

    @Test
    void the051CategoriesListTheirItemsByNameAndNeverUseTheTagsThatDoNotWork() throws IOException {
        for (Map.Entry<String, List<String>> entry : PLAIN_051.entrySet()) {
            String compact = read(TAGS.resolve(entry.getKey() + ".json")).replaceAll("\\s+", "");
            for (String item : entry.getValue()) {
                assertTrue(compact.contains("\"minecraft:" + item + "\""), entry.getKey() + " lacks " + item);
            }
        }
        for (String id : ExtensionCategories.IDS) {
            String compact = read(TAGS.resolve(id + ".json")).replaceAll("\\s+", "");
            assertFalse(compact.contains("#minecraft:shulker_boxes"), id + ": that tag does not exist, #c:shulker_boxes does");
            assertFalse(compact.contains("#c:dyed"), id + ": #c:dyed holds every dyed block");
        }
        // things that must not be in the category they would be tempting for
        assertFalse(read(TAGS.resolve("plant.json")).contains("sapling"), "saplings stay in crop");
        assertFalse(read(TAGS.resolve("enchant.json")).contains("pickaxe"));
        assertFalse(read(TAGS.resolve("building.json")).contains("cobblestone\""), "cobblestone is natural");
        assertTrue(read(TAGS.resolve("storage.json")).contains("#c:shulker_boxes"));
    }

    @Test
    void enchantAlsoMatchesTheStoredEnchantmentsComponentAndNotTheEnchantments() throws IOException {
        String matcher = Files.readString(Path.of("src/main/java/pnnh/mcfloatingbackpack/inventory/extension/ItemCategoryMatcher.java"),
                StandardCharsets.UTF_8);
        int at = matcher.indexOf("category.equals(\"enchant\")");
        assertTrue(at > 0);
        assertTrue(matcher.substring(at, at + 120).contains("DataComponents.STORED_ENCHANTMENTS"));
        assertFalse(matcher.replace("STORED_ENCHANTMENTS", "").contains("ENCHANTMENTS"), "ENCHANTMENTS would take every enchanted tool");
    }
}
