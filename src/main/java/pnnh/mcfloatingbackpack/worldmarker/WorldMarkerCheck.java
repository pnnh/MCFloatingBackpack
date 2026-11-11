package pnnh.mcfloatingbackpack.worldmarker;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import pnnh.mcfloatingbackpack.worldmarker.WorldMarkerGuard.Decision;

/**
 * 041: the whole start-up check of one world, from reading its marker to writing it or refusing: {@link WorldMarkerEvents} only gives it
 * the world's path and logs what it returns. Kept free of Minecraft types so the flow (above all that a refusal and an accepted risk
 * write nothing) is tested in a temporary directory.
 */
public final class WorldMarkerCheck {
    public enum Severity { INFO, WARN, ERROR }

    public record Line(Severity severity, String text) {
    }

    /**
     * @param decision    what was decided
     * @param lines       what to log, in order
     * @param crashTitle  the description of the crash report, set only when the start is refused
     * @param crashDetail the explanation of the crash report, set only when the start is refused
     */
    public record Result(Decision decision, List<Line> lines, String crashTitle, String crashDetail) {
        public boolean refused() {
            return decision == Decision.REFUSE;
        }
    }

    private WorldMarkerCheck() {
    }

    /**
     * @param marker          the marker file of the world
     * @param current         this version's data revision
     * @param accept          whether the player accepted the risk for this run
     * @param debugOverride   the development override of the revision to write, or null
     * @param currentModVersion this version's number, or null
     */
    public static Result run(Path marker, int current, boolean accept, Integer debugOverride, String currentModVersion) {
        MarkerRead read = WorldMarkerStore.read(marker);
        int target = WorldMarkerGuard.revisionToWrite(debugOverride, current, read);
        Decision decision = WorldMarkerGuard.decide(read, current, accept, currentModVersion, target);
        List<Line> lines = new ArrayList<>();
        String markerPath = marker.toAbsolutePath().normalize().toString();
        switch (decision) {
            case PASS -> {
            }
            case PASS_AND_WRITE -> {
                if (read instanceof MarkerRead.Corrupt corrupt) {
                    lines.add(new Line(Severity.WARN, WorldGuardTexts.corruptWarn(markerPath, corrupt.reason())));
                }
                Integer previous = read instanceof MarkerRead.Valid valid ? valid.marker().dataRevision() : null;
                try {
                    WorldMarkerStore.write(marker, new WorldMarker(target, currentModVersion));
                    lines.add(new Line(Severity.INFO, WorldGuardTexts.markerSetInfo(target, previous)));
                } catch (IOException | RuntimeException failed) {
                    // Not being able to write the marker must never keep the player out of the world.
                    lines.add(new Line(Severity.WARN, WorldGuardTexts.writeFailedWarn(markerPath, String.valueOf(failed))));
                }
            }
            case ACCEPTED -> lines.add(new Line(Severity.WARN, WorldGuardTexts.acceptedWarn(read.revision(), current)));
            case REFUSE -> {
                MarkerRead.Valid valid = (MarkerRead.Valid) read;
                String detail = WorldGuardTexts.refuseDetail(valid.marker().dataRevision(), valid.marker().modVersion(), current,
                        currentModVersion, markerPath);
                lines.add(new Line(Severity.ERROR, detail));
                return new Result(decision, lines, WorldGuardTexts.crashTitle(valid.marker().dataRevision(), current), detail);
            }
        }
        return new Result(decision, lines, null, null);
    }
}
