package pnnh.mcfloatingbackpack.client;

import org.lwjgl.glfw.GLFW;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
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

    private FloatingBackpackKeys() {
    }

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
    }
}
