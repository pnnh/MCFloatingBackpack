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

    /** 057: a restore that could not restore some stacks (the item no longer exists) has a text of its own, which says how many. */
    public static boolean restoredWithLoss(Status status) {
        return status.code() == ResultCode.RESTORED && status.n1() > 0;
    }

    /** The key of what a status says (a restore with lost stacks has its own). */
    public static String key(Status status) {
        return restoredWithLoss(status) ? PREFIX + "restored_lost" : key(status.code());
    }

    /** How many {@code %s} the text a status says has. */
    public static int placeholders(Status status) {
        return restoredWithLoss(status) ? 3 : placeholders(status.code());
    }

    /** How many {@code %s} the text of this code has. */
    public static int placeholders(ResultCode code) {
        return switch (code) {
            case RESTORED, DELETED_ONE, DELETED_ALL, TRIM_DONE, TRIM_PREVIEWED -> 2;
            default -> 0;
        };
    }

    public static Object[] args(Status status) {
        if (restoredWithLoss(status)) {
            // "Gave <target> a new backpack of <id>. <n> stack(s) could not be restored"
            return new Object[] {status.b(), status.a(), status.n1()};
        }
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
