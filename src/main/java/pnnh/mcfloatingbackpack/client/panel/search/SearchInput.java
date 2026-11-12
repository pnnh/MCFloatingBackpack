package pnnh.mcfloatingbackpack.client.panel.search;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.client.panel.SearchBoxLayout;
import pnnh.mcfloatingbackpack.client.panel.TitleBarLayout;

/**
 * 042: the search box's keyboard and mouse. While the box has the focus the keyboard is the box's: every key and character is taken
 * here (the caller cancels the event), so B, E, T, Delete and the rest do nothing else; without the focus nothing is taken. Esc
 * clears the box and closes it; a press outside it only takes the focus away. The text may hold any character.
 */
@OnlyIn(Dist.CLIENT)
public final class SearchInput {
    private SearchInput() {
    }

    /** A key pressed while the box has the focus; it is always taken. */
    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        SearchState state = SearchState.get();
        SearchTextInput input = state.input();
        switch (keyCode) {
            case InputConstants.KEY_ESCAPE -> {
                state.close(SearchContext.source());
                return true;
            }
            case InputConstants.KEY_BACKSPACE -> input.backspace();
            case InputConstants.KEY_DELETE -> input.delete();
            case InputConstants.KEY_LEFT -> input.left();
            case InputConstants.KEY_RIGHT -> input.right();
            case InputConstants.KEY_HOME -> input.home();
            case InputConstants.KEY_END -> input.end();
            default -> {
                if (Screen.isSelectAll(keyCode)) {
                    input.selectAll();
                } else if (Screen.isPaste(keyCode)) {
                    input.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
                }
            }
        }
        state.textChanged(SearchContext.source());
        return true;
    }

    /** A character typed while the box has the focus (a code point: Chinese typed through an input method comes in this way). */
    public static boolean onCharTyped(int codePoint) {
        SearchState state = SearchState.get();
        state.input().insert(codePoint);
        state.textChanged(SearchContext.source());
        return true;
    }

    /**
     * A left press in the title bar, before the close button and the drag bar see it.
     *
     * @return true when the press belonged to the search (the magnifier, the clear button or the box)
     */
    public static boolean onPress(FloatingPanelState panel, double mouseX, double mouseY) {
        SearchState state = SearchState.get();
        if (!state.isOpen()) {
            if (panel.hitsSearchButton(mouseX, mouseY)) {
                state.openBox();
                return true;
            }
            return false;
        }
        if (panel.hitsSearchClear(mouseX, mouseY)) {
            state.close(SearchContext.source());
            return true;
        }
        if (panel.hitsSearchBox(mouseX, mouseY)) {
            state.focus();
            placeCaret(panel, mouseX);
            return true;
        }
        return false;
    }

    /** Any press that is not in the box takes the keyboard away from it (the box and its results stay). */
    public static void loseFocusIfOutside(FloatingPanelState panel, double mouseX, double mouseY) {
        SearchState state = SearchState.get();
        if (state.isFocused() && !panel.hitsSearchBox(mouseX, mouseY)) {
            state.loseFocus();
        }
    }

    private static void placeCaret(FloatingPanelState panel, double mouseX) {
        Minecraft minecraft = Minecraft.getInstance();
        SearchTextInput input = SearchState.get().input();
        String text = input.text();
        TitleBarLayout bar = panel.titleBar();
        int countWidth = minecraft.font.width(String.valueOf(SearchState.get().liveCount(SearchContext.source())));
        SearchBoxLayout inside = SearchBoxLayout.of(bar.boxLeft(), bar.boxRight(), countWidth);
        int areaWidth = inside.textRight() - inside.textLeft();
        int start = SearchTextView.windowStart(text, input.caret(), areaWidth, minecraft.font::width);
        input.setCaret(SearchTextView.caretAt(text, start, (int) mouseX - inside.textLeft(), minecraft.font::width));
    }
}
