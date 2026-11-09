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

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * 028: the five backpacks show a flat icon in the GUI only and keep their 3D model everywhere else (the ground included). Plain text and
 * file checks (the test class path has no JSON library and no Minecraft).
 */
class BackpackIconResourcesTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/mcfloatingbackpack");
    private static final List<String> TIERS = List.of("leather", "iron", "gold", "diamond", "netherite");

    private static String read(String relative) throws IOException {
        return Files.readString(ASSETS.resolve(relative), StandardCharsets.UTF_8);
    }

    private static String compact(String json) {
        return json.replaceAll("\\s+", "");
    }

    @Test
    void theEntryModelPicksTheIconForTheGuiOnly() throws IOException {
        for (String tier : TIERS) {
            String name = tier + "_floating_backpack";
            String json = compact(read("models/item/" + name + ".json"));
            assertTrue(json.contains("\"loader\":\"neoforge:separate_transforms\""), name);
            assertTrue(json.contains("\"gui_light\":\"front\""), name);
            assertTrue(json.contains("\"base\":{\"parent\":\"mcfloatingbackpack:item/" + name + "_3d\"}"), name);
            String icon = "{\"parent\":\"mcfloatingbackpack:item/" + name + "_icon\"}";
            assertTrue(json.contains("\"gui\":" + icon), name + " gui");
            // The back layer draws the pack with the FIXED context, the hands and the ground keep the 3D model: none of them may be listed.
            String perspectives = json.substring(json.indexOf("\"perspectives\""));
            for (String forbidden : List.of("\"ground\"", "\"fixed\"", "\"head\"", "\"firstperson_", "\"thirdperson_")) {
                assertFalse(perspectives.contains(forbidden), name + " must not replace " + forbidden);
            }
        }
    }

    @Test
    void theThreeDModelKeepsTheObjAndTheTransformsTheBackLayerNeeds() throws IOException {
        for (String tier : TIERS) {
            String name = tier + "_floating_backpack";
            String json = compact(read("models/item/" + name + "_3d.json"));
            assertTrue(json.contains("\"loader\":\"neoforge:obj\""), name);
            assertTrue(json.contains("\"model\":\"mcfloatingbackpack:models/item/" + tier + "_backpack.obj\""), name);
            assertTrue(json.contains("\"base\":\"mcfloatingbackpack:item/" + name + "\""), name);
            assertTrue(json.contains("\"gui\":{"), name + " keeps its gui display");
            assertTrue(json.contains("\"fixed\":{"), name + " fixed is what the back layer uses");
            assertTrue(json.contains("\"firstperson_righthand\":{"), name);
            assertTrue(json.contains("\"thirdperson_righthand\":{"), name);
        }
    }

    @Test
    void theIconModelIsAFlatItem() throws IOException {
        for (String tier : TIERS) {
            String name = tier + "_floating_backpack";
            String json = compact(read("models/item/" + name + "_icon.json"));
            assertTrue(json.contains("\"parent\":\"minecraft:item/generated\""), name);
            assertTrue(json.contains("\"layer0\":\"mcfloatingbackpack:item/" + name + "_icon\""), name);
        }
    }

    @Test
    void everyIconIs32x32WithATransparentBorderAndSomethingDrawn() throws IOException {
        Set<String> seen = new HashSet<>();
        for (String tier : TIERS) {
            Path file = ASSETS.resolve("textures/item/" + tier + "_floating_backpack_icon.png");
            assertTrue(Files.isRegularFile(file), "missing " + file);
            BufferedImage image = ImageIO.read(file.toFile());
            assertEquals(32, image.getWidth(), tier);
            assertEquals(32, image.getHeight(), tier);
            int opaque = 0;
            int transparent = 0;
            for (int y = 0; y < 32; y++) {
                for (int x = 0; x < 32; x++) {
                    int alpha = image.getRGB(x, y) >>> 24;
                    if (alpha > 0) {
                        opaque++;
                    } else {
                        transparent++;
                    }
                    boolean border = x == 0 || y == 0 || x == 31 || y == 31;
                    if (border) {
                        assertEquals(0, alpha, tier + ": the outer ring must be transparent (atlas bleeding), at " + x + "," + y);
                    }
                }
            }
            assertTrue(opaque > 150, tier + ": barely anything drawn (" + opaque + " pixels)");
            assertTrue(transparent > 100, tier + ": no transparent background");
            seen.add(java.util.Arrays.toString(image.getRGB(0, 0, 32, 32, null, 0, 32)));
        }
        assertEquals(TIERS.size(), seen.size(), "the five icons must all differ");
    }
}
