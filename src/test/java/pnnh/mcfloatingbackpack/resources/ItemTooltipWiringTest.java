package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/** 045: the wiring no pure test can see because the classes need Minecraft, and that nothing else was touched. */
class ItemTooltipWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String renderer() throws IOException {
        return read(SRC.resolve("client/panel/FloatingPanelRenderer.java"));
    }

    private static String method(String source, String signature) {
        int start = source.indexOf(signature);
        assertTrue(start > 0, signature);
        int end = source.indexOf("\n    }\n", start);
        return source.substring(start, end);
    }

    @Test
    void theTooltipIsTheVanillaOneOfTheStackUnderTheMouse() throws IOException {
        String body = method(renderer(), "private static boolean drawItemTooltip");
        assertTrue(body.contains("graphics.renderTooltip(minecraft.font, stack, mouseX, mouseY)"));
        assertTrue(body.contains("view.layout().hit(mouseX, mouseY)"));
        assertTrue(body.contains("view.getStackInSlot(hit.index())"));
        assertTrue(body.contains("view.getToolStack(hit.area(), hit.index())"));
    }

    @Test
    void theRulesAreAskedWithEveryReasonToStayQuiet() throws IOException {
        String body = method(renderer(), "private static boolean drawItemTooltip");
        assertTrue(body.contains("ItemTooltipRules.shouldShow("));
        assertTrue(body.contains("TagPanelState.get().isOpen() || PickupPanelState.get().isOpen()"));
        assertTrue(body.contains("cursorStack(minecraft)"));
        assertTrue(body.contains("PanelDragInput.preview() != null"));
        assertTrue(body.contains("TrashConfirmState.get().isPending()"));
    }

    @Test
    void anItemTooltipTakesTheCellFromTheExtensionRowsTooltipAndOnlyThenTheRowOneIsTried() throws IOException {
        String render = renderer();
        int item = render.indexOf("if (!drawItemTooltip(graphics, minecraft, mouseX, mouseY))");
        int row = render.indexOf("drawRowTooltip(graphics, minecraft, mouseX, mouseY);");
        assertTrue(item > 0 && row > item, "the row tooltip is only the fallback");
        // and both come before the trash panel and the confirmation dialog, like the row tooltip always did
        assertTrue(row < render.indexOf("TrashPanelRenderer.render(graphics, mouseX, mouseY)"));
        assertTrue(row < render.indexOf("TrashConfirmRenderer.render(graphics, mouseX, mouseY)"));
    }

    @Test
    void theRulesHaveNoMinecraftTypes() throws IOException {
        String rules = read(SRC.resolve("client/panel/ItemTooltipRules.java"));
        assertFalse(rules.contains("net.minecraft"));
        assertFalse(rules.contains("net.neoforged"));
    }

    @Test
    void noLanguageKeyPacketOrConfigWasAdded() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            Set<String> keys = new TreeSet<>();
            Matcher m = KEY.matcher(read(LANG.resolve(file)));
            while (m.find()) {
                keys.add(m.group(1));
            }
            assertFalse(keys.stream().anyMatch(k -> k.contains("item.tooltip") || k.contains("itemtooltip")), file);
        }
        assertTrue(read(SRC.resolve("network/BackpackNetwork.java")).contains("versioned(\"18\")"));
        assertEquals(1, Pattern.compile("defineListAllowEmpty").matcher(read(SRC.resolve("compat/CompatConfig.java"))).results().count());
        for (String name : List.of("ItemTooltipRules")) {
            assertFalse(read(SRC.resolve("client/panel/" + name + ".java")).contains("PacketDistributor"));
        }
    }
}
