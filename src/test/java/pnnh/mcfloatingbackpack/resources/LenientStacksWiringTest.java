package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 056: every list of stacks a backpack keeps is read with the place-keeping codec, so a removed item never moves the stacks after it. */
class LenientStacksWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theMainAreaTheToolAreasTheRowsAndTheTrashUseIt() throws IOException {
        assertTrue(read("inventory/BackpackContents.java").contains("LenientStacks.LIST\n                            .fieldOf(\"items\")"));
        String tools = read("inventory/ToolAreaContents.java");
        for (String area : new String[] {"left", "right", "bottom"}) {
            assertTrue(tools.contains("LenientStacks.LIST.optionalFieldOf(\"" + area + "\""), area);
        }
        assertTrue(read("inventory/extension/ExtensionRowsCodecs.java").contains("LenientStacks.LIST.fieldOf(\"cells\")"));
        assertTrue(read("inventory/trash/TrashContents.java").contains("LenientStacks.LIST.xmap("));
    }

    @Test
    void noStackListOfTheBackpackIsReadWithTheOrdinaryListCodecAnyMore() throws IOException {
        for (String file : new String[] {"inventory/BackpackContents.java", "inventory/ToolAreaContents.java",
                "inventory/extension/ExtensionRowsCodecs.java", "inventory/trash/TrashContents.java"}) {
            assertFalse(read(file).contains("OPTIONAL_CODEC.listOf()"), file);
            assertFalse(read(file).replace("OPTIONAL_CODEC\n                            .optionalFieldOf", "").contains("OPTIONAL_CODEC\n                            .listOf()"), file);
        }
    }
}
