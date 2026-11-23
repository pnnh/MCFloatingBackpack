package pnnh.mcfloatingbackpack.client.compat;

import java.util.Optional;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

/**
 * 060: a third-party mod's way of telling, on the client, that the cursor is over an entry of its terminal's network list (an entry may or
 * may not be a slot of the menu: AE2's are, Integrated Terminals' are drawn by the screen itself). Third-party
 * types appear only in the implementations (under {@code compat/<modid>/}); the rest of the mod only knows this interface.
 */
public interface NetworkTerminalClient {
    /**
     * The entry under the cursor (GUI-scaled position), if this is the mod's terminal screen, the right page, and the entry is something
     * that can be taken. Changes nothing.
     */
    Optional<NetworkEntry> entryAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY);

    /**
     * What the player holds on the terminal's own cursor (a picked-up entry that is not the menu's carried stack), if this is the mod's terminal
     * screen and the page is one the panel can take from. Changes nothing. Most terminals have no such thing (AE2's picked-up stack is the
     * menu's carried one, which the panel already follows).
     */
    default Optional<NetworkHeld> held(AbstractContainerScreen<?> screen) {
        return Optional.empty();
    }

    /** Lets go of what {@link #held} found, once the panel has taken the drop (the terminal must not also drop it somewhere). */
    default void release(AbstractContainerScreen<?> screen) {
    }
}
