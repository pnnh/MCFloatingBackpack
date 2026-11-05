package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.client.panel.HostScreenPolicy.Action;
import pnnh.mcfloatingbackpack.client.panel.HostScreenPolicy.ScreenKind;
import pnnh.mcfloatingbackpack.compat.ContainerScreenCompat;

/**
 * 019: keeps the host screen in step with the panel. {@link #ensure()} is cheap and safe to call as often as
 * needed; the actual decision lives in {@link HostScreenPolicy}. Screen changes cannot be followed through events
 * ({@code setScreen(null)} fires no {@code Opening}, and {@code Closing} may not change the screen), so this is
 * called every frame and right after anything that changes the panel's visibility.
 */
@OnlyIn(Dist.CLIENT)
public final class HostScreenManager {
    /** Mappings whose click the host replayed and that nobody has consumed yet. */
    private static final List<KeyMapping> REPLAYED = new ArrayList<>();

    /**
     * Set when the host stepped away to replay a key. Until the next tick has started the host is not put back:
     * that leaves "no screen" long enough for a mod that polls its key at the start of a tick (Traveler's Backpack
     * reads it in {@code ClientTickEvent.Pre}) or only while no screen is open to see the click, which is what lets
     * any mod's key work without knowing about this one.
     */
    private static boolean awayUntilNextTick;

    /** When the host last stepped away, to log how long the panel was without a screen (0 = not away). */
    private static long awaySinceNanos;

    private HostScreenManager() {
    }

    /** True while the host is away replaying a key: there is no screen, but the panel is meant to stay on show. */
    public static boolean isAway() {
        return awayUntilNextTick;
    }

    /** The host replayed a click on these mappings (see {@link PanelHostScreen}) and stays away until the next tick. */
    static void rememberReplayed(Collection<KeyMapping> mappings) {
        REPLAYED.addAll(mappings);
        awayUntilNextTick = true;
        awaySinceNanos = System.nanoTime();
    }

    /**
     * Start of a tick, after every other mod's start-of-tick handler (lowest priority). A replayed click that
     * nobody consumed (a mod that does not read its key this way at all) must not wait around to fire when the host
     * finally closes, so it is thrown away; then the host comes back if it is due.
     */
    public static void beforeTick() {
        awayUntilNextTick = false;
        for (KeyMapping mapping : REPLAYED) {
            while (mapping.consumeClick()) {
                // drop it
            }
        }
        REPLAYED.clear();
        ensure();
    }

    /** End of a tick: put the host back if it is due (and the replay window is over). */
    public static void afterTick() {
        ensure();
    }

    /** Leaving the world. */
    public static void reset() {
        REPLAYED.clear();
        awayUntilNextTick = false;
        awaySinceNanos = 0;
    }

    public static void ensure() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.getOverlay() != null) {
            return;
        }
        Action action = HostScreenPolicy.decide(
                FloatingPanelState.get().isVisible(),
                kindOf(minecraft.screen),
                !minecraft.player.isDeadOrDying());
        switch (action) {
            case OPEN_HOST -> {
                if (!awayUntilNextTick) {
                    if (awaySinceNanos != 0) {
                        MCFloatingBackpack.LOGGER.info(
                                "019 host: open (was away {} ms)", (System.nanoTime() - awaySinceNanos) / 1_000_000L);
                        awaySinceNanos = 0;
                    } else {
                        MCFloatingBackpack.LOGGER.info("019 host: open");
                    }
                    minecraft.setScreen(new PanelHostScreen());
                }
            }
            case CLOSE_HOST -> {
                MCFloatingBackpack.LOGGER.info("019 host: close (panel hidden)");
                minecraft.setScreen(null);
            }
            case CLEAR_PANEL -> {
                MCFloatingBackpack.LOGGER.info("019 host: clear panel (player dead)");
                FloatingPanelEvents.closePanel();
            }
            case NONE -> {
            }
        }
    }

    private static ScreenKind kindOf(Screen screen) {
        if (screen == null) {
            return ScreenKind.NONE;
        }
        if (screen instanceof PanelHostScreen) {
            return ScreenKind.HOST;
        }
        return ContainerScreenCompat.isOpenContainerScreen(screen) ? ScreenKind.CONTAINER : ScreenKind.OTHER;
    }
}
