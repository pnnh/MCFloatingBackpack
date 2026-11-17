package pnnh.mcfloatingbackpack.client.snapshot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenState.Status;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;

/** 031: result codes become a language key and arguments. */
class SnapshotResultTextTest {
    @Test
    void everyCodeHasItsOwnKey() {
        Set<String> keys = new HashSet<>();
        for (ResultCode code : ResultCode.values()) {
            String key = SnapshotResultText.key(code);
            assertTrue(key.startsWith("gui.mcfloatingbackpack.snapshot.result."), key);
            assertTrue(keys.add(key), "duplicate " + key);
        }
        assertEquals("gui.mcfloatingbackpack.snapshot.result.trim_done", SnapshotResultText.key(ResultCode.TRIM_DONE));
    }

    @Test
    void theArgumentsAreAsManyAsTheTextTakes() {
        for (ResultCode code : ResultCode.values()) {
            Status status = new Status(code, "NF56", "Dev", 3, 4);
            Object[] args = SnapshotResultText.args(status);
            assertEquals(SnapshotResultText.placeholders(status), args.length, code.name());
        }
    }

    /** 057: a restore that lost stacks has its own text with the number as a third argument. */
    @Test
    void aRestoreThatLostStacksHasItsOwnKeyAndThreeArguments() {
        Status lost = new Status(ResultCode.RESTORED, "NF56", "Dev", 4, 0);
        assertEquals("gui.mcfloatingbackpack.snapshot.result.restored_lost", SnapshotResultText.key(lost));
        assertEquals(java.util.List.of("Dev", "NF56", 4), java.util.List.of(SnapshotResultText.args(lost)));
        Status whole = new Status(ResultCode.RESTORED, "NF56", "Dev", 0, 0);
        assertEquals("gui.mcfloatingbackpack.snapshot.result.restored", SnapshotResultText.key(whole));
        assertEquals(2, SnapshotResultText.placeholders(whole));
        assertEquals(3, SnapshotResultText.placeholders(lost));
    }

    @Test
    void theArgumentsComeInTheOrderTheEnglishTextsTakeThem() {
        assertEquals(java.util.List.of("Dev", "NF56"), java.util.List.of(SnapshotResultText.args(new Status(ResultCode.RESTORED, "NF56", "Dev", 0, 0))));
        assertEquals(java.util.List.of(2, "NF56"), java.util.List.of(SnapshotResultText.args(new Status(ResultCode.DELETED_ONE, "NF56", "", 2, 0))));
        assertEquals(java.util.List.of(5, "NF56"), java.util.List.of(SnapshotResultText.args(new Status(ResultCode.DELETED_ALL, "NF56", "", 5, 0))));
        assertEquals(java.util.List.of(3, 9), java.util.List.of(SnapshotResultText.args(new Status(ResultCode.TRIM_PREVIEWED, "", "", 3, 9))));
        assertEquals(java.util.List.of(9, 3), java.util.List.of(SnapshotResultText.args(new Status(ResultCode.TRIM_DONE, "", "", 9, 3))));
    }
}
