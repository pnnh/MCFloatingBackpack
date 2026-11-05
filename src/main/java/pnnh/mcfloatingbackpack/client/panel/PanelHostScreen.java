package pnnh.mcfloatingbackpack.client.panel;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.client.FloatingBackpackKeys;
import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.client.panel.HostKeyRouter.KeyRole;

/**
 * 019: the transparent screen the panel sits on when no other screen is open (constitution I). It is a fallback
 * only: it draws nothing (the panel is drawn by the screen-render hook, as on any container screen), does not
 * pause the game and holds no items. {@link HostScreenManager} opens it when the panel is visible and nothing else
 * is showing, and any other screen simply replaces it.
 */
@OnlyIn(Dist.CLIENT)
public final class PanelHostScreen extends Screen {
    /**
     * A key bound to something other than a world action (E, chat, a mod's "open backpack"...). While a screen is
     * open the game applies no key mappings, so it is held here and replayed in {@link #tick()}.
     */
    private boolean pending;
    private int pendingKeyCode;
    private int pendingScanCode;
    private int pendingModifiers;
    private boolean pendingReleased;
    private List<KeyMapping> pendingMappings = List.of();

    public PanelHostScreen() {
        super(Component.empty());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** ESC is handled in {@link #keyPressed}: it closes the panel, not the (invisible) screen. */
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    /** No dimming and no blur: the world must look exactly as it does without a screen. */
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            FloatingPanelEvents.closePanel();
            return true;
        }
        List<KeyMapping> bound = boundMappings(keyCode, scanCode);
        KeyRole role = classify(bound);
        if (role == KeyRole.YIELD) {
            // One key per tick: a second one pressed before the tick replaces the first.
            pending = true;
            pendingKeyCode = keyCode;
            pendingScanCode = scanCode;
            pendingModifiers = modifiers;
            pendingReleased = false;
            pendingMappings = bound;
            return true;
        }
        return role == KeyRole.SWALLOW;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (pending && keyCode == pendingKeyCode) {
            pendingReleased = true;
            return true;
        }
        return classify(boundMappings(keyCode, scanCode)) == KeyRole.SWALLOW;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    @Override
    public void tick() {
        if (!FloatingPanelState.get().isVisible()) {
            HostScreenManager.ensure();
            return;
        }
        if (pending) {
            replay();
        }
    }

    /**
     * Gives the pending key back to the game, as if no screen had been open. {@code Minecraft.tick} runs
     * {@code screen.tick()} and then, if there is no screen, {@code handleKeybinds}: leaving the screen here lets
     * that very tick consume the click (E opens the inventory, T opens chat...), with no rendered frame in between.
     * The press is fed through the game's own keyboard handler <em>after</em> the screen is gone, so it takes the
     * no-screen path in full: key mappings are set and clicked while their conflict context is active (a mapping that
     * only works in game ignores a click made over a screen), and {@code InputEvent.Key} is posted, which is how some
     * mods (Sophisticated Backpacks) read their keys. If the key opened a screen, that screen simply replaces the
     * host; if not, {@link HostScreenManager} puts the host back at the end of the tick.
     */
    private void replay() {
        int keyCode = pendingKeyCode;
        int scanCode = pendingScanCode;
        int modifiers = pendingModifiers;
        boolean released = pendingReleased;
        List<KeyMapping> mappings = pendingMappings;
        pending = false;
        pendingReleased = false;
        pendingMappings = List.of();

        Minecraft minecraft = Minecraft.getInstance();
        List<String> names = new ArrayList<>();
        for (KeyMapping mapping : mappings) {
            names.add(mapping.getName());
        }
        MCFloatingBackpack.LOGGER.info("019 host: replay key {} -> {}", keyCode, names);
        minecraft.setScreen(null);
        HostScreenManager.rememberReplayed(mappings);
        long window = minecraft.getWindow().getWindow();
        minecraft.keyboardHandler.keyPress(window, keyCode, scanCode, GLFW.GLFW_PRESS, modifiers);
        if (released) {
            // The key went up before this tick, while the host swallowed the release: send it now.
            minecraft.keyboardHandler.keyPress(window, keyCode, scanCode, GLFW.GLFW_RELEASE, modifiers);
        }
    }

    @Override
    public void removed() {
        pending = false;
        pendingReleased = false;
        pendingMappings = List.of();
        FloatingPanelInput.resetPressTracking();
    }

    /**
     * The mappings a press of this key would trigger when no screen is open. A mapping whose conflict context is
     * only active while a screen is open (JEI's "add bookmark" shares the default key A with strafe-left, and its
     * contexts are its own, not the vanilla {@code GUI} one) has nothing to hand back to the game, and must not turn a
     * plain world-action key into one that makes the host step aside.
     *
     * <p>So each mapping's own context is asked whether it would be active with no screen open. That is done by
     * clearing {@code Minecraft.screen} for the duration of the check only (contexts just read it) and restoring it
     * straight after; it needs no knowledge of any particular mod.
     */
    private static List<KeyMapping> boundMappings(int keyCode, int scanCode) {
        Minecraft minecraft = Minecraft.getInstance();
        Screen screen = minecraft.screen;
        List<KeyMapping> bound = new ArrayList<>();
        minecraft.screen = null;
        try {
            for (KeyMapping mapping : minecraft.options.keyMappings) {
                if (mapping.matches(keyCode, scanCode) && mapping.getKeyConflictContext().isActive()) {
                    bound.add(mapping);
                }
            }
        } finally {
            minecraft.screen = screen;
        }
        return bound;
    }

    private static KeyRole classify(List<KeyMapping> bound) {
        List<String> names = new ArrayList<>();
        for (KeyMapping mapping : bound) {
            names.add(mapping.getName());
        }
        return HostKeyRouter.classify(names, FloatingBackpackKeys.TOGGLE.getName());
    }
}
