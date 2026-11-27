package pnnh.mcfloatingbackpack.client.quick;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.inventory.revision.DataRevision;

/**
 * 067: the same action-bar line is not repeated within a second. The revision warning goes to chat, like the one the open
 * path already uses, and is covered by the same gap.
 */
@OnlyIn(Dist.CLIENT)
public final class QuickNotices {
    static final int GAP_TICKS = 20;

    private static final Map<String, Integer> LAST = new HashMap<>();

    private QuickNotices() {
    }

    public static void actionBar(Minecraft minecraft, String key) {
        actionBar(minecraft, key, null);
    }

    /** {@code arg} is inserted as {@code %s}. The one-second gap is still per translation key. */
    public static void actionBar(Minecraft minecraft, String key, Component arg) {
        if (minecraft == null || minecraft.player == null || !allow(minecraft, key)) {
            return;
        }
        Component line = arg == null ? Component.translatable(key) : Component.translatable(key, arg);
        minecraft.player.displayClientMessage(line, true);
    }

    public static void revisionProtected(Minecraft minecraft, int stored) {
        String key = "message.mcfloatingbackpack.revision.protected";
        if (minecraft == null || minecraft.player == null || !allow(minecraft, key)) {
            return;
        }
        FloatingPanelEvents.chat(
                minecraft, Component.translatable(key, stored, DataRevision.CURRENT));
    }

    private static boolean allow(Minecraft minecraft, String key) {
        int now = minecraft.player.tickCount;
        Integer previous = LAST.get(key);
        if (previous != null && now - previous < GAP_TICKS && now >= previous) {
            return false;
        }
        LAST.put(key, now);
        return true;
    }
}
