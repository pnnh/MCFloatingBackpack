package pnnh.mcfloatingbackpack.client.panel.jukebox;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.jukebox.JukeboxPlayback;
import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelLayout.Button;
import pnnh.mcfloatingbackpack.client.panel.jukebox.JukeboxPanelLayout.Toggle;

/**
 * 053: draws the player page of the mobile jukebox: the frame and a title bar in the floating panel's own colors, the title of the track and
 * its number, four raised buttons with glyphs and two switches. Everything is {@code fill} and text, no textures. Called from
 * {@link FloatingPanelRenderer#render} inside its overlay depth, after the panel's own content.
 */
@OnlyIn(Dist.CLIENT)
public final class JukeboxPanelRenderer {
    /** Vanilla's container title color. */
    private static final int COLOR_TITLE = 0xFF404040;
    private static final int COLOR_TEXT = 0xFF303030;
    private static final int COLOR_HINT = 0xFF707070;
    private static final int COLOR_BG = 0xFFA8A8A8;
    private static final int COLOR_BG_HOVER = 0xFFC9D6F0;
    private static final int COLOR_BG_OFF = 0xFF9A9A9A;
    private static final int COLOR_BG_ON = 0xFF8FC27A;
    private static final int COLOR_LIGHT = 0xFFFFFFFF;
    private static final int COLOR_DARK = 0xFF555555;
    private static final int COLOR_GLYPH = 0xFF3A3A3A;
    private static final int COLOR_GLYPH_OFF = 0xFF707070;
    /** The widest a tooltip line gets before it wraps. */
    private static final int TOOLTIP_WIDTH = 170;

    private JukeboxPanelRenderer() {
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        JukeboxPanelLayout layout = JukeboxPanelInput.layout();
        JukeboxPlayback.Status status = JukeboxPlayback.status();
        Rect frame = layout.rect();
        FloatingPanelRenderer.drawVanillaPanel(graphics, frame.x(), frame.y(), frame.w(), frame.h());

        Rect title = layout.titleRect();
        graphics.drawString(
                minecraft.font,
                Component.translatable("jukebox.mcfloatingbackpack.title"),
                title.x() + JukeboxPanelLayout.PAD + 1,
                title.y() + (title.h() - minecraft.font.lineHeight) / 2 + 1,
                COLOR_TITLE,
                false);

        Rect name = layout.nameRect();
        Component line = !status.hasDiscs()
                ? Component.translatable("jukebox.mcfloatingbackpack.empty")
                : status.title() == null ? Component.literal("-") : status.title();
        graphics.drawString(
                minecraft.font,
                minecraft.font.plainSubstrByWidth(line.getString(), name.w()),
                name.x(),
                name.y() + 1,
                status.hasDiscs() ? COLOR_TEXT : COLOR_HINT,
                false);
        if (status.hasDiscs()) {
            Rect count = layout.countRect();
            graphics.drawString(
                    minecraft.font,
                    Component.translatable("jukebox.mcfloatingbackpack.track", status.number(), status.count()),
                    count.x(),
                    count.y() + 1,
                    COLOR_HINT,
                    false);
        }

        Button hoveredButton = layout.buttonAt(mouseX, mouseY);
        for (Button button : Button.values()) {
            drawButton(graphics, layout.buttonRect(button), button, status, hoveredButton == button);
        }
        Toggle hoveredToggle = layout.toggleAt(mouseX, mouseY);
        for (Toggle toggle : Toggle.values()) {
            boolean on = toggle == Toggle.LOOP_ONE ? status.loopOne() : status.shuffle();
            drawToggle(graphics, minecraft, layout.toggleRect(toggle), toggle, on, hoveredToggle == toggle);
        }

        if (hoveredButton != null) {
            String key = switch (hoveredButton) {
                case PREVIOUS -> "jukebox.mcfloatingbackpack.previous";
                case PLAY_STOP -> status.playing() ? "jukebox.mcfloatingbackpack.stop" : "jukebox.mcfloatingbackpack.play";
                case REWIND -> "jukebox.mcfloatingbackpack.rewind";
                case NEXT -> "jukebox.mcfloatingbackpack.next";
            };
            tooltip(graphics, minecraft, Component.translatable(key), mouseX, mouseY);
        } else if (hoveredToggle != null) {
            boolean on = hoveredToggle == Toggle.LOOP_ONE ? status.loopOne() : status.shuffle();
            Component name2 = Component.translatable(
                    hoveredToggle == Toggle.LOOP_ONE ? "jukebox.mcfloatingbackpack.loop_one" : "jukebox.mcfloatingbackpack.shuffle");
            tooltip(graphics, minecraft, name2.copy().append(": ").append(Component.translatable(
                    on ? "jukebox.mcfloatingbackpack.on" : "jukebox.mcfloatingbackpack.off")), mouseX, mouseY);
        }
    }

