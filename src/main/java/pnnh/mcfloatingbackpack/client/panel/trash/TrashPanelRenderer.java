package pnnh.mcfloatingbackpack.client.panel.trash;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;
import pnnh.mcfloatingbackpack.client.panel.PanelSlots;
import pnnh.mcfloatingbackpack.inventory.PanelArea;

/**
 * 021: draws the trash can panel: the frame and a title bar in the floating panel's own colors, and nine cells in the
 * right area's well color (the trash can lives there). Everything is {@code fill} and text, no textures. Called from
 * {@link FloatingPanelRenderer#render} inside its overlay depth, after the panel's own content.
 */
@OnlyIn(Dist.CLIENT)
public final class TrashPanelRenderer {
    /** Vanilla's container title color. */
    private static final int COLOR_TITLE = 0xFF404040;

    private TrashPanelRenderer() {
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        TrashPanelLayout layout = TrashPanelInput.layout();
        Rect frame = layout.rect();
        FloatingPanelRenderer.drawVanillaPanel(graphics, frame.x(), frame.y(), frame.w(), frame.h());

        Rect title = layout.titleRect();
        Component text = Component.translatable("trash.mcfloatingbackpack.title");
        graphics.drawString(
                minecraft.font,
                text,
                title.x() + TrashPanelLayout.PAD + 1,
                title.y() + (title.h() - minecraft.font.lineHeight) / 2 + 1,
                COLOR_TITLE,
                false);

        int hovered = layout.cellAt(mouseX, mouseY);
        for (int i = 0; i < TrashPanelLayout.CELLS; i++) {
            Rect cell = layout.cellRect(i);
            FloatingPanelRenderer.drawSlotWell(graphics, cell.x(), cell.y(), FloatingPanelRenderer.wellColor(PanelArea.RIGHT));
            ItemStack stack = ClientBackpackView.get().trash().get(i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, cell.x() + 1, cell.y() + 1);
                graphics.renderItemDecorations(minecraft.font, stack, cell.x() + 1, cell.y() + 1);
            }
            if (i == hovered) {
                graphics.fill(
                        cell.x(),
                        cell.y(),
                        cell.x() + PanelSlots.SLOT_SIZE,
                        cell.y() + PanelSlots.SLOT_SIZE,
                        FloatingPanelRenderer.COLOR_SLOT_HOVER);
            }
        }
    }
}
