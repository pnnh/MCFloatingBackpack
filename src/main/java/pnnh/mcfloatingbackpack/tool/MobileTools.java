package pnnh.mcfloatingbackpack.tool;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * 054: what the server knows about the mobile tools of the right area: whether the backpack the B key opens has one, and how to open its vanilla
 * menu. The backpack is only read (not through the gate, nothing is changed), so a backpack that a newer version saved works too.
 */
public final class MobileTools {
    private MobileTools() {
    }

    /** The item of a tool. */
    public static Item itemOf(MobileTool tool) {
        return switch (tool) {
            case CRAFTING_TABLE -> ModItems.MOBILE_CRAFTING_TABLE.get();
        };
    }

    /** Whether the backpack the B key opens (hand, then back, then inventory) has this tool in its right area. */
    public static boolean has(Player player, MobileTool tool) {
        BackpackRef ref = BackpackRef.resolve(player);
        if (ref == null) {
            return false;
        }
        BackpackContents contents = ref.getStack(player).get(ModDataComponents.BACKPACK_CONTENTS.get());
        if (contents == null) {
            return false;
        }
        Item item = itemOf(tool);
        for (ItemStack stack : contents.tools().right()) {
            if (stack.is(item)) {
                return true;
            }
        }
        return false;
    }

    /** Opens the tool's vanilla menu for the player; whatever menu is open is closed first, with what is held on its cursor given back. */
    public static void open(ServerPlayer player, MobileTool tool) {
        MenuProvider provider = switch (tool) {
            case CRAFTING_TABLE -> new SimpleMenuProvider(
                    (id, inventory, p) -> new MobileCraftingMenu(id, inventory, ContainerLevelAccess.create(p.level(), p.blockPosition())),
                    Component.translatable("container.crafting"));
        };
        player.openMenu(provider);
    }
}
