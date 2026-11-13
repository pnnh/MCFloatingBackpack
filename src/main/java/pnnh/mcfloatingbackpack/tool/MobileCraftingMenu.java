package pnnh.mcfloatingbackpack.tool;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;

/**
 * 054: the vanilla crafting menu, opened by the mobile crafting table. Everything is the vanilla menu's (the grid, the result, the recipe book,
 * what is given back on closing), and its type is vanilla's, so the client shows the vanilla screen and builds the vanilla menu itself. The
 * {@code access} is the player's own place, so that the result is worked out ({@link ContainerLevelAccess#NULL} would never work it out); the
 * one thing changed is {@link #stillValid}: the vanilla menu asks whether it stands at a crafting table block, this one whether the backpack the B
 * key opens still has the mobile crafting table in its right area. The server asks every tick, so taking the tool out (or losing the
 * backpack) closes the menu at once.
 */
public final class MobileCraftingMenu extends CraftingMenu implements MobileMenu {
    public MobileCraftingMenu(int containerId, Inventory playerInventory, ContainerLevelAccess access) {
        super(containerId, playerInventory, access);
    }

    @Override
    public boolean stillValid(Player player) {
        return MobileTools.has(player, MobileTool.CRAFTING_TABLE);
    }

    @Override
    public MobileTool tool() {
        return MobileTool.CRAFTING_TABLE;
    }
}
