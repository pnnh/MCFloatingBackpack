package pnnh.mcfloatingbackpack.client.snapshot;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.snapshot.net.SnapshotListPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotPreviewPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotResultPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotVersionsPayload;

/**
 * 031: the client end of the snapshot window: opens it and hands the server's replies to it. A reply that arrives when the window is
 * not open (it was closed first) is dropped.
 */
@OnlyIn(Dist.CLIENT)
public final class SnapshotClient {
    private SnapshotClient() {
    }

    /** Opens the window (from the command, the key or the panel's button); nothing happens if it is already open. */
    public static void open() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.level == null) {
            return;
        }
        if (minecraft.screen instanceof SnapshotScreen) {
            return;
        }
        minecraft.setScreen(new SnapshotScreen());
    }

    private static SnapshotScreen screen() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft != null && minecraft.screen instanceof SnapshotScreen screen ? screen : null;
    }

    public static void onList(SnapshotListPayload payload) {
        SnapshotScreen screen = screen();
        if (screen != null) {
            screen.onList(payload);
        }
    }

    public static void onVersions(SnapshotVersionsPayload payload) {
        SnapshotScreen screen = screen();
        if (screen != null) {
            screen.onVersions(payload);
        }
    }

    public static void onPreview(SnapshotPreviewPayload payload) {
        SnapshotScreen screen = screen();
        if (screen != null) {
            screen.onPreview(payload);
        }
    }

    public static void onResult(SnapshotResultPayload payload) {
        SnapshotScreen screen = screen();
        if (screen != null) {
            screen.onResult(payload);
        }
    }
}
