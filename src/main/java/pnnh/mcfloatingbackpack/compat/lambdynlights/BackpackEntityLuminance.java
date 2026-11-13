package pnnh.mcfloatingbackpack.compat.lambdynlights;

import dev.lambdaurora.lambdynlights.api.entity.luminance.EntityLuminance;
import dev.lambdaurora.lambdynlights.api.item.ItemLightSourceManager;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.light.PlayerLights;

/**
 * 052: how bright LambDynamicLights makes a player: the light item in the bottom tool area of the backpack the player has (see
 * {@link PlayerLights}). LambDynamicLights asks this on the client for every player it knows. The floating backpack does no lighting of
 * its own: without LambDynamicLights nothing is asked and nothing shines. Switched off with {@code lambdynlights=GENERIC} or
 * {@code =OFF} in the config's {@code modLevels} (the mod-specific adaptation).
 */
public final class BackpackEntityLuminance implements EntityLuminance {
    public static final BackpackEntityLuminance INSTANCE = new BackpackEntityLuminance();

    private BackpackEntityLuminance() {
    }

    @Override
    public Type type() {
        return BackpackDynamicLight.TYPE;
    }

    @Override
    public int getLuminance(ItemLightSourceManager itemLightSourceManager, Entity entity) {
        if (!(entity instanceof Player player) || !CompatConfig.policy().specificAdaptation(BackpackDynamicLight.MOD_ID)) {
            return 0;
        }
        return PlayerLights.brightnessOf(player);
    }
}
