package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/** 054: the mobile crafting table's model, texture, recipe and language keys. Plain text and pixel checks (no JSON library). */
class MobileCraftingTableResourcesTest {
    private static final Path ROOT = Path.of("src/main/resources");

    private static String read(String relative) throws IOException {
        return Files.readString(ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

    private static TreeSet<String> keys(String json) {
        TreeSet<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"\\s*:").matcher(json);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    @Test
    void theModelIsAFlatOneOfTheTexture() throws IOException {
        String model = read("assets/mcfloatingbackpack/models/item/mobile_crafting_table.json").replaceAll("\\s+", "");
        assertTrue(model.contains("\"parent\":\"minecraft:item/generated\""));
        assertTrue(model.contains("\"layer0\":\"mcfloatingbackpack:item/mobile_crafting_table\""));
    }

    @Test
    void theTextureIs16x16RgbaWithRealContentAndIsNotAnotherIcon() throws IOException {
        Path path = ROOT.resolve("assets/mcfloatingbackpack/textures/item/mobile_crafting_table.png");
        byte[] png = Files.readAllBytes(path);
        assertEquals(16, java.nio.ByteBuffer.wrap(png, 16, 4).getInt());
        assertEquals(16, java.nio.ByteBuffer.wrap(png, 20, 4).getInt());
        assertEquals(6, png[25], "RGBA");
        BufferedImage image = ImageIO.read(path.toFile());
        List<Integer> mine = pixels(image);
        assertTrue(mine.stream().filter(argb -> (argb >>> 24) == 255).count() >= 120);
        try (Stream<Path> files = Files.list(ROOT.resolve("assets/mcfloatingbackpack/textures/item"))) {
            for (Path other : files.filter(f -> f.toString().endsWith(".png") && !f.equals(path)).toList()) {
                BufferedImage o = ImageIO.read(other.toFile());
                assertFalse(o != null && pixels(o).equals(mine), other + " is the same picture");
            }
        }
    }

    private static List<Integer> pixels(BufferedImage image) {
        java.util.ArrayList<Integer> out = new java.util.ArrayList<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                out.add(image.getRGB(x, y));
            }
        }
        return out;
    }

    @Test
    void theRecipeIsTheOneAgreed() throws IOException {
        String json = read("data/mcfloatingbackpack/recipe/mobile_crafting_table.json").replaceAll("\\s+", "");
        assertTrue(json.contains("\"type\":\"minecraft:crafting_shaped\""));
        assertTrue(json.contains("\"pattern\":[\"IDI\",\"ATB\",\"ICI\"]"));
        for (String entry : new String[] {"\"I\":{\"item\":\"minecraft:iron_ingot\"}", "\"D\":{\"item\":\"minecraft:diamond\"}",
                "\"A\":{\"item\":\"minecraft:iron_pickaxe\"}", "\"B\":{\"item\":\"minecraft:iron_axe\"}",
                "\"T\":{\"item\":\"minecraft:crafting_table\"}", "\"C\":{\"item\":\"minecraft:chest\"}"}) {
            assertTrue(json.contains(entry), entry);
        }
        assertTrue(json.contains("\"result\":{\"count\":1,\"id\":\"mcfloatingbackpack:mobile_crafting_table\"}"));
    }

    @Test
    void noOtherRecipeHasTheSameCells() throws IOException {
        Set<String> seen = new HashSet<>();
        try (Stream<Path> files = Files.list(ROOT.resolve("data/mcfloatingbackpack/recipe"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                String json = Files.readString(file, StandardCharsets.UTF_8).replaceAll("\\s+", "");
                Matcher pattern = Pattern.compile("\"pattern\":\\[([^\\]]*)\\]").matcher(json);
                if (!pattern.find()) {
                    continue;
                }
                StringBuilder cells = new StringBuilder();
                Matcher keyed = Pattern.compile("\"([A-Z])\":\\{\"(?:item|tag)\":\"([^\"]+)\"\\}").matcher(json);
                java.util.Map<String, String> key = new java.util.TreeMap<>();
                while (keyed.find()) {
                    key.put(keyed.group(1), keyed.group(2));
                }
                for (char c : pattern.group(1).toCharArray()) {
                    if (Character.isLetter(c)) {
                        cells.append(key.get(String.valueOf(c))).append(';');
                    } else if (c == ',') {
                        cells.append('/');
                    }
                }
                assertTrue(seen.add(cells.toString()) || json.contains("smithing"), file + " has the same cells as another recipe");
            }
        }
    }

    @Test
    void bothLanguagesHaveEveryKeyAndTheSameKeys() throws IOException {
        String en = read("assets/mcfloatingbackpack/lang/en_us.json");
        String zh = read("assets/mcfloatingbackpack/lang/zh_cn.json");
        assertEquals(keys(en), keys(zh));
        for (String key : new String[] {"item.mcfloatingbackpack.mobile_crafting_table", "item.mcfloatingbackpack.mobile_crafting_table.tooltip",
                "message.mcfloatingbackpack.limit.mobile_crafting_table"}) {
            assertTrue(keys(en).contains(key), "en_us missing " + key);
            assertTrue(keys(zh).contains(key), "zh_cn missing " + key);
        }
        assertTrue(zh.contains("\"item.mcfloatingbackpack.mobile_crafting_table\": \"移动工作台\""));
        assertTrue(en.contains("\"item.mcfloatingbackpack.mobile_crafting_table\": \"Mobile Crafting Table\""));
    }

    @Test
    void theToolIsProtectedFromTheTrashAndIsInTheCreativeTabAfterTheMobileJukebox() throws IOException {
        assertTrue(read("data/mcfloatingbackpack/tags/item/never_trash.json").contains("\"mcfloatingbackpack:mobile_crafting_table\""));
        String items = Files.readString(Path.of("src/main/java/pnnh/mcfloatingbackpack/item/ModItems.java"), StandardCharsets.UTF_8);
        assertTrue(items.contains("ITEMS.register(\"mobile_crafting_table\""));
        assertTrue(items.indexOf("output.accept(MOBILE_JUKEBOX.get())") < items.indexOf("output.accept(MOBILE_CRAFTING_TABLE.get())"));
        assertTrue(items.indexOf("output.accept(MOBILE_CRAFTING_TABLE.get())") < items.indexOf("TANKS.values()"));
    }
}
