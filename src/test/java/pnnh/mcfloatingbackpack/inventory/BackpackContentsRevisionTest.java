package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 040: the data revision is stored on disk only, as an optional field; the wire codec does not carry it (it would change the format
 * of every backpack stack that is synchronised, which needs a new protocol number). Plain text checks: the codecs need Minecraft's
 * registries and cannot be run here.
 */
class BackpackContentsRevisionTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theRevisionIsAnOptionalFieldOfTheDiskCodecWithZeroAsTheDefault() throws IOException {
        String contents = read("inventory/BackpackContents.java");
        assertTrue(contents.contains(".optionalFieldOf(\"data_revision\", 0)"));
        assertTrue(contents.contains(".forGetter(BackpackContents::dataRevision))"));
    }

    @Test
    void theWireCodecDoesNotCarryTheRevision() throws IOException {
        String contents = read("inventory/BackpackContents.java");
        int start = contents.indexOf("public static final StreamCodec<RegistryFriendlyByteBuf, BackpackContents> STREAM_CODEC");
        int end = contents.indexOf("public static BackpackContents empty(");
        assertTrue(start > 0 && end > start);
        String wire = contents.substring(start, end);
        assertFalse(wire.contains("dataRevision"), "the wire format must not change (protocol 17)");
        assertFalse(wire.contains("data_revision"));
    }

    @Test
    void everySaveStampsTheCurrentRevisionUnlessADevelopmentSwitchSaysOtherwise() throws IOException {
        String inventory = read("inventory/BackpackInventory.java");
        assertTrue(inventory.contains("VersionDebug.stampRevisionOverride()"));
        assertTrue(inventory.contains(".withDataRevision(stamp != null ? stamp : pnnh.mcfloatingbackpack.inventory.revision.DataRevision.CURRENT)"));
    }

    @Test
    void theRevisionSurvivesCopyingFittingAndUpgrading() throws IOException {
        String contents = read("inventory/BackpackContents.java");
        assertTrue(contents.contains("normalize(items, cursor, tools, tags, trash, pickupBlacklist, rows, fluids, dataRevision)"));
        assertTrue(contents.contains("fit(items, cursor, tools, tags, trash, pickupBlacklist, rows, fluids, capacity).withDataRevision(dataRevision)"));
        String recipe = read("inventory/recipe/BackpackUpgradeRecipe.java");
        assertTrue(recipe.contains(".withDataRevision(current.dataRevision())"));
    }
}
