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

/**
 * 042: the search's language keys, and the wiring that no pure test can see because the classes need Minecraft: the three keyboard
 * events and the mouse press know the search box, the search is reset where a session ends, nothing was added to the network, the
 * configuration or the dependencies, and no texture was added.
 */
class SearchResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final List<String> KEYS = List.of(
            "panel.mcfloatingbackpack.search.tooltip",
            "panel.mcfloatingbackpack.search.clear",
            "panel.mcfloatingbackpack.search.placeholder",
            "panel.mcfloatingbackpack.search.count");
    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static Set<String> keys(String json) {
        Set<String> out = new TreeSet<>();
        Matcher m = KEY.matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    @Test
    void bothLanguagesHaveTheFourKeys() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            Set<String> keys = keys(read(LANG.resolve(file)));
            for (String key : KEYS) {
                assertTrue(keys.contains(key), file + " has " + key);
            }
        }
        assertEquals(keys(read(LANG.resolve("zh_cn.json"))), keys(read(LANG.resolve("en_us.json"))));
    }

    @Test
    void theCountTakesOneNumberByPosition() throws IOException {
        for (String file : new String[] {"zh_cn.json", "en_us.json"}) {
            Matcher m = Pattern.compile("\"panel.mcfloatingbackpack.search.count\"\\s*:\\s*\"([^\"]*)\"").matcher(read(LANG.resolve(file)));
            assertTrue(m.find(), file);
            assertTrue(m.group(1).contains("%s"), file + ": " + m.group(1));
        }
    }

    @Test
    void allThreeKeyboardEventsKnowTheSearchBoxAndEscapeComesAfterTheDialog() throws IOException {
        String events = read(SRC.resolve("client/FloatingPanelEvents.java"));
        String pressed = events.substring(events.indexOf("onScreenKeyPressed(ScreenEvent.KeyPressed.Pre"), events.indexOf("onScreenKeyPressedPost"));
        assertTrue(pressed.contains("SearchState.get().isFocused()"));
        assertTrue(pressed.contains("SearchInput.onKeyPressed"));
        assertTrue(pressed.indexOf("TrashConfirmInput.onEscape") < pressed.indexOf("SearchInput.onKeyPressed"),
                "the confirmation dialog's Esc comes before the search box's");
        String released = events.substring(events.indexOf("onScreenKeyReleased"), events.indexOf("onScreenCharTyped"));
        assertTrue(released.contains("SearchState.get().isFocused()"));
        String typed = events.substring(events.indexOf("onScreenCharTyped"), events.indexOf("onScreenMouseScroll"));
        assertTrue(typed.contains("SearchInput.onCharTyped(event.getCodePoint())"));
    }

    @Test
    void theDeleteKeyDoesNothingWhileTheSearchBoxIsTyping() throws IOException {
        assertTrue(read(SRC.resolve("client/panel/DeleteKeyInput.java")).contains("SearchState.get().isFocused()"));
    }

    @Test
    void aPressGoesToTheSearchBeforeTheSnapshotCloseAndDragButtons() throws IOException {
        String input = read(SRC.resolve("client/panel/FloatingPanelInput.java"));
        int search = input.indexOf("SearchInput.onPress");
        assertTrue(search > 0);
        assertTrue(input.indexOf("SearchInput.loseFocusIfOutside") < search);
        assertTrue(search < input.indexOf("state.hitsSnapshotButton"));
        assertTrue(search < input.indexOf("state.hitsCloseButton"));
        assertTrue(search < input.indexOf("state.hitsDragBar"));
    }

    @Test
    void theSearchIsResetWhereASessionOrTheWorldEnds() throws IOException {
        String events = read(SRC.resolve("client/FloatingPanelEvents.java"));
        assertTrue(events.substring(events.indexOf("public static void closePanel"), events.indexOf("onLoggingIn")).contains("SearchState.get().reset()"));
        assertTrue(events.substring(events.indexOf("onLoggingIn"), events.indexOf("onLoggingOut")).contains("SearchState.get().reset()"));
        assertTrue(events.substring(events.indexOf("onLoggingOut"), events.indexOf("onScreenClosing")).contains("SearchState.get().reset()"));
        assertTrue(read(SRC.resolve("network/BackpackSessionPayload.java")).contains("SearchState.get().reset()"));
    }

    @Test
    void theProtocolIs19AndNoPacketOrConfigWasAdded() throws IOException {
        assertTrue(read(SRC.resolve("network/BackpackNetwork.java")).contains("versioned(\"19\")"));
        try (var files = Files.walk(SRC.resolve("client/panel/search"))) {
            files.filter(f -> f.toString().endsWith(".java")).forEach(f -> {
                try {
                    String text = read(f);
                    assertFalse(text.contains("PacketDistributor"), f + " sends nothing");
                    assertFalse(text.contains("ModConfigSpec"), f + " adds no config");
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
        }
    }

    @Test
    void noTextureWasAddedForTheMagnifier() throws IOException {
        try (var files = Files.walk(Path.of("src/main/resources/assets/mcfloatingbackpack/textures"))) {
            assertTrue(files.noneMatch(f -> f.toString().contains("search") || f.toString().contains("magnif")));
        }
    }
}
