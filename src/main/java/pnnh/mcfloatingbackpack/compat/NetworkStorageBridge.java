package pnnh.mcfloatingbackpack.compat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;

/**
 * 058: a third-party mod's way of taking stacks from the floating backpack straight into its own item network, for menus where the generic
 * path of 012 (park the stack in a player slot and call the menu's {@code quickMoveStack}) does nothing, because the network is not made of
 * slots. Implementations keep the third-party types to themselves (constitution VII: under {@code compat/<modid>/}); the rest of the mod
 * only knows this interface and {@link NetworkStorageBridges}. Taking stacks out of such a network is a later feature.
 */
public interface NetworkStorageBridge extends BridgeDispatch.Bridge<ServerPlayer, AbstractContainerMenu, ItemStack> {
}
