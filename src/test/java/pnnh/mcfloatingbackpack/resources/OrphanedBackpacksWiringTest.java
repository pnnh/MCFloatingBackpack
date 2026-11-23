package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/** 062: giving back the backpacks Accessories dropped is wired as the plan says. Source text checks. */
class OrphanedBackpacksWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theTwoMessagesAreInBothLanguages() throws IOException {
        for (String lang : new String[] {"en_us", "zh_cn"}) {
            JsonObject names = JsonParser.parseString(Files.readString(LANG.resolve(lang + ".json"), StandardCharsets.UTF_8)).getAsJsonObject();
            for (String key : new String[] {"message.mcfloatingbackpack.orphaned.returned", "message.mcfloatingbackpack.orphaned.dropped"}) {
                assertTrue(names.has(key), lang + " " + key);
                assertTrue(names.get(key).getAsString().contains("%1$s"), lang + " " + key + " says how many");
            }
            assertTrue(names.get("message.mcfloatingbackpack.orphaned.dropped").getAsString().contains("%2$s"), lang + ": says how many lie at the feet");
        }
    }

    @Test
    void theEventsAreOnlyHungInTheAccessoriesBranchOfTheConstructor() throws IOException {
        String main = read("MCFloatingBackpack.java");
        int branch = main.indexOf("if (ModList.get().isLoaded(\"accessories\"))");
        int nextBranch = main.indexOf("// 059: the server's levels for the mods");
        assertTrue(branch > 0 && nextBranch > branch);
        String inBranch = main.substring(branch, nextBranch);
        for (String call : new String[] {"OrphanedBackpacks.onPlayerDataRead(", "OrphanedBackpacks.onLogin(", "OrphanedBackpacks.onLogout(",
            "OrphanedBackpacks.tick(", "OrphanedBackpacks.clear()"}) {
            assertTrue(inBranch.contains(call), call);
            assertEquals(1, main.split(java.util.regex.Pattern.quote(call), -1).length - 1, call + " is called from one place");
        }
        assertTrue(inBranch.contains("PlayerEvent.LoadFromFile") && inBranch.contains("event.getPlayerUUID() + \".dat\""));
    }

    @Test
    void theCheckComesAfterAccessoriesOwnGiveBackAndAlsoWhenThePlayerLeaves() throws IOException {
        String orphaned = read("compat/accessories/OrphanedBackpacks.java");
        assertTrue(orphaned.contains("static final int DELAY_TICKS = 20;"), "Accessories gives back its own in its first tick");
        assertTrue(orphaned.contains("public static void onLogout(ServerPlayer player) {\n        COUNTDOWN.remove(player.getUUID());\n        process(player);"),
                "the check is made before the data is saved");
        assertTrue(orphaned.contains("ServerCompatLevels.specificAdaptation(\"accessories\")"), "only at FULL");
        assertTrue(orphaned.contains("catch (RuntimeException | LinkageError e)"), "whatever goes wrong changes nothing");
        assertTrue(orphaned.contains("new ContainerMenuAccess(player).fallback()"), "inventory first, then the feet");
        assertTrue(orphaned.contains("OrphanRules.decide("));
        assertTrue(orphaned.contains("PENDING.remove(player.getUUID())"), "the check is made once");
    }

    @Test
    void theOnlyOnesThatTouchAccessoriesTypesAreInTheAccessoriesPackage() throws IOException {
        assertFalse(read("compat/OrphanRules.java").contains("io.wispforest") || read("compat/OrphanRules.java").contains("net.minecraft"));
        assertFalse(read("compat/accessories/OrphanedBackpackScan.java").contains("io.wispforest"), "the file is read as plain NBT");
        assertTrue(read("compat/accessories/OrphanedBackpacks.java").contains("io.wispforest.accessories.api.AccessoriesCapability"));
    }

    @Test
    void theScanReadsTheLayoutOfAccessoriesDataAndDoesNotTrustIt() throws IOException {
        String scan = read("compat/accessories/OrphanedBackpackScan.java");
        for (String name : new String[] {"neoforge:attachments", "accessories:inventory_holder", "accessories_containers", "\"items\"", "\"cosmetics\"",
            "mcfloatingbackpack:backpack_identity", "\"uuid\""}) {
            assertTrue(scan.contains(name), name);
        }
        assertTrue(scan.contains("catch (IOException | RuntimeException e)") && scan.contains("catch (RuntimeException e)"), "a layout that is not as expected reads as nothing");
        for (String tier : new String[] {"leather", "iron", "gold", "diamond", "netherite"}) {
            assertTrue(scan.contains("mcfloatingbackpack:" + tier + "_floating_backpack"), tier);
        }
    }
}
