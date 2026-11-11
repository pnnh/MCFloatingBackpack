package pnnh.mcfloatingbackpack.worldmarker;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** 041: what the refusal and the log lines must say. */
class WorldGuardTextsTest {
    private static final String PATH = "/saves/New World/mcfloatingbackpack/world_marker.properties";

    @Test
    void theRefusalSaysWhatAPlayerNeedsToActOnIt_inBothLanguages() {
        String detail = WorldGuardTexts.refuseDetail(7, "0.3.0", 1, "0.1.0", PATH);
        for (String needed : new String[] {"7", "0.3.0", "1", "0.1.0", "-Dmcfloatingbackpack.acceptDowngrade=true", PATH, "back up", "Update",
                "data_revision", "[English]", "[中文]", "备份", "升级", "数据修订", "加 JVM 参数", "删除"}) {
            assertTrue(detail.contains(needed), "missing: " + needed);
        }
    }

    @Test
    void aMarkerWithoutAVersionSaysUnknown() {
        String detail = WorldGuardTexts.refuseDetail(7, null, 1, null, PATH);
        assertTrue(detail.contains("unknown") && detail.contains("未知"));
    }

    @Test
    void theTitleNamesBothRevisionsInBothLanguages() {
        String title = WorldGuardTexts.crashTitle(7, 1);
        assertTrue(title.contains("7 > 1") && title.contains("refusing") && title.contains("拒绝加载"));
    }

    @Test
    void theLogLinesNameTheirSubject() {
        assertTrue(WorldGuardTexts.acceptedWarn(7, 1).contains("7") && WorldGuardTexts.acceptedWarn(7, 1).contains("accepted"));
        assertTrue(WorldGuardTexts.corruptWarn(PATH, "boom").contains(PATH) && WorldGuardTexts.corruptWarn(PATH, "boom").contains("boom"));
        assertTrue(WorldGuardTexts.writeFailedWarn(PATH, "denied").contains("denied"));
        assertTrue(WorldGuardTexts.markerSetInfo(1, null).contains("none") && WorldGuardTexts.markerSetInfo(1, null).contains("无"));
        assertTrue(WorldGuardTexts.markerSetInfo(2, 1).contains("was 1") && WorldGuardTexts.markerSetInfo(2, 1).contains("原为 1"));
    }
}
