package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 053: what no pure test can see because the classes need Minecraft: where the page is hooked into the panel, that the sound is in the ear and
 * counted by its length, that nothing reaches into the sound engine, where the playlist is read from, the two places that ask the placement
 * rule, and that nothing was added to the network, the configuration or the build.
 */
class JukeboxWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path CLIENT = SRC.resolve("client");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    @Test
    void thePageIsHookedInWhereTheTrashCanPageIs() throws IOException {
        String input = read(CLIENT.resolve("panel/FloatingPanelInput.java"));
        assertTrue(input.contains("JukeboxPanelInput.onPress(button, mouseX, mouseY)"));
        assertTrue(input.indexOf("TrashPanelInput.onPress(button, mouseX, mouseY)") < input.indexOf("JukeboxPanelInput.onPress(button, mouseX, mouseY)"));
        assertTrue(input.indexOf("JukeboxPanelInput.onPress(button, mouseX, mouseY)") < input.indexOf("SearchInput.onPress(state, mouseX, mouseY)"),
                "before the panel's own hit tests");
        assertTrue(input.contains("JukeboxPanelState.get().toggle()"));
        // opening one page closes the other
        int trash = input.indexOf("TrashPanelState.get().toggle()");
        assertTrue(input.substring(Math.max(0, trash - 120), trash).contains("JukeboxPanelState.get().collapse()"));
        int jukebox = input.indexOf("JukeboxPanelState.get().toggle()");
        assertTrue(input.substring(Math.max(0, jukebox - 120), jukebox).contains("TrashPanelState.get().collapse()"));
        assertTrue(input.contains("toolHit.area() == PanelArea.RIGHT"));
        assertTrue(input.contains("ModItems.MOBILE_JUKEBOX.get()"));
        assertTrue(read(CLIENT.resolve("panel/FloatingPanelRenderer.java")).contains("JukeboxPanelRenderer.render(graphics, mouseX, mouseY)"));
    }

    @Test
    void theEventsCollapseResetAndDriveThePlayback() throws IOException {
        String events = read(CLIENT.resolve("FloatingPanelEvents.java"));
        assertTrue(events.contains("JukeboxPlayback.tick(minecraft)"));
        assertTrue(events.contains("!ClientBackpackView.get().hasMobileJukebox()"));
        assertTrue(events.contains("JukeboxPanelInput.covers(mouseX, mouseY)"), "the wheel over the page is taken");
        int close = events.indexOf("public static void closePanel()");
        String closeBody = events.substring(close, events.indexOf("@SubscribeEvent", close));
        assertTrue(closeBody.contains("JukeboxPanelState.get().collapse()"));
        assertFalse(closeBody.contains("JukeboxPlayback"), "closing the panel does not stop the music");
        assertEquals(2, Pattern.compile("JukeboxPlayback\\.reset\\(\\)").matcher(events).results().count(), "logging in and logging out");
    }

    @Test
    void theSoundIsInTheEarOfTheRecordsCategoryAndIsStartedTheWayAVanillaJukeboxStarts() throws IOException {
        String sound = read(CLIENT.resolve("jukebox/MobileJukeboxSound.java"));
        assertTrue(sound.contains("SoundSource.RECORDS"));
        assertTrue(sound.contains("this.relative = true"));
        assertTrue(sound.contains("Attenuation.NONE"));
        assertTrue(sound.contains("this.looping = false"));
        String playback = read(CLIENT.resolve("jukebox/JukeboxPlayback.java"));
        assertTrue(playback.contains("getMusicManager().stopPlaying()"));
        assertTrue(playback.contains("gui.setNowPlaying(song.description())"));
        assertTrue(playback.contains("song.lengthInSeconds()"), "the length of the song's data counts the track out");
        assertTrue(playback.contains("minecraft.isPaused()"), "and only while the game is not paused");
    }

    @Test
    void nothingReachesIntoTheSoundEngine() throws IOException {
        try (Stream<Path> files = Files.walk(CLIENT)) {
            for (Path file : files.filter(f -> f.toString().contains("jukebox") && f.toString().endsWith(".java")).toList()) {
                String text = read(file);
                for (String banned : new String[] {"setAccessible", "getDeclaredField", "getDeclaredMethod", "instanceToChannel", "ChannelAccess", "AccessTransformer"}) {
                    assertFalse(text.contains(banned), file + " has " + banned);
                }
            }
        }
        assertFalse(Files.exists(Path.of("src/main/resources/META-INF/accesstransformer.cfg")));
        assertFalse(Files.exists(Path.of("src/main/resources/mcfloatingbackpack.mixins.json")));
    }

    @Test
    void thePlaylistIsReadFromTheBackpackStackAndNotFromThePanelsView() throws IOException {
        String playback = read(CLIENT.resolve("jukebox/JukeboxPlayback.java"));
        assertTrue(playback.contains("BackpackRef.resolve(player)"));
        assertTrue(playback.contains("contents.rows().visible()"), "only the visible rows");
        assertTrue(playback.contains("contents.tools().right()"));
        assertTrue(playback.contains("JukeboxSong.fromStack("));
        assertFalse(playback.contains("ClientBackpackView"), "the view is empty while the panel is closed");
    }

    @Test
    void theTwoPlacesThatPutItemsIntoAToolAreaAskTheRightAreaRule() throws IOException {
        for (String file : new String[] {"network/BackpackClickPayload.java", "client/panel/FloatingPanelInput.java"}) {
            String source = read(SRC.resolve(file));
            assertTrue(source.contains("ToolSingleRules.check("), file);
            assertTrue(source.contains("PanelArea.RIGHT"), file);
            assertTrue(source.contains("message.mcfloatingbackpack.limit.mobile_jukebox"), file);
        }
    }

    @Test
    void nothingWasAddedToTheNetworkTheDataTheConfigOrTheBuildAndTheRevisionIsFour() throws IOException {
        String network = read(SRC.resolve("network/BackpackNetwork.java"));
        assertTrue(network.contains("versioned(\"20\")"), "the protocol is 20 since 054");
        assertFalse(network.contains("Jukebox"), "no packet for the player");
        assertTrue(read(SRC.resolve("inventory/revision/DataRevision.java")).contains("CURRENT = 5;"));
        assertEquals(1, Pattern.compile("defineListAllowEmpty").matcher(read(SRC.resolve("compat/CompatConfig.java"))).results().count());
        for (String dir : new String[] {"jukebox", "client/jukebox", "client/panel/jukebox"}) {
            try (Stream<Path> files = Files.walk(SRC.resolve(dir))) {
                for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                    assertFalse(read(file).contains("CustomPacketPayload"), file.toString());
                }
            }
        }
    }

    @Test
    void thePureClassesHaveNoMinecraftTypes() throws IOException {
        for (String file : new String[] {"jukebox/PlaylistReader.java", "jukebox/JukeboxPlayer.java", "inventory/ToolSingleRules.java",
                "client/panel/jukebox/JukeboxPanelLayout.java", "client/panel/jukebox/JukeboxPanelState.java"}) {
            String source = read(SRC.resolve(file));
            assertFalse(source.contains("net.minecraft"), file);
            assertFalse(source.contains("net.neoforged"), file);
        }
    }
}
