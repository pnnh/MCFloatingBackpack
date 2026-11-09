package pnnh.mcfloatingbackpack;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import pnnh.mcfloatingbackpack.client.ClientSetup;
import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.creative.CreativeCursorBridge;
import pnnh.mcfloatingbackpack.inventory.pickup.AutoPickupEvents;
import pnnh.mcfloatingbackpack.inventory.recipe.ModRecipes;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.network.BackpackNetwork;

/**
 * Floating backpack mod entry. Common code always loads; client overlay hooks
 * register only on the physical client.
 */
@Mod(MCFloatingBackpack.MOD_ID)
public class MCFloatingBackpack {
    public static final String MOD_ID = "mcfloatingbackpack";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MCFloatingBackpack(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("MCFloatingBackpack loading");
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModRecipes.register(modEventBus);
        BackpackNetwork.register(modEventBus);
        // 024: the auto pickup. Low priority, so Sophisticated Backpacks and Traveler's Backpack (normal) have run first.
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOW, (ItemEntityPickupEvent.Pre event) -> AutoPickupEvents.onItemPickup(event));
        // 027: the server's mirror of a creative player's cursor ends when the inventory screen closes or the player leaves.
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getContainer() == player.inventoryMenu) {
                CreativeCursorBridge.onClose(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                CreativeCursorBridge.onLogout(player);
            }
        });
        // 020: client-only settings; a dedicated server never loads a CLIENT config.
        modContainer.registerConfig(ModConfig.Type.CLIENT, CompatConfig.SPEC);
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.register(modEventBus, modContainer);
        }
    }
}
