package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * 036: the flat GUI icons of the backpacks have a margin of 3 to 5 pixels of 32 around the bag, so beside the tool icons (trash can,
 * tag manager: 16 pixels tall) they looked small, as if the bag were the tool. Each icon model enlarges the GUI display so that the bag
 * is about as tall as those; this checks the scale is there and stays in a sensible range (the bag must still fit the 18-pixel slot).
 */
class BackpackIconSizeTest {
    private static final Path MODELS = Path.of("src/main/resources/assets/mcfloatingbackpack/models/item");
    private static final Pattern SCALE = Pattern.compile("\"gui\"\\s*:\\s*\\{\\s*\"scale\"\\s*:\\s*\\[\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*,\\s*([0-9.]+)\\s*\\]");

    @Test
    void everyTierIconIsEnlargedInTheGui() throws IOException {
        for (String tier : new String[] {"leather", "iron", "gold", "diamond", "netherite"}) {
            String json = Files.readString(MODELS.resolve(tier + "_floating_backpack_icon.json"), StandardCharsets.UTF_8);
            Matcher m = SCALE.matcher(json);
            assertTrue(m.find(), tier + " has a gui scale");
            double x = Double.parseDouble(m.group(1));
            assertTrue(x > 1.0 && x <= 1.25, tier + " scale " + x);
            assertTrue(x == Double.parseDouble(m.group(2)), tier + " scales evenly");
        }
    }
}
