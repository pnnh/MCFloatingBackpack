package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 063: the quick-transfer handler only calls it an error when the total really is inconsistent. */
class NetworkDeliveryLogWiringTest {
    private static final Path PAYLOAD =
            Path.of("src/main/java/pnnh/mcfloatingbackpack/network/BackpackQuickTransferPayload.java");

    @Test
    void absorbedIsDebugAndInconsistentIsError() throws IOException {
        String src = Files.readString(PAYLOAD, StandardCharsets.UTF_8);
        int absorbed = src.indexOf("result.conserved() && result.absorbed() > 0");
        int debug = src.indexOf("LOGGER.debug(", absorbed);
        int inconsistent = src.indexOf("if (!result.conserved())");
        int error = src.indexOf("LOGGER.error(", inconsistent);
        assertTrue(absorbed > 0 && debug > absorbed, "an absorbed stack is logged at DEBUG");
        assertTrue(inconsistent > debug && error > inconsistent, "only an inconsistent total is an ERROR");
        assertTrue(src.contains("changed the item total"), "the ERROR keeps its wording");
    }

    @Test
    void theDeliveryNeverNamesAThirdPartyType() throws IOException {
        String src = Files.readString(
                Path.of("src/main/java/pnnh/mcfloatingbackpack/inventory/OpenContainerDelivery.java"), StandardCharsets.UTF_8);
        assertTrue(!src.contains("appeng") && !src.contains("integratedterminals"), "generic path stays generic");
    }
}
