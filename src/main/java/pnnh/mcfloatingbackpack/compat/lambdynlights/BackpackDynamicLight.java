package pnnh.mcfloatingbackpack.compat.lambdynlights;

import dev.lambdaurora.lambdynlights.api.DynamicLightsContext;
import dev.lambdaurora.lambdynlights.api.DynamicLightsInitializer;
import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 052: the one entry into the LambDynamicLights support, found through the {@code lambdynlights:initializer} entry point that
 * {@code neoforge.mods.toml} declares (the way Traveler's Backpack does it). LambDynamicLights calls it, and only when it is installed;
 * the floating backpack's own code never refers to this class, so without LambDynamicLights none of it is loaded. The light is a
 * custom luminance for players (see {@link BackpackEntityLuminance}).
 */
public class BackpackDynamicLight implements DynamicLightsInitializer {
    /** The mod id of LambDynamicLights (also the key in the config's {@code modLevels}). */
    public static final String MOD_ID = "lambdynlights";

    public static final EntityLuminance.Type TYPE = EntityLuminance.Type.registerSimple(
            ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "tool_light"), BackpackEntityLuminance.INSTANCE);

    @Override
    public void onInitializeDynamicLights(DynamicLightsContext context) {
        context.entityLightSourceManager().onRegisterEvent().register(registerContext ->
                registerContext.register(EntityType.PLAYER, new EntityLuminance[] {BackpackEntityLuminance.INSTANCE}));
    }

    @Override
    public void onInitializeDynamicLights(ItemLightSourceManager itemLightSourceManager) {
        // the light comes from the backpack, not from an item that is held: nothing to register for items
    }
}
