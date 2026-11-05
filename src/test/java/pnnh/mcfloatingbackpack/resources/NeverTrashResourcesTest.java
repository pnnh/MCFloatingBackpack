package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** 022: the item tag that lists what may not be marked as trash. Plain text checks: the test class path has no JSON library. */
class NeverTrashResourcesTest {
    private static final Path TAG = Path.of("src/main/resources/data/mcfloatingbackpack/tags/item/never_trash.json");
    private static final List<String> COLORS = List.of(
            "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray",
            "light_gray", "cyan", "purple", "blue", "brown", "green", "red", "black");

    private static String tag() throws IOException {
        return Files.readString(TAG, StandardCharsets.UTF_8).replaceAll("\\s+", "");
    }

    @Test
    void itAddsToTheTagInsteadOfReplacingIt() throws IOException {
        assertTrue(tag().contains("\"replace\":false"));
    }

    @Test
    void itListsThisModsItems() throws IOException {
        String tag = tag();
        for (String id : List.of(
                "leather_floating_backpack",
                "iron_floating_backpack",
                "gold_floating_backpack",
                "diamond_floating_backpack",
                "netherite_floating_backpack",
                "tag_manager",
                "trash_can")) {
            assertTrue(tag.contains("\"mcfloatingbackpack:" + id + "\""), id);
        }
    }

    @Test
    void itListsShulkerBoxesOfEveryColorAndTheValuables() throws IOException {
        String tag = tag();
        assertTrue(tag.contains("\"minecraft:shulker_box\""));
        for (String color : COLORS) {
            assertTrue(tag.contains("\"minecraft:" + color + "_shulker_box\""), color);
        }
        for (String id : List.of(
                "dragon_egg", "nether_star", "elytra", "totem_of_undying", "beacon", "heart_of_the_sea",
                "enchanted_golden_apple")) {
            assertTrue(tag.contains("\"minecraft:" + id + "\""), id);
        }
    }

    @Test
    void bundlesAreOptionalEntries() throws IOException {
        String tag = tag();
        assertTrue(tag.contains("{\"id\":\"minecraft:bundle\",\"required\":false}"));
        for (String color : COLORS) {
            assertTrue(tag.contains("{\"id\":\"minecraft:" + color + "_bundle\",\"required\":false}"), color);
        }
    }
}
