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

import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.tank.FluidKind;
import pnnh.mcfloatingbackpack.tank.TankCatalog;
import pnnh.mcfloatingbackpack.tank.TankCatalog.Tank;

/** 048: the 19 tanks have all their files, in the shapes contracts/recipes-and-assets.md fixes. */
class TankResourcesTest {
    private static final Path RES = Path.of("src/main/resources");
    private static final Path ASSETS = RES.resolve("assets/mcfloatingbackpack");
    private static final Path DATA = RES.resolve("data/mcfloatingbackpack");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String id(Tank tank) {
        return TankCatalog.itemId(tank.kind(), tank.tier());
    }

    private static String container(FluidKind kind) {
        return switch (kind) {
            case WATER -> "minecraft:water_bucket";
            case LAVA -> "minecraft:lava_bucket";
            case MILK -> "minecraft:milk_bucket";
            case HONEY -> "minecraft:honey_bottle";
            default -> throw new IllegalArgumentException();
        };
    }

    @Test
    void everyTankHasAModelATextureAndARecipe() throws IOException {
        assertEquals(19, TankCatalog.all().size());
        for (Tank tank : TankCatalog.all()) {
            String id = id(tank);
            String model = read(ASSETS.resolve("models/item/" + id + ".json"));
            assertTrue(model.contains("\"minecraft:item/generated\""), id);
            assertTrue(model.contains("\"mcfloatingbackpack:item/" + id + "\""), id);
            assertTrue(Files.exists(DATA.resolve("recipe/" + id + ".json")), id);
            Path texture = ASSETS.resolve("textures/item/" + id + ".png");
            byte[] png = Files.readAllBytes(texture);
            assertEquals(0x89, png[0] & 0xFF, id + " is a PNG");
            int width = ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
            int height = ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
            assertEquals(16, width, id);
            assertEquals(16, height, id);
        }
    }

    @Test
    void thereIsNoLeatherLavaTankAnywhere() throws IOException {
        for (String path : List.of(
                "assets/mcfloatingbackpack/models/item/leather_lava_tank.json",
                "assets/mcfloatingbackpack/textures/item/leather_lava_tank.png",
                "data/mcfloatingbackpack/recipe/leather_lava_tank.json")) {
            assertFalse(Files.exists(RES.resolve(path)), path);
        }
        assertFalse(read(ASSETS.resolve("lang/en_us.json")).contains("leather_lava_tank"));
        assertFalse(read(DATA.resolve("tags/item/never_trash.json")).contains("leather_lava_tank"));
    }

    @Test
    void thereAreNoTankFilesForTheOtherNames() throws IOException {
        try (var stream = Files.list(ASSETS.resolve("models/item"))) {
            assertEquals(19, stream.filter(p -> p.getFileName().toString().endsWith("_tank.json")).count());
        }
        try (var stream = Files.list(DATA.resolve("recipe"))) {
            assertEquals(19, stream.filter(p -> p.getFileName().toString().endsWith("_tank.json")).count());
        }
        try (var stream = Files.list(ASSETS.resolve("textures/item"))) {
            assertEquals(19, stream.filter(p -> p.getFileName().toString().endsWith("_tank.png")).count());
        }
    }

    private static String pattern(String recipe) {
        Matcher matcher = Pattern.compile("\"pattern\":\\s*\\[([^\\]]*)\\]").matcher(recipe);
        assertTrue(matcher.find(), recipe);
        return matcher.group(1).replaceAll("[\\s\",]", "/").replaceAll("/+", "/").replaceAll("^/|/$", "");
    }

    @Test
    void leatherAndIronTanksAreShapedAsTheContractSays() throws IOException {
        for (Tank tank : TankCatalog.all()) {
            String recipe = read(DATA.resolve("recipe/" + id(tank) + ".json"));
            if (tank.tier() == BackpackTier.LEATHER) {
                assertTrue(recipe.contains("\"minecraft:crafting_shaped\""));
                assertTrue(recipe.contains("\"minecraft:leather\"") && recipe.contains("\"minecraft:glass\""), id(tank));
                if (tank.kind() == FluidKind.OIL) {
                    assertEquals("LGL/LBL/LKL", pattern(recipe));
                    assertTrue(recipe.contains("\"minecraft:bucket\"") && recipe.contains("\"minecraft:coal_block\""));
                } else {
                    assertEquals("LGL/LCL/LLL", pattern(recipe));
                    assertTrue(recipe.contains("\"" + container(tank.kind()) + "\""), id(tank));
                }
            } else if (tank.tier() == BackpackTier.IRON) {
                assertTrue(recipe.contains("\"minecraft:crafting_shaped\""));
                assertTrue(recipe.contains("\"minecraft:iron_ingot\""));
                if (tank.kind() == FluidKind.OIL) {
                    assertEquals("III/IBI/IKI", pattern(recipe));
                    assertTrue(recipe.contains("\"minecraft:bucket\"") && recipe.contains("\"minecraft:coal_block\""));
                } else {
                    assertEquals("III/ICI/III", pattern(recipe));
                    assertTrue(recipe.contains("\"" + container(tank.kind()) + "\""), id(tank));
                }
            }
        }
    }

