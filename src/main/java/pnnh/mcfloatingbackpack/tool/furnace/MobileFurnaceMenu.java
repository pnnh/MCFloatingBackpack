package pnnh.mcfloatingbackpack.tool.furnace;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceMenu;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.tool.MobileMenu;
import pnnh.mcfloatingbackpack.tool.MobileTool;

/**
 * 055: the vanilla furnace menu, opened by the mobile furnace. Its type is vanilla's, so the client shows the vanilla furnace screen and builds
 * the vanilla menu itself; everything in it is vanilla's (the slots, shift click, the recipe book, what the fuel slot takes) except four things:
 * the furnace under it is a {@link MobileFurnaceSession} (a container and four numbers kept on the item, not a block entity), the output slot
 * pays experience from it (vanilla's slot only does for a block entity), it works the furnace out up to now whenever the server looks at the menu
 * (it may be looked at several times in a tick, which works out nothing the second time), and it is valid as long as the backpack the B key opens
 * is still the one it was opened on and still has a mobile furnace in its right area. Nothing in the container is given back when it closes: it
 * stays in the furnace, on the item.
 */
public final class MobileFurnaceMenu extends FurnaceMenu implements MobileMenu {
    private final MobileFurnaceSession session;

    public MobileFurnaceMenu(int containerId, Inventory playerInventory, MobileFurnaceSession session) {
        super(containerId, playerInventory, session.container(), session.data());
        this.session = session;
        Slot vanilla = this.slots.get(2);
        Slot result = new ResultSlot(playerInventory.player, session, vanilla.x, vanilla.y);
        result.index = 2;
        this.slots.set(2, result);
    }

    @Override
    public MobileTool tool() {
        return MobileTool.FURNACE;
    }

    @Override
    public boolean stillValid(Player player) {
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null) {
            return false;
        }
        ItemStack backpack = ref.getStack(player);
        return session.identity().equals(MobileFurnaceStore.identityOf(backpack)) && !MobileFurnaceStore.furnaceOf(backpack).isEmpty();
    }

    @Override
    public void broadcastChanges() {
        session.advanceTo(level.getGameTime());
        super.broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        session.advanceTo(level.getGameTime());
        session.save();
        super.removed(player);
    }

    /** Vanilla's output slot, which also pays the experience out of the mobile furnace (vanilla's only does for a furnace block entity). */
    private static final class ResultSlot extends FurnaceResultSlot {
        private final MobileFurnaceSession session;
        private final Player holder;

        ResultSlot(Player player, MobileFurnaceSession session, int x, int y) {
            super(player, session.container(), 2, x, y);
            this.session = session;
            this.holder = player;
        }

        @Override
        protected void checkTakeAchievements(ItemStack stack) {
            super.checkTakeAchievements(stack);
            if (holder instanceof ServerPlayer serverPlayer) {
                session.payExperience(serverPlayer);
            }
        }
    }
}
