package pnnh.mcfloatingbackpack;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;

import pnnh.mcfloatingbackpack.client.ClientSetup;
import pnnh.mcfloatingbackpack.inventory.BackpackAttachments;
import pnnh.mcfloatingbackpack.network.BackpackNetwork;

/**
 * Floating backpack mod entry. Common code always loads; client overlay hooks
 * register only on the physical client.
 */
@Mod(MCFloatingBackpack.MOD_ID)
public class MCFloatingBackpack {
    public static final String MOD_ID = "mcfloatingbackpack";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCFloatingBackpack(IEventBus modEventBus) {
        LOGGER.info("MCFloatingBackpack loading");
        BackpackAttachments.register(modEventBus);
        BackpackNetwork.register(modEventBus);
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.register(modEventBus);
        }
    }
}
