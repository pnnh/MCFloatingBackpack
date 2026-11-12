package pnnh.mcfloatingbackpack.client.panel;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionCategories;
import pnnh.mcfloatingbackpack.inventory.extension.ExtensionRules;
import pnnh.mcfloatingbackpack.inventory.extension.ItemCategoryMatcher;
import pnnh.mcfloatingbackpack.inventory.extension.RowLayout;
import pnnh.mcfloatingbackpack.inventory.extension.RowView;

/**
 * 037: the panel's guess, before anything is sent, at whether a stack can go into a cell of the main area. It runs the same rule
 * as the server ({@link ExtensionRules#canPlace(java.util.List, int, int, Object, pnnh.mcfloatingbackpack.inventory.extension.CategoryMatcher)},
 * which the server's own check delegates to) on what the server told the client about the rows; only the server decides.
 *
 * <p>A guess may only be too generous, never too strict: a stack the server would take must never be called unfit, or the player
 * would think the feature broke. So the answer is "yes" whenever the guess cannot be sure: there are no rows, the cell is one of
 * the tier capacity's, the stack is empty, the category tags have not reached the client yet (just after joining a world or a data
 * pack change), the list of cells does not match the rows the client was told about (a sync is on its way), or the row is of a
 * category this version does not know (039: a client older than the server).
 */
@OnlyIn(Dist.CLIENT)
public final class ClientPlacement {
    private ClientPlacement() {
    }

    /** Whether the stack can be put into the main-area cell {@code slot} of the backpack the panel shows. */
    public static boolean canPlace(ClientBackpackView view, int slot, ItemStack stack) {
        RowView rows = view.rowView();
        if (rows.visibleCategories().isEmpty() || stack.isEmpty() || slot < rows.baseCapacity()) {
            return true;
        }
        if (view.slotCount() != rows.baseCapacity() + rows.visibleCategories().size() * ExtensionCategories.ROW_CELLS) {
            return true;
        }
        // 039: a row of a category this version does not know (it was made by a newer version): say yes and let the server decide.
        if (new RowLayout(rows.baseCapacity(), rows.visibleCategories()).isUnknownRow(slot)) {
            return true;
        }
        if (!categoryTagsBound()) {
            return true;
        }
        return ExtensionRules.canPlace(
                rows.visibleCategories(), rows.baseCapacity(), slot, stack, new ItemCategoryMatcher(view.tags()));
    }

    /** Whether the data tags of all the categories have been bound in the client's item registry (they arrive with the world's tags). */
    static boolean categoryTagsBound() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return false;
        }
        try {
            var items = minecraft.level.registryAccess().registryOrThrow(Registries.ITEM);
            for (String id : ExtensionCategories.IDS) {
                TagKey<Item> key = TagKey.create(
                        Registries.ITEM, ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "extension/" + id));
                if (items.getTag(key).isEmpty()) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException notBoundYet) {
            // A registry whose tags are not bound throws when asked for one.
            return false;
        }
    }
}
