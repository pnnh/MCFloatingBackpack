package pnnh.mcfloatingbackpack.client.quick;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.inventory.BackpackContents;
import pnnh.mcfloatingbackpack.inventory.BackpackInventory;
import pnnh.mcfloatingbackpack.inventory.BackpackRef;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.quick.QuickLists;
import pnnh.mcfloatingbackpack.inventory.revision.DataRevision;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.item.ModItems;

/**
 * 067: decides whether the quick-access key may open the grid. The caller has already checked that this is the game view
 * (no overlay, no screen, panel hidden). Nothing here asks the server, and a newer data revision does not open the
 * 040 confirm window.
 */
@OnlyIn(Dist.CLIENT)
public final class QuickAccessOpener {
    private QuickAccessOpener() {
    }

    public static void tryOpen(Minecraft minecraft) {
        if (minecraft == null || minecraft.player == null) {
            return;
        }
        BackpackRef ref = BackpackRef.resolve(minecraft.player);
        if (ref == null) {
            QuickNotices.actionBar(minecraft, "message.mcfloatingbackpack.need_backpack");
            return;
        }
        if (ref.source() == BackpackRef.Source.MAIN_HAND) {
            QuickNotices.actionBar(minecraft, "message.mcfloatingbackpack.quick.in_hand");
            return;
        }
        ItemStack stack = ref.getStack(minecraft.player);
        BackpackContents contents = stack.get(ModDataComponents.BACKPACK_CONTENTS.get());
        int stored = contents == null ? 0 : contents.dataRevision();
        if (DataRevision.isNewer(stored)) {
            QuickNotices.revisionProtected(minecraft, stored);
            return;
        }
        BackpackInventory inventory = BackpackInventory.fromItem(stack);
        if (!inventory.hasQuickAccess()) {
            QuickNotices.actionBar(minecraft, "message.mcfloatingbackpack.quick.need_tool", toolName(inventory.tier()));
            return;
        }
        if (QuickLists.candidates(inventory).isEmpty()) {
            QuickNotices.actionBar(minecraft, "message.mcfloatingbackpack.quick.none");
            return;
        }
        minecraft.setScreen(new QuickAccessScreen());
    }

    private static Component toolName(BackpackTier tier) {
        if (tier == null) {
            return Component.translatable("item.mcfloatingbackpack.quick_access");
        }
        return Component.translatable(ModItems.quickAccess(tier).getDescriptionId());
    }
}
