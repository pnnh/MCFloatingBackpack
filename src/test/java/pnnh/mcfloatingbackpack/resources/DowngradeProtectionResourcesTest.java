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
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * 040: the language keys of the protection messages, and the wiring no pure test can see (the gate and the recipe need Minecraft):
 * the protection comes before anything is written, only an explicit open can confirm, the recipe reads the revision, and nothing was
 * added that the feature must not add (a config, a command, a change of the protocol).
 */
class DowngradeProtectionResourcesTest {
    private static final Path LANG = Path.of("src/main/resources/assets/mcfloatingbackpack/lang");
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");
    private static final Pattern KEY = Pattern.compile("\"([^\"]+)\"\\s*:");

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private static String src(String relative) throws IOException {
        return read(SRC.resolve(relative));
    }

    private static Set<String> keys(String json) {
        Set<String> out = new TreeSet<>();
        Matcher m = KEY.matcher(json);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String value(String json, String key) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"").matcher(json);
        assertTrue(m.find(), key);
        return m.group(1);
    }

    @Test
    void bothLanguagesHaveTheTwoMessages() throws IOException {
        String zh = read(LANG.resolve("zh_cn.json"));
        String en = read(LANG.resolve("en_us.json"));
        assertEquals(keys(zh), keys(en), "the two language files must have the same keys");
        for (String text : new String[] {value(zh, "message.mcfloatingbackpack.revision.protected"), value(en, "message.mcfloatingbackpack.revision.protected")}) {
            assertTrue(text.contains("%1$s") && text.contains("%2$s") && text.contains("60"), text);
        }
        assertTrue(value(zh, "message.mcfloatingbackpack.revision.protected").contains("备份"));
        assertTrue(value(en, "message.mcfloatingbackpack.revision.protected").contains("back up"));
        assertTrue(value(zh, "message.mcfloatingbackpack.revision.accepted").contains("%2$s"));
        assertTrue(value(en, "message.mcfloatingbackpack.revision.accepted").contains("%2$s"));
    }

    @Test
    void theProtectionComesBeforeTheBackpackIsGivenAnIdentityOrSnapshotted() throws IOException {
        String gate = src("inventory/identity/BackpackGate.java");
        int acquire = gate.indexOf("public static Optional<BackpackInventory> acquire(ServerPlayer player, BackpackRef ref)");
        assertTrue(acquire > 0);
        String body = gate.substring(acquire);
        int protectedCheck = body.indexOf("if (isProtected(stack))");
        int identity = body.indexOf("new BackpackIdentity(");
        int snapshot = body.indexOf("service.noteUse(");
        assertTrue(protectedCheck > 0 && identity > protectedCheck && snapshot > protectedCheck,
                "the check must come before anything is written or noted");
    }

    @Test
    void onlyAnExplicitOpenCanConfirm() throws IOException {
        String gate = src("inventory/identity/BackpackGate.java");
        // the windows are consumed in acquireForOpen only
        assertEquals(1, count(gate, "WINDOWS.consume("));
        int open = gate.indexOf("public static Optional<BackpackInventory> acquireForOpen(");
        int consume = gate.indexOf("WINDOWS.consume(");
        assertTrue(open > 0 && consume > open);
        // and acquireForOpen is called by the open request and by nothing else
        try (Stream<Path> files = Files.walk(SRC)) {
            List<String> callers = files.filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return read(p).contains("acquireForOpen(") && !p.endsWith("BackpackGate.java");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .map(p -> p.getFileName().toString())
                    .toList();
            assertEquals(List.of("BackpackOpenRequestPayload.java"), callers);
        }
    }

    @Test
    void theAutoPickupSkipsAndOnlyLogs() throws IOException {
        String pickup = src("inventory/pickup/AutoPickupEvents.java");
        assertTrue(pickup.contains("BackpackGate.noteAutoPickupSkipped(player, ref);"));
        String gate = src("inventory/identity/BackpackGate.java");
        int note = gate.indexOf("public static void noteAutoPickupSkipped(");
        assertTrue(note > 0);
        String body = gate.substring(note, gate.indexOf("/** What identifies the backpack for a confirmation", note));
        assertFalse(body.contains("sendSystemMessage") || body.contains("tell("), "an auto pickup must not chat");
    }

    @Test
    void theUpgradeRecipeDoesNotMatchAProtectedBackpack() throws IOException {
        String recipe = src("inventory/recipe/BackpackUpgradeRecipe.java");
        assertTrue(recipe.contains("&& !isProtected(input.base())"));
        assertTrue(recipe.contains("DataRevision.isNewer(contents.dataRevision())"));
    }

    @Test
    void theConfirmationStampsTheBackpackAtOnce() throws IOException {
        String gate = src("inventory/identity/BackpackGate.java");
        assertTrue(gate.contains(".withDataRevision(DataRevision.CURRENT)"));
        assertTrue(gate.contains("ref.setStack(player, stamped);"));
    }

    @Test
    void theProtocolAndTheOtherPayloadsAreUntouchedAndNothingWasAddedToConfigOrCommands() throws IOException {
        String network = src("network/BackpackNetwork.java");
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"19\")"));
        assertTrue(network.contains("event.registrar(MCFloatingBackpack.MOD_ID).versioned(\"1\").optional()"));
        assertFalse(src("diag/DiagConfig.java").toLowerCase().contains("revision"));
        assertFalse(src("compat/CompatConfig.java").toLowerCase().contains("revision"));
        assertFalse(src("snapshot/SnapshotCommands.java").contains("literal(\"revision"));
        // the revision never reaches the wire
        assertTrue(src("inventory/BackpackContents.java").contains("optionalFieldOf(\"data_revision\", 0)"));
    }

    private static int count(String text, String part) {
        int n = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            n++;
        }
        return n;
    }
}
