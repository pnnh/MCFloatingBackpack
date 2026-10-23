package pnnh.mcfloatingbackpack;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import pnnh.mcfloatingbackpack.network.BackpackNetwork;

@EventBusSubscriber(modid = MCFloatingBackpack.MOD_ID)
public final class ServerEvents {
    private ServerEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BackpackNetwork.sendFullSync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BackpackNetwork.sendFullSync(player);
        }
    }
}
