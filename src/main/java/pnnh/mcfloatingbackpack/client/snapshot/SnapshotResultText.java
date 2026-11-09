package pnnh.mcfloatingbackpack.client.snapshot;

import java.util.Locale;

import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenState.Status;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;

/**
 * 031: the line of text a result code stands for: a language key and the arguments to put in it, in the order the text takes
 * them. Pure logic: the window turns this into a component.
 */
public final class SnapshotResultText {
    private static final String PREFIX = "gui.mcfloatingbackpack.snapshot.result.";

    private SnapshotResultText() {
    }

    public static String key(ResultCode code) {
        return PREFIX + code.name().toLowerCase(Locale.ROOT);
    }

    /** How many {@code %s} the text of this code has. */
    public static int placeholders(ResultCode code) {
        return switch (code) {
            case RESTORED, DELETED_ONE, DELETED_ALL, TRIM_DONE, TRIM_PREVIEWED -> 2;
            default -> 0;
        };
    }

    public static Object[] args(Status status) {
        return switch (status.code()) {
            // "Gave <target> a new backpack of <id>"
            case RESTORED -> new Object[] {status.b(), status.a()};
            // "Deleted snapshot #<n> of backpack <id>"
            case DELETED_ONE -> new Object[] {status.n1(), status.a()};
            // "Deleted <n> snapshot(s) of backpack <id>"
            case DELETED_ALL -> new Object[] {status.n1(), status.a()};
            // "<backpacks> backpack(s), <files> snapshot(s)" and "Deleted <files> snapshot(s) of <backpacks> backpack(s)"
            case TRIM_PREVIEWED -> new Object[] {status.n1(), status.n2()};
            case TRIM_DONE -> new Object[] {status.n1(), status.n2()};
            default -> new Object[0];
        };
    }
}
