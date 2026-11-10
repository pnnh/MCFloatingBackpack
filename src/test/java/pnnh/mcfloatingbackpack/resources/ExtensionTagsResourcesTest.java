package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals(EXPECTED.keySet(), new TreeSet<>(ExtensionCategories.IDS));
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
            assertEquals(EXPECTED.get(id), found, id);
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
        for (String id : ExtensionCategories.IDS) {
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
        assertEquals(13, PresetTags.ids().size());
        assertTrue(PresetTags.isPreset("plant"), "plant stays, it is not a category");
        assertTrue(!ExtensionCategories.isKnown("plant"));
    }
}