    private static void tooltip(GuiGraphics graphics, Minecraft minecraft, Component text, int mouseX, int mouseY) {
        graphics.renderTooltip(minecraft.font, minecraft.font.split(text, TOOLTIP_WIDTH), mouseX, mouseY);
    }

    /** A raised square: lit at the top left, dark at the bottom right; the pressed look is the hover color. */
    private static void raised(GuiGraphics graphics, Rect r, int background) {
        graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + r.h(), background);
        graphics.fill(r.x(), r.y(), r.x() + r.w(), r.y() + 1, COLOR_LIGHT);
        graphics.fill(r.x(), r.y(), r.x() + 1, r.y() + r.h(), COLOR_LIGHT);
        graphics.fill(r.x(), r.y() + r.h() - 1, r.x() + r.w(), r.y() + r.h(), COLOR_DARK);
        graphics.fill(r.x() + r.w() - 1, r.y(), r.x() + r.w(), r.y() + r.h(), COLOR_DARK);
    }

    private static void drawButton(GuiGraphics graphics, Rect r, Button button, JukeboxPlayback.Status status, boolean hovered) {
        boolean enabled = status.hasDiscs();
        raised(graphics, r, !enabled ? COLOR_BG_OFF : hovered ? COLOR_BG_HOVER : COLOR_BG);
        int color = enabled ? COLOR_GLYPH : COLOR_GLYPH_OFF;
        int cx = r.x() + r.w() / 2;
        int cy = r.y() + r.h() / 2;
        switch (button) {
            case PREVIOUS -> {
                graphics.fill(cx - 4, cy - 3, cx - 3, cy + 4, color);
                triangleLeft(graphics, cx - 2, cy - 3, color);
            }
            case NEXT -> {
                triangleRight(graphics, cx - 2, cy - 3, color);
                graphics.fill(cx + 3, cy - 3, cx + 4, cy + 4, color);
            }
            case REWIND -> {
                triangleLeft(graphics, cx - 4, cy - 3, color);
                triangleLeft(graphics, cx, cy - 3, color);
            }
            case PLAY_STOP -> {
                if (status.playing()) {
                    graphics.fill(cx - 3, cy - 3, cx + 4, cy + 4, color);
                } else {
                    triangleRight(graphics, cx - 2, cy - 4, color, 9);
                }
            }
        }
    }

    private static void drawToggle(GuiGraphics graphics, Minecraft minecraft, Rect r, Toggle toggle, boolean on, boolean hovered) {
        raised(graphics, r, on ? COLOR_BG_ON : hovered ? COLOR_BG_HOVER : COLOR_BG);
        Component label = Component.translatable(
                toggle == Toggle.LOOP_ONE ? "jukebox.mcfloatingbackpack.loop_one.label" : "jukebox.mcfloatingbackpack.shuffle.label");
        String text = minecraft.font.plainSubstrByWidth(label.getString(), r.w() - 4);
        graphics.drawString(
                minecraft.font,
                text,
                r.x() + (r.w() - minecraft.font.width(text)) / 2,
                r.y() + (r.h() - minecraft.font.lineHeight) / 2 + 1,
                COLOR_GLYPH,
                false);
    }

    /** A triangle pointing right, seven pixels high and four wide, its top left at ({@code x}, {@code y}). */
    private static void triangleRight(GuiGraphics graphics, int x, int y, int color) {
        triangleRight(graphics, x, y, color, 7);
    }

    private static void triangleRight(GuiGraphics graphics, int x, int y, int color, int height) {
        int columns = (height + 1) / 2;
        for (int i = 0; i < columns; i++) {
            graphics.fill(x + i, y + i, x + i + 1, y + height - i, color);
        }
    }

    /** A triangle pointing left, seven pixels high and four wide, its top left at ({@code x}, {@code y}). */
    private static void triangleLeft(GuiGraphics graphics, int x, int y, int color) {
        for (int i = 0; i < 4; i++) {
            graphics.fill(x + 3 - i, y + i, x + 4 - i, y + 7 - i, color);
        }
    }
}
