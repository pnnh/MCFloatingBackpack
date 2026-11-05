package pnnh.mcfloatingbackpack.compat.jei;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.gui.handlers.IGlobalGuiHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;

import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;

/**
 * Tells JEI to treat the floating panel's current screen area as occupied (008), via JEI's own
 * {@code IGlobalGuiHandler} extension point rather than registering per container-screen type —
 * the panel can appear over any of them.
 *
 * <p>This class lives in its own package (not {@code pnnh.mcfloatingbackpack.compat}) because it
 * is the only place in this mod that compiles against a third-party mod's API directly; it is
 * only ever instantiated by JEI's own plugin scanner, so it is never touched when JEI isn't
 * present. See {@code build.gradle} for the matching {@code compileOnly} dependency.
 */
@JeiPlugin
public final class FloatingBackpackJeiPlugin implements IModPlugin {
    private static final ResourceLocation PLUGIN_UID =
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return PLUGIN_UID;
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGlobalGuiHandler(new IGlobalGuiHandler() {
            @Override
            public Collection<Rect2i> getGuiExtraAreas() {
                FloatingPanelState state = FloatingPanelState.get();
                // 020: this is the one mod-specific adaptation; the client config (compat.modLevels, mod id 'jei')
                // switches it off at GENERIC or OFF. Asked every time JEI lays out, so a change needs no restart.
                if (!state.isVisible() || !CompatConfig.policy().specificAdaptation("jei")) {
                    return Collections.emptyList();
                }
                return List.of(
                        new Rect2i(state.getPosX(), state.getPosY(), state.getWidth(), state.getHeight()));
            }
        });
    }
}
