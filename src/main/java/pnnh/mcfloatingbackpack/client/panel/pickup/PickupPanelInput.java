package pnnh.mcfloatingbackpack.client.panel.pickup;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.tags.TagPanelLayout;
import pnnh.mcfloatingbackpack.client.panel.tags.TagTextInput;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.pickup.PickupBlacklistRequest.Op;
import pnnh.mcfloatingbackpack.inventory.tags.TagRules;
import pnnh.mcfloatingbackpack.network.BackpackPickupBlacklistOpPayload;

/**
 * 024: the pickup blacklist page's side of the mouse and the keyboard, same as 018's {@code TagPanelInput}. While the page
 * is expanded it covers the main area and takes every press and wheel turn there, so the main area's own gestures
 * (selection, quick transfer, drag) never see them. The keyboard is taken only while the text box has the focus.
 */
@OnlyIn(Dist.CLIENT)
public final class PickupPanelInput {
    private PickupPanelInput() {
    }

    /** A press on the main area while the page covers it. @return true: it is always taken. */
    public static boolean onMainPress(int button, double mouseX, double mouseY) {
        PickupPanelState state = PickupPanelState.get();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        PanelLayout.Rect area = ClientBackpackView.get().layout().rect(PanelArea.MAIN);
        PickupPanelModel model = PickupPanelContext.model();
        TagPanelLayout layout = PickupPanelContext.layout(area.w(), area.h(), model);
        TagPanelLayout.Hit hit = layout.hit(mouseX - area.x(), mouseY - area.y());
        switch (hit.kind()) {
            case TAG -> {
                state.stopEditing();
                state.setPendingDelete(model.tags().get(hit.index()));
            }
            case DELETE -> {
                String tag = model.tags().get(hit.index());
                state.setPendingDelete(null);
                send(Op.REMOVE, tag);
            }
            case PLUS -> {
                if (model.canAdd()) {
                    state.startEditing();
                }
            }
            case TEXTBOX -> placeCaret(layout, mouseX - area.x());
            case DROPDOWN -> {
                // Clicking an entry adds that tag at once (the keyboard way is arrows + Enter).
                List<String> offered = PickupPanelContext.dropdown();
                if (hit.index() < offered.size()) {
                    add(offered.get(hit.index()));
                }
            }
            default -> state.cancelTransient();
        }
        return true;
    }

    /** The wheel over the main area while the page covers it. @return true: it is always taken. */
    public static boolean onMainScroll(double scrollDeltaY) {
        PickupPanelState state = PickupPanelState.get();
        // One row per notch, up for wheel up; the layout clamps it to what the content allows.
        state.setScroll(state.scroll() + (scrollDeltaY > 0 ? -1 : 1));
        return true;
    }

    /** A key pressed while the text box has the focus; it is always taken. */
    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        PickupPanelState state = PickupPanelState.get();
        TagTextInput input = state.input();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> state.stopEditing();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commit();
            case GLFW.GLFW_KEY_BACKSPACE -> {
                input.backspace();
                state.setDropdownIndex(-1);
            }
            case GLFW.GLFW_KEY_DELETE -> {
                input.delete();
                state.setDropdownIndex(-1);
            }
            case GLFW.GLFW_KEY_LEFT -> input.left();
            case GLFW.GLFW_KEY_RIGHT -> input.right();
            case GLFW.GLFW_KEY_HOME -> input.home();
            case GLFW.GLFW_KEY_END -> input.end();
            case GLFW.GLFW_KEY_DOWN -> moveDropdown(1);
            case GLFW.GLFW_KEY_UP -> moveDropdown(-1);
            default -> {
                if (Screen.isPaste(keyCode)) {
                    input.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
                    state.setDropdownIndex(-1);
                }
            }
        }
        return true;
    }

    /** A character typed while the text box has the focus; it is always taken. */
    public static boolean onCharTyped(char c) {
        PickupPanelState state = PickupPanelState.get();
        state.input().type(c);
        state.setDropdownIndex(-1);
        return true;
    }

    private static void moveDropdown(int delta) {
        PickupPanelState state = PickupPanelState.get();
        int count = Math.min(PickupPanelContext.dropdown().size(), TagPanelLayout.DROPDOWN_MAX);
        int next = state.dropdownIndex() + delta;
        state.setDropdownIndex(Math.max(-1, Math.min(count - 1, next)));
    }

    /** Enter: adds the highlighted entry, or else the typed text if it is a valid tag. */
    private static void commit() {
        PickupPanelState state = PickupPanelState.get();
        List<String> dropdown = PickupPanelContext.dropdown();
        int index = state.dropdownIndex();
        String tag = index >= 0 && index < Math.min(dropdown.size(), TagPanelLayout.DROPDOWN_MAX)
                ? dropdown.get(index)
                : state.input().text();
        add(tag);
    }

    /** Adds the tag to the blacklist (if it is valid and there is room), then leaves the text box. */
    private static void add(String tag) {
        PickupPanelState state = PickupPanelState.get();
        if (!TagRules.isValid(tag)) {
            return;
        }
        PickupPanelModel model = PickupPanelContext.model();
        if (!model.canAdd()) {
            state.stopEditing();
            return;
        }
        // Already listed: nothing to send (the server would treat it the same way), just close the box.
        if (!model.tags().contains(tag)) {
            send(Op.ADD, tag);
        }
        state.input().clear();
        state.stopEditing();
    }

    private static void placeCaret(TagPanelLayout layout, double localX) {
        TagTextInput input = PickupPanelState.get().input();
        Font font = Minecraft.getInstance().font;
        double relative = localX - layout.textBoxRect().x() - 3;
        String text = input.text();
        int caret = 0;
        while (caret < text.length() && font.width(text.substring(0, caret + 1)) <= relative) {
            caret++;
        }
        input.home();
        for (int i = 0; i < caret; i++) {
            input.right();
        }
    }

    private static void send(Op op, String tag) {
        PacketDistributor.sendToServer(BackpackPickupBlacklistOpPayload.of(op, tag));
    }
}
