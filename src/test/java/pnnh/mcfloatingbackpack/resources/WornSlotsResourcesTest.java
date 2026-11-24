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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * 061: the data files that give Curios and Accessories the dedicated slot, and the tags of the dedicated and the charm slot, are all there,
 * parse, name the same slot, and carry the condition that the start-up switch decides.
 */
class WornSlotsResourcesTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path DATA = RES.resolve("data/mcfloatingbackpack");
    private static final String SLOT = "floating_backpack";
    private static final String[] TIERS = {"leather", "iron", "gold", "diamond", "netherite"};

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void assertCondition(JsonObject file, String mod) {
        JsonArray conditions = file.getAsJsonArray("neoforge:conditions");
        assertEquals(1, conditions.size());
        JsonObject condition = conditions.get(0).getAsJsonObject();
        assertEquals("mcfloatingbackpack:slot_enabled", condition.get("type").getAsString());
        assertEquals(mod, condition.get("mod").getAsString());
    }

    private static List<String> strings(JsonArray array) {
        List<String> out = new ArrayList<>();
        for (JsonElement element : array) {
            out.add(element.getAsString());
        }
        return out;
    }

    @Test
    void theCuriosSlotAndItsEntityFileCarryTheCondition() throws IOException {
        JsonObject slot = json(DATA.resolve("curios/slots/" + SLOT + ".json"));
        assertCondition(slot, "curios");
        assertEquals(1, slot.get("size").getAsInt(), "one slot");
        assertEquals("mcfloatingbackpack:slot/" + SLOT, slot.get("icon").getAsString());
        assertEquals(List.of("curios:tag"), strings(slot.getAsJsonArray("validators")));
        JsonObject entities = json(DATA.resolve("curios/entities/player_" + SLOT + ".json"));
        assertCondition(entities, "curios");
        assertEquals(List.of("minecraft:player"), strings(entities.getAsJsonArray("entities")));
        assertEquals(List.of(SLOT), strings(entities.getAsJsonArray("slots")));
    }

    @Test
    void theAccessoriesSlotItsEntityAndItsGroupCarryTheCondition() throws IOException {
        JsonObject slot = json(DATA.resolve("accessories/slot/" + SLOT + ".json"));
        assertCondition(slot, "accessories");
        assertEquals(1, slot.get("amount").getAsInt(), "one slot");
        assertEquals("mcfloatingbackpack:gui/slot/" + SLOT, slot.get("icon").getAsString());
        assertTrue(strings(slot.getAsJsonArray("validators")).contains("accessories:tag"));
        JsonObject entity = json(DATA.resolve("accessories/entity/player_" + SLOT + ".json"));
        assertCondition(entity, "accessories");
        assertEquals(List.of(SLOT), strings(entity.getAsJsonArray("slots")));
        JsonObject group = json(DATA.resolve("accessories/group/chest.json"));
        assertCondition(group, "accessories");
        assertEquals(List.of(SLOT), strings(group.getAsJsonArray("slots")));
    }

    @Test
    void theOldBackSlotFilesAreUntouchedAndStillUnconditional() throws IOException {
        JsonObject entities = json(DATA.resolve("curios/entities/player_back.json"));
        assertFalse(entities.has("neoforge:conditions"), "033: the back slot is assigned whatever the switch says");
        assertEquals(List.of("back"), strings(entities.getAsJsonArray("slots")));
    }

    @Test
    void theTagsHoldTheFiveTiersAndHaveNoCondition() throws IOException {
        for (String mod : new String[] {"curios", "accessories"}) {
            for (String slot : new String[] {"back", SLOT, "charm"}) {
                JsonObject tag = json(RES.resolve("data/" + mod + "/tags/item/" + slot + ".json"));
                assertFalse(tag.has("neoforge:conditions"), mod + ":" + slot);
                List<String> values = strings(tag.getAsJsonArray("values"));
                // 065: the dedicated slot also takes the five combined backpacks; the back and charm slots do not
                assertEquals(slot.equals(SLOT) ? 2 * TIERS.length : TIERS.length, values.size(), mod + ":" + slot);
                for (String tier : TIERS) {
                    assertTrue(values.contains("mcfloatingbackpack:" + tier + "_floating_backpack"), mod + ":" + slot + " " + tier);
                    assertEquals(
                            slot.equals(SLOT),
                            values.contains("mcfloatingbackpack:" + tier + "_combined_backpack"),
                            mod + ":" + slot + " combined " + tier);
                }
            }
        }
    }

    @Test
    void theIconsExistAndArePng16x16() throws IOException {
        for (String path : new String[] {"textures/slot/" + SLOT + ".png", "textures/gui/slot/" + SLOT + ".png"}) {
            byte[] png = Files.readAllBytes(RES.resolve("assets/mcfloatingbackpack/" + path));
            assertEquals((byte) 0x89, png[0]);
            assertEquals('P', png[1]);
            int width = ((png[16] & 0xff) << 24) | ((png[17] & 0xff) << 16) | ((png[18] & 0xff) << 8) | (png[19] & 0xff);
            int height = ((png[20] & 0xff) << 24) | ((png[21] & 0xff) << 16) | ((png[22] & 0xff) << 8) | (png[23] & 0xff);
            assertEquals(16, width, path);
            assertEquals(16, height, path);
        }
    }

    @Test
    void theSlotIsNamedInBothLanguages() throws IOException {
        for (String lang : new String[] {"en_us", "zh_cn"}) {
            JsonObject names = json(RES.resolve("assets/mcfloatingbackpack/lang/" + lang + ".json"));
            assertTrue(names.has("curios.identifier." + SLOT), lang);
            assertTrue(names.has("accessories.slot." + SLOT), lang);
            assertFalse(names.get("curios.identifier." + SLOT).getAsString().isBlank(), lang);
        }
    }
}
