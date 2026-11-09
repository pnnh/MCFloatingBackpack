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
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.item.BackpackTier;

/**
 * 033: the two data files that make Curios' back slot work for the floating backpack: the {@code curios:back} item tag, and the
 * slot assignment that gives a player a back slot. Plain text checks (the test class path has no JSON library).
 */
class CuriosBackResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path TAG = ROOT.resolve("data/curios/tags/item/back.json");
    private static final Path ENTITIES = ROOT.resolve("data/mcfloatingbackpack/curios/entities/player_back.json");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static List<String> quotedValues(String json, String key) {
        Matcher array = Pattern.compile("\"" + key + "\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
        List<String> values = new ArrayList<>();
        if (array.find()) {
            Matcher item = Pattern.compile("\"([^\"]*)\"").matcher(array.group(1));
            while (item.find()) {
                values.add(item.group(1));
            }
        }
        return values;
    }

    @Test
    void theBackTagHoldsExactlyTheFiveBackpacksAndDoesNotReplaceOtherModsEntries() throws IOException {
        assertTrue(Files.exists(TAG), "data/curios/tags/item/back.json is missing");
        String json = read(TAG);
        assertTrue(json.replaceAll("\\s+", "").contains("\"replace\":false"), "the tag must not replace what other mods put in curios:back");
        Set<String> expected = new TreeSet<>();
        for (BackpackTier tier : BackpackTier.values()) {
            expected.add("mcfloatingbackpack:" + tier.id());
        }
        assertEquals(5, expected.size());
        assertEquals(expected, new TreeSet<>(quotedValues(json, "values")));
        assertEquals(5, quotedValues(json, "values").size(), "no entry twice");
    }

    @Test
    void theSlotAssignmentGivesPlayersABackSlot() throws IOException {
        assertTrue(Files.exists(ENTITIES), "the player's back slot assignment is missing");
        String json = read(ENTITIES);
        assertEquals(List.of("minecraft:player"), quotedValues(json, "entities"));
        assertEquals(List.of("back"), quotedValues(json, "slots"));
    }

    @Test
    void theSlotAssignmentIsUnconditionalAndItsNameIsNotSkippedByCurios() throws IOException {
        // Curios skips entity files whose name starts with an underscore; and the assignment is not tied to a condition (spec decision 5)
        assertFalse(ENTITIES.getFileName().toString().startsWith("_"));
        assertFalse(read(ENTITIES).contains("conditions"));
    }

    @Test
    void theFilesAreWhereCuriosReadsThem() {
        // tags: data/<namespace>/tags/item/<slot>.json under the curios namespace; slot assignments: data/<any namespace>/curios/entities/
        assertEquals("data/curios/tags/item/back.json", ROOT.relativize(TAG).toString().replace('\\', '/'));
        assertEquals("data/mcfloatingbackpack/curios/entities/player_back.json", ROOT.relativize(ENTITIES).toString().replace('\\', '/'));
    }
}
