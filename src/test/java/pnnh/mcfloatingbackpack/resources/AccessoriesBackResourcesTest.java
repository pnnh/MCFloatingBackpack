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
 * 034: the data file that makes Accessories' back slot accept the floating backpack: the {@code accessories:back} item tag. There is no
 * slot assignment file: Accessories gives every player a back slot by itself. Plain text checks (the test class path has no JSON library).
 */
class AccessoriesBackResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");
    private static final Path TAG = ROOT.resolve("data/accessories/tags/item/back.json");

    private static List<String> values(String json) {
        Matcher array = Pattern.compile("\"values\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json);
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
        assertTrue(Files.exists(TAG), "data/accessories/tags/item/back.json is missing");
        String json = Files.readString(TAG, StandardCharsets.UTF_8);
        assertTrue(json.replaceAll("\\s+", "").contains("\"replace\":false"), "the tag must not replace what other mods put in accessories:back");
        Set<String> expected = new TreeSet<>();
        for (BackpackTier tier : BackpackTier.values()) {
            expected.add("mcfloatingbackpack:" + tier.id());
        }
        assertEquals(5, expected.size());
        assertEquals(expected, new TreeSet<>(values(json)));
        assertEquals(5, values(json).size(), "no entry twice");
    }

    @Test
    void theTagIsWhereAccessoriesReadsIt() {
        // data/<namespace>/tags/item/<slot>.json under the accessories namespace (the item tag is accessories:back)
        assertEquals("data/accessories/tags/item/back.json", ROOT.relativize(TAG).toString().replace('\\', '/'));
    }

    @Test
    void thereIsNoSlotAssignmentBecauseAccessoriesGivesPlayersABackSlotItself() {
        assertFalse(Files.exists(ROOT.resolve("data/mcfloatingbackpack/accessories")), "Accessories already assigns its back slot to players");
        assertFalse(Files.exists(ROOT.resolve("data/accessories/accessories")), "nothing of ours belongs in Accessories' own data folder");
    }
}
