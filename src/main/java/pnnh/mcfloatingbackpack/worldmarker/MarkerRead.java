package pnnh.mcfloatingbackpack.worldmarker;

/**
 * 041: what reading a world's marker file gave. Pure data, no Minecraft types.
 */
public sealed interface MarkerRead {
    /** There is no marker file: a world no version with the guard has touched. Reads as revision 0. */
    record Missing() implements MarkerRead {
    }

    /** A readable marker. */
    record Valid(WorldMarker marker) implements MarkerRead {
    }

    /** A file that is there but cannot be used (it does not parse, or it cannot be read). Reads as revision 0, never blocks a start. */
    record Corrupt(String reason) implements MarkerRead {
    }

    Missing MISSING = new Missing();

    /** The revision this read stands for: the marker's, or 0 when there is none or it is unusable. */
    default int revision() {
        return this instanceof Valid valid ? valid.marker().dataRevision() : 0;
    }
}
