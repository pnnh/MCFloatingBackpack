package pnnh.mcfloatingbackpack.compat;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.ItemStackTransferOps;

/**
 * 058: the registered {@link NetworkStorageBridge}s and the one question the delivery of stacks to an open container asks before it takes
 * the generic path: "does a bridge take this stack into this menu?". Registration happens when the mod loads (only for mods that are
 * installed); whether a bridge may run is decided at every call by {@link CompatRuntimeState}, which only exists once the server has
 * started. The rules are in {@link BridgeDispatch}.
 */
public final class NetworkStorageBridges {
    private static final List<NetworkStorageBridge> BRIDGES = new CopyOnWriteArrayList<>();
    private static final BridgeDispatch<ServerPlayer, AbstractContainerMenu, ItemStack> DISPATCH =
            new BridgeDispatch<>(() -> BRIDGES, ThirdPartyCompatService::state, ItemStackTransferOps.INSTANCE, ThirdPartyNotices::failed);

    private NetworkStorageBridges() {
    }

    public static void register(NetworkStorageBridge bridge) {
        BRIDGES.add(bridge);
    }

    /**
     * @return empty when no bridge took the stack (go on with the generic path); otherwise what is left of it, which stays in the backpack
     */
    public static Optional<ItemStack> tryInsert(ServerPlayer player, AbstractContainerMenu menu, ItemStack stack) {
        if (BRIDGES.isEmpty()) {
            return Optional.empty();
        }
        return DISPATCH.tryInsert(player, menu, stack);
    }
}
