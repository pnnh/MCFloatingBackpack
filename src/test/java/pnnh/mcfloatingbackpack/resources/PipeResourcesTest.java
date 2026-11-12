package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.pipe.PipeKind;

/** 049: the three pipes have all their files, in the shapes contracts/recipes-and-assets.md fixes. */
class PipeResourcesTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/mcfloatingbackpack");
    private static final Path DATA = RES.resolve("data/mcfloatingbackpack");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** The rows of the recipe's pattern joined by "/", spaces kept (a space is an empty cell). */
    private static String pattern(String recipe) {
        Matcher matcher = Pattern.compile("\"pattern\":\\s*\\[([^\\]]*)\\]").matcher(recipe);
        assertTrue(matcher.find(), recipe);
        Matcher row = Pattern.compile("\"([^\"]*)\"").matcher(matcher.group(1));
        StringBuilder out = new StringBuilder();
        while (row.find()) {
            out.append(out.length() == 0 ? "" : "/").append(row.group(1));
        }
        return out.toString();
    }

    @Test
    void everyPipeHasAModelATextureAndARecipe() throws IOException {
        for (PipeKind kind : PipeKind.values()) {
            String id = kind.itemId();
            String model = read(ASSETS.resolve("models/item/" + id + ".json"));
            assertTrue(model.contains("\"minecraft:item/generated\"") && model.contains("\"mcfloatingbackpack:item/" + id + "\""), id);
            assertTrue(Files.exists(DATA.resolve("recipe/" + id + ".json")), id);
            byte[] png = Files.readAllBytes(ASSETS.resolve("textures/item/" + id + ".png"));
            assertEquals(0x89, png[0] & 0xFF, id + " is a PNG");
            int width = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
            int height = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
            assertEquals(16, width, id);
            assertEquals(16, height, id);
        }
    }

    @Test
    void theRecipesAreShapedAsTheContractSays() throws IOException {
        String water = read(DATA.resolve("recipe/water_pipe.json"));
        assertEquals(" I / C / I ", pattern(water));
        assertTrue(water.contains("\"minecraft:crafting_shaped\"") && water.contains("\"minecraft:iron_ingot\"") && water.contains("\"minecraft:water_bucket\""));
        String lava = read(DATA.resolve("recipe/lava_pipe.json"));
        assertEquals(" I / C / I ", pattern(lava));
        assertTrue(lava.contains("\"minecraft:lava_bucket\""));
        String oil = read(DATA.resolve("recipe/oil_pipe.json"));
        assertEquals(" K / B / I ", pattern(oil));
        assertTrue(oil.contains("\"minecraft:coal_block\"") && oil.contains("\"minecraft:bucket\"") && oil.contains("\"minecraft:iron_ingot\""));
    }

    @Test
    void everyRecipeGivesExactlyOneOfItsPipe() throws IOException {
        for (PipeKind kind : PipeKind.values()) {
            String recipe = read(DATA.resolve("recipe/" + kind.itemId() + ".json")).replaceAll("\\s", "");
            assertTrue(recipe.contains("\"result\":{\"count\":1,\"id\":\"mcfloatingbackpack:" + kind.itemId() + "\"}"), kind.itemId());
        }
    }

    @Test
    void aPipeIsCheaperThanTheIronTankOfTheSameLiquid() throws IOException {
        for (PipeKind kind : PipeKind.values()) {
            String pipe = pattern(read(DATA.resolve("recipe/" + kind.itemId() + ".json"))).replaceAll("[/ ]", "");
            String tank = pattern(read(DATA.resolve("recipe/iron_" + kind.fluid().id() + "_tank.json"))).replaceAll("[/ ]", "");
            assertTrue(pipe.length() < tank.length(), kind + ": " + pipe + " vs " + tank);
        }
    }

    @Test
    void thePipesAreProtectedFromTheTrashCan() throws IOException {
        String tag = read(DATA.resolve("tags/item/never_trash.json"));
        for (PipeKind kind : PipeKind.values()) {
            assertTrue(tag.contains("\"mcfloatingbackpack:" + kind.itemId() + "\""), kind.itemId());
        }
    }

    @Test
    void bothLanguagesHaveEveryKey() throws IOException {
        for (String lang : List.of("en_us", "zh_cn")) {
            String text = read(ASSETS.resolve("lang/" + lang + ".json"));
            for (PipeKind kind : PipeKind.values()) {
                assertTrue(text.contains("\"item.mcfloatingbackpack." + kind.itemId() + "\""), lang + " " + kind);
            }
            for (String key : List.of(
                    "item.mcfloatingbackpack.pipe.tooltip",
                    "item.mcfloatingbackpack.pipe.needs_tank",
                    "message.mcfloatingbackpack.pipe.full_now",
                    "message.mcfloatingbackpack.pipe.empty_now",
                    "message.mcfloatingbackpack.pipe.cannot_absorb",
                    "message.mcfloatingbackpack.pipe.cannot_place",
                    "message.mcfloatingbackpack.pipe.no_backpack",
                    "message.mcfloatingbackpack.pipe.backpack_protected",
                    "message.mcfloatingbackpack.pipe.no_tank",
                    "message.mcfloatingbackpack.pipe.oil_locked",
                    "message.mcfloatingbackpack.pipe.no_fluid")) {
                assertTrue(text.contains("\"" + key + "\""), lang + " " + key);
            }
        }
    }

    @Test
    void thePipesAreRegisteredAndShownAfterTheTanksAndBeforeTheExtensions() throws IOException {
        String items = read(Path.of("src/main/java/pnnh/mcfloatingbackpack/item/ModItems.java"));
        assertTrue(items.contains("ITEMS.register(kind.itemId(), () -> new PipeItem(kind, new Item.Properties()))"));
        int tanks = items.indexOf("TANKS.values()", items.indexOf("output.accept(AUTO_PICKUP.get())"));
        int pipes = items.indexOf("PIPES.values()", tanks);
        int extensions = items.indexOf("CATEGORY_EXTENSIONS.values()", pipes);
        assertTrue(tanks > 0 && pipes > tanks && extensions > pipes);
        String item = read(Path.of("src/main/java/pnnh/mcfloatingbackpack/item/PipeItem.java"));
        assertTrue(item.contains("stacksTo(1)"));
        assertFalse(item.contains("DataComponent"), "a pipe carries no data");
    }
}
