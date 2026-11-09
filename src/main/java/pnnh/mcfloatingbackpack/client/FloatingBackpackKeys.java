package pnnh.mcfloatingbackpack.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * Default toggle is B. Players can rebind or add modifiers in Controls.
 * UNIVERSAL so the key still works while another container Screen is open.
 */
@OnlyIn(Dist.CLIENT)
public final class FloatingBackpackKeys {
    public static final String CATEGORY = "key.categories." + MCFloatingBackpack.MOD_ID;

    public static final KeyMapping TOGGLE = new KeyMapping(
            "key." + MCFloatingBackpack.MOD_ID + ".toggle",
            KeyConflictContext.UNIVERSAL,
            InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_B),
            CATEGORY);

    /**
     * 022: deletes the stack under the mouse (or the selection) into the trash can. GUI context, so it is only live with
     * a screen open and the host screen's key forwarding (019), which looks at what a key does with no screen open,
     * does not take it for a key to hand back to the game. Backspace on macOS, where the Delete key is hard to come by.
     */
    public static final KeyMapping DELETE_TO_TRASH = new KeyMapping(
            "key." + MCFloatingBackpack.MOD_ID + ".delete_to_trash",
            KeyConflictContext.GUI,
            InputConstants.Type.KEYSYM.getOrCreate(
                    Minecraft.ON_OSX ? GLFW.GLFW_KEY_BACKSPACE : GLFW.GLFW_KEY_DELETE),
            CATEGORY);

    /**
     * 031: opens the snapshot window. Not bound by default (a key that is not asked for would clash with another mod's); the
     * player binds it in Controls.
     */
    public static final KeyMapping SNAPSHOTS = new KeyMapping(
            "key." + MCFloatingBackpack.MOD_ID + ".snapshots",
            KeyConflictContext.IN_GAME,
            InputConstants.UNKNOWN,
            CATEGORY);

    private FloatingBackpackKeys() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(DELETE_TO_TRASH);
        event.register(SNAPSHOTS);
    }
}
