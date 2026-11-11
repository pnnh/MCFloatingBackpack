package pnnh.mcfloatingbackpack;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import pnnh.mcfloatingbackpack.client.ClientSetup;
import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.diag.BackpackWatchService;
import pnnh.mcfloatingbackpack.diag.DiagConfig;
import pnnh.mcfloatingbackpack.diag.DiagLog;
import pnnh.mcfloatingbackpack.diag.LogLevel;
import pnnh.mcfloatingbackpack.diag.MainHandTrace;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionPacks;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.item.CategoryExtensionItem;
import pnnh.mcfloatingbackpack.item.UniversalExtensionItem;
import pnnh.mcfloatingbackpack.version.PlayerVersions;
import pnnh.mcfloatingbackpack.version.VersionNetwork;
import pnnh.mcfloatingbackpack.compat.accessories.AccessoriesCompat;
import pnnh.mcfloatingbackpack.compat.curios.CuriosCompat;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.creative.CreativeCursorBridge;
import pnnh.mcfloatingbackpack.inventory.pickup.AutoPickupEvents;
import pnnh.mcfloatingbackpack.inventory.recipe.ModRecipes;
import pnnh.mcfloatingbackpack.item.ModItems;
import pnnh.mcfloatingbackpack.network.BackpackNetwork;
import pnnh.mcfloatingbackpack.worldmarker.WorldMarkerEvents;
import pnnh.mcfloatingbackpack.snapshot.SnapshotCommands;
import pnnh.mcfloatingbackpack.snapshot.SnapshotService;

/**
 * Floating backpack mod entry. Common code always loads; client overlay hooks
 * register only on the physical client.
 */
@Mod(MCFloatingBackpack.MOD_ID)
public class MCFloatingBackpack {
    public static final String MOD_ID = "mcfloatingbackpack";
    public static final Logger LOGGER = LogUtils.getLogger();
    /** Diagnostics: the server's look at every player's main hand, once a tick. */
    private static final MainHandTrace SERVER_MAIN_HAND = new MainHandTrace();

    public MCFloatingBackpack(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("MCFloatingBackpack loading");
        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModRecipes.register(modEventBus);
        // 036: the category extensions are what 035's rows are made of: tell the rules which item is a pack of which category.
        // Both sides run this, so the server's rules and the client agree.
        ExtensionPacks.register(
                stack -> stack.getItem() instanceof CategoryExtensionItem extension ? extension.category() : null);
        // 038: a universal extension counts only in a backpack of its own tier; both sides need to know which tier it is.
        ExtensionPacks.registerTier(
                stack -> stack.getItem() instanceof UniversalExtensionItem universal ? universal.tierId() : null);
        BackpackNetwork.register(modEventBus);
        // 033: a backpack can be worn in Curios' back slot, but only when Curios is there; nothing of it loads otherwise.
        if (ModList.get().isLoaded("curios")) {
            CuriosCompat.register();
        }
        // 034: and in Accessories' back slot, after Curios' (the client also registers its renderers, in ClientSetup).
        if (ModList.get().isLoaded("accessories")) {
            AccessoriesCompat.register();
            // 036: and not equipped by the right click that opens the panel (Accessories does that by default; see BackpackAccessory).
            modEventBus.addListener(
                    (net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent event) -> event.enqueueWork(AccessoriesCompat::registerAccessories));
        }
        // 024: the auto pickup. Low priority, so Sophisticated Backpacks and Traveler's Backpack (normal) have run first.
        NeoForge.EVENT_BUS.addListener(
                EventPriority.LOW, (ItemEntityPickupEvent.Pre event) -> AutoPickupEvents.onItemPickup(event));
        // 027: the server's mirror of a creative player's cursor ends when the inventory screen closes or the player leaves.
        NeoForge.EVENT_BUS.addListener((PlayerContainerEvent.Close event) -> {
            if (event.getEntity() instanceof ServerPlayer player && event.getContainer() == player.inventoryMenu) {
                CreativeCursorBridge.onClose(player);
            }
        });
        // 039: at login the server tells the player which version of this mod it runs (see VersionNetwork).
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                VersionNetwork.onLogin(player);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                PlayerVersions.forget(player.getUUID());
                CreativeCursorBridge.onLogout(player);
                BackpackWatchService.forget(player.getUUID());
                SERVER_MAIN_HAND.forget(player.getUUID());
                BackpackGate.forget(player.getUUID());
                // 030: what the player changed last is written as a snapshot now.
                SnapshotService.get().ifPresent(service -> service.onLogout(player.getUUID()));
            }
        });
        // 041: before the world is loaded, and so before any player data is read, a world that a newer data revision saved is refused.
        NeoForge.EVENT_BUS.addListener(WorldMarkerEvents::onServerAboutToStart);
        // 030: backpack snapshots, kept outside the world: started with the server, checked once a second, written out when it stops.
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> SnapshotService.start(event.getServer()));
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> SnapshotService.stop());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            // Every tick: a backpack coming into or going out of a main hand, with the time (see MainHandTrace).
            for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                var change = SERVER_MAIN_HAND.observe(player.getUUID(), player.getInventory().selected, player.getMainHandItem());
                if (change != null) {
                    DiagLog.record(player.getUUID(), change.text());
                    DiagLog.log(
                            DiagLog.Side.SERVER,
                            change.sameSlot() ? LogLevel.WARN : LogLevel.INFO,
                            "{} of {} (tick {}); backpacks now: {}",
                            change.text(),
                            player.getGameProfile().getName(),
                            event.getServer().getTickCount(),
                            MainHandTrace.backpacks(player.getInventory()));
                }
            }
            if (event.getServer().getTickCount() % 20 == 0) {
                SnapshotService.get().ifPresent(SnapshotService::tick);
                BackpackWatchService.tick(event.getServer());
                // The panel follows the backpack: if another one is where it looked, show that one (or close if none is left).
                for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
                    BackpackGate.tickOpenSession(player);
                }
            }
        });
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> SnapshotCommands.register(event.getDispatcher()));
        // 020: client-only settings; a dedicated server never loads a CLIENT config.
        modContainer.registerConfig(ModConfig.Type.CLIENT, CompatConfig.SPEC);
        // The server's own log level (the world's serverconfig folder); on a dedicated server this is its only config.
        modContainer.registerConfig(ModConfig.Type.SERVER, DiagConfig.SERVER_SPEC);
        if (FMLEnvironment.dist.isClient()) {
            ClientSetup.register(modEventBus, modContainer);
        }
    }
}
