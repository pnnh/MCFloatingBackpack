package pnnh.mcfloatingbackpack.tool.furnace;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackGate;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;
import pnnh.mcfloatingbackpack.item.ModItems;
import net.minecraft.server.level.ServerPlayer;

/**
 * 055: where the server finds and keeps a mobile furnace's state: on the mobile furnace item in the right area of the backpack the B key opens.
 * Reading looks at the stored backpack and changes nothing. Writing goes through {@link BackpackGate} like every other change of a backpack (a
 * backpack saved by a newer version is never written), and only into the backpack the furnace menu was opened on: the identity of the backpack
 * must still be the one the menu remembers, so a state never lands in another backpack's furnace.
 */
public final class MobileFurnaceStore {
    private MobileFurnaceStore() {
    }

    /** The identity of a backpack stack, or null if it has none (yet). */
    public static UUID identityOf(ItemStack backpack) {
        BackpackIdentity identity = backpack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        return identity == null ? null : identity.uuid();
    }

    /** The first mobile furnace in the right area of the backpack stack, or empty. */
    public static ItemStack furnaceOf(ItemStack backpack) {
        BackpackContents contents = backpack.get(ModDataComponents.BACKPACK_CONTENTS.get());
        if (contents == null) {
            return ItemStack.EMPTY;
        }
        for (ItemStack stack : contents.tools().right()) {
            if (stack.is(ModItems.MOBILE_FURNACE.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    /** The state on the player's mobile furnace (an empty furnace if it has none), or null if the backpack the B key opens has no mobile furnace. */
    public static FurnaceState read(Player player) {
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null) {
            return null;
        }
        ItemStack furnace = furnaceOf(ref.getStack(player));
        return furnace.isEmpty() ? null : furnace.getOrDefault(ModDataComponents.MOBILE_FURNACE_STATE.get(), FurnaceState.EMPTY);
    }

    /**
     * Puts the state on the mobile furnace of the backpack with this identity (the one the B key opens now). Does nothing, and says so, if that is
     * not the backpack any more, if the backpack is refused (saved by a newer version), or if it has no mobile furnace.
     *
     * @return whether the state was written
     */
    public static boolean write(ServerPlayer player, UUID identity, FurnaceState state) {
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null || identity == null || !identity.equals(identityOf(ref.getStack(player)))) {
            return false;
        }
        Optional<BackpackInventory> acquired = BackpackGate.acquire(player, ref);
        if (acquired.isEmpty()) {
            return false;
        }
        BackpackInventory inventory = acquired.get();
        ItemStackHandler right = inventory.handler(PanelArea.RIGHT);
        for (int i = 0; i < right.getSlots(); i++) {
            ItemStack stack = right.getStackInSlot(i);
            if (stack.is(ModItems.MOBILE_FURNACE.get())) {
                ItemStack updated = stack.copy();
                updated.set(ModDataComponents.MOBILE_FURNACE_STATE.get(), state);
                right.setStackInSlot(i, updated);
                ref.saveInventory(player, inventory);
                return true;
            }
        }
        return false;
    }

    /**
     * 055: the furnace's menu. Unlike the crafting table it has a state to keep, so the backpack goes through the gate first (it gets its identity,
     * which the menu remembers so that it never writes into another backpack's furnace); a backpack saved by a newer version is refused, and the
     * player is told why. The furnace is worked out up to the game time now (the catch-up for the time it was shut) and written back before the
     * menu is opened.
     *
     * @return the provider, or null if nothing is to be opened
     */
    public static MenuProvider provider(ServerPlayer player) {
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null) {
            return null;
        }
        if (BackpackGate.acquire(player, ref).isEmpty()) {
            if (BackpackGate.isProtected(ref.getStack(player))) {
                player.displayClientMessage(Component.translatable("message.mcfloatingbackpack.furnace.protected"), true);
            }
            return null;
        }
        FurnaceState state = MobileFurnaceStore.read(player);
        UUID identity = MobileFurnaceStore.identityOf(ref.getStack(player));
        if (state == null || identity == null) {
            return null;
        }
        MobileFurnaceSession session = new MobileFurnaceSession(player, identity, state, player.serverLevel().getGameTime());
        session.save();
        return new SimpleMenuProvider((id, inventory, p) -> new MobileFurnaceMenu(id, inventory, session), Component.translatable("container.furnace"));
    }
}
