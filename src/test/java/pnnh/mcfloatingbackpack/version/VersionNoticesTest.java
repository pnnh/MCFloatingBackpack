package pnnh.mcfloatingbackpack.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.version.ModVersions.Result;
import pnnh.mcfloatingbackpack.version.VersionNotices.Notice;

class VersionNoticesTest {
    @Test
    void eachResultHasItsNotice() {
        assertEquals(Notice.SILENT, VersionNotices.noticeFor(Result.MATCH));
        assertEquals(Notice.OLDER, VersionNotices.noticeFor(Result.CLIENT_OLDER));
        assertEquals(Notice.NEWER, VersionNotices.noticeFor(Result.CLIENT_NEWER));
        assertEquals(Notice.UNKNOWN, VersionNotices.noticeFor(Result.UNCOMPARABLE));
    }

    @Test
    void onlyAMatchIsSilent() {
        for (Result result : Result.values()) {
            assertEquals(result == Result.MATCH, VersionNotices.noticeFor(result) == Notice.SILENT, result.name());
        }
    }

    @Test
    void theTextForAnOldClientHasTheServerVersionAndBothLanguages() {
        String text = VersionNotices.legacyClientText("0.2.0");
        String[] lines = text.split("\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].contains("0.2.0") && lines[0].contains("备份") && lines[0].contains("升级"), lines[0]);
        assertTrue(lines[1].contains("0.2.0") && lines[1].contains("back up") && lines[1].contains("update"), lines[1]);
    }

    @Test
    void aMissingServerVersionReadsUnknown() {
        for (String missing : new String[] {null, "", "  "}) {
            String text = VersionNotices.legacyClientText(missing);
            assertTrue(text.contains("未知") && text.contains("unknown"), String.valueOf(missing));
        }
    }
}