    @Test
    void goldTanksAreAnIronTankInARingOfEightGoldIngots() throws IOException {
        for (Tank tank : TankCatalog.all()) {
            if (tank.tier() != BackpackTier.GOLD) {
                continue;
            }
            String recipe = read(DATA.resolve("recipe/" + id(tank) + ".json"));
            assertEquals("AAA/ATA/AAA", pattern(recipe));
            assertTrue(recipe.contains("\"minecraft:gold_ingot\""));
            assertTrue(recipe.contains("\"mcfloatingbackpack:iron_" + tank.kind().id() + "_tank\""), id(tank));
        }
    }

    @Test
    void netheriteTanksAreMadeAtTheSmithingTable() throws IOException {
        for (Tank tank : TankCatalog.all()) {
            if (tank.tier() != BackpackTier.NETHERITE) {
                continue;
            }
            String recipe = read(DATA.resolve("recipe/" + id(tank) + ".json"));
            assertTrue(recipe.contains("\"minecraft:smithing_transform\""));
            assertTrue(recipe.contains("\"minecraft:netherite_upgrade_smithing_template\""));
            assertTrue(recipe.contains("\"minecraft:netherite_ingot\""));
            assertTrue(recipe.contains("\"mcfloatingbackpack:gold_" + tank.kind().id() + "_tank\""), id(tank));
        }
    }

    @Test
    void everyRecipeGivesExactlyOneOfItsTank() throws IOException {
        for (Tank tank : TankCatalog.all()) {
            String recipe = read(DATA.resolve("recipe/" + id(tank) + ".json"));
            assertTrue(recipe.replaceAll("\\s", "").contains("\"result\":{\"count\":1,\"id\":\"mcfloatingbackpack:" + id(tank) + "\"}"), id(tank));
        }
    }

    @Test
    void theTanksAreProtectedFromTheTrashCan() throws IOException {
        String tag = read(DATA.resolve("tags/item/never_trash.json"));
        for (Tank tank : TankCatalog.all()) {
            assertTrue(tag.contains("\"mcfloatingbackpack:" + id(tank) + "\""), id(tank));
        }
    }

    @Test
    void bothLanguagesHaveEveryKey() throws IOException {
        for (String lang : List.of("en_us", "zh_cn")) {
            String text = read(ASSETS.resolve("lang/" + lang + ".json"));
            for (Tank tank : TankCatalog.all()) {
                assertTrue(text.contains("\"item.mcfloatingbackpack." + id(tank) + "\""), lang + " " + id(tank));
            }
            for (FluidKind kind : FluidKind.values()) {
                assertTrue(text.contains("\"fluid.mcfloatingbackpack." + kind.id() + "\""), lang + " " + kind);
            }
            for (BackpackTier tier : BackpackTier.values()) {
                assertTrue(text.contains("\"tier.mcfloatingbackpack." + tier.name().toLowerCase() + "\""), lang + " " + tier);
            }
            for (String key : List.of(
                    "item.mcfloatingbackpack.tank.capacity",
                    "item.mcfloatingbackpack.tank.min_tier",
                    "tooltip.mcfloatingbackpack.tank.amount",
                    "tooltip.mcfloatingbackpack.tank.empty",
                    "message.mcfloatingbackpack.tank.tier",
                    "message.mcfloatingbackpack.tank.duplicate",
                    "message.mcfloatingbackpack.tank.too_small")) {
                assertTrue(text.contains("\"" + key + "\""), lang + " " + key);
            }
        }
    }

    @Test
    void theItemsAreRegisteredFromTheCatalogAndShownInTheCreativeTab() throws IOException {
        String items = read(Path.of("src/main/java/pnnh/mcfloatingbackpack/item/ModItems.java"));
        assertTrue(items.contains("TankCatalog.itemId(tank.kind(), tank.tier())"));
        assertTrue(items.contains("for (DeferredHolder<Item, TankItem> tank : TANKS.values())"));
        int auto = items.indexOf("output.accept(AUTO_PICKUP.get())");
        int tanks = items.indexOf("TANKS.values()", auto);
        int extensions = items.indexOf("CATEGORY_EXTENSIONS.values()", auto);
        assertTrue(auto > 0 && tanks > auto && extensions > tanks, "tanks go after the auto pickup and before the extensions");
        String item = read(Path.of("src/main/java/pnnh/mcfloatingbackpack/item/TankItem.java"));
        assertTrue(item.contains("stacksTo(1)"));
        assertFalse(item.contains("DataComponent"), "a tank carries no data: the liquid belongs to the backpack");
    }
}
