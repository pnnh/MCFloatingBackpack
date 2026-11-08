package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.FloatingPanelEvents;
import pnnh.mcfloatingbackpack.client.panel.ClientBackpackView;
import pnnh.mcfloatingbackpack.client.panel.PanelLayout;
import pnnh.mcfloatingbackpack.client.panel.tags.TagOverviewModel.Row;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.tags.TagOverviewRequest.Op;
import pnnh.mcfloatingbackpack.inventory.tags.TagRules;
import pnnh.mcfloatingbackpack.network.BackpackTagOverviewPayload;

/**
 * 025: the overview's side of the mouse and the keyboard. While the overview is up it covers the main area and takes every
 * press and wheel turn there. The keyboard is taken only while a text box has the focus: the filter box on the list page,
 * the add box on the detail page. Changes are only asked of the server; what is shown follows the tags the server syncs
 * back (no guessing on the client).
 */
@OnlyIn(Dist.CLIENT)
public final class TagOverviewInput {
    private TagOverviewInput() {
    }

    /** A text box the keys can drive: the filter box or the detail page's add box. */
    private interface Box {
        TagTextInput input();

        int dropdownIndex();

        void setDropdownIndex(int index);

        void stopEditing();

        List<String> dropdown();

        void commit(String tag);
    }

    private static final Box FILTER = new Box() {
        @Override
        public TagTextInput input() {
            return TagOverviewState.get().filterInput();
        }

        @Override
        public int dropdownIndex() {
            return TagOverviewState.get().filterDropdownIndex();
        }

        @Override
        public void setDropdownIndex(int index) {
            TagOverviewState.get().setFilterDropdownIndex(index);
        }

        @Override
        public void stopEditing() {
            TagOverviewState.get().stopFilterEditing();
        }

        @Override
        public List<String> dropdown() {
            return TagOverviewContext.filterDropdown();
        }

        @Override
        public void commit(String tag) {
            chooseFilter(tag);
        }
    };

    private static final Box DETAIL = new Box() {
        @Override
        public TagTextInput input() {
            return TagPanelState.get().input();
        }

        @Override
        public int dropdownIndex() {
            return TagPanelState.get().dropdownIndex();
        }

        @Override
        public void setDropdownIndex(int index) {
            TagPanelState.get().setDropdownIndex(index);
        }

        @Override
        public void stopEditing() {
            TagPanelState.get().stopEditing();
        }

        @Override
        public List<String> dropdown() {
            return TagOverviewContext.detailDropdown();
        }

        @Override
        public void commit(String tag) {
            addToDetail(tag);
        }
    };

    private static Box activeBox() {
        return TagOverviewState.get().view() == TagOverviewState.View.DETAIL ? DETAIL : FILTER;
    }

    // ---------------------------------------------------------------- mouse

    /** A press on the main area while the overview covers it. @return true: it is always taken. */
    public static boolean onMainPress(int button, double mouseX, double mouseY) {
        TagOverviewState state = TagOverviewState.get();
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            return true;
        }
        PanelLayout.Rect area = ClientBackpackView.get().layout().rect(PanelArea.MAIN);
        double lx = mouseX - area.x();
        double ly = mouseY - area.y();
        if (TagOverviewLayout.menuIconRect(area.w()).contains(lx, ly)) {
            state.toSelection();
            return true;
        }
        switch (state.view()) {
            case LIST -> pressList(area, lx, ly);
            case DETAIL -> pressDetail(area, lx, ly);
            default -> {
            }
        }
        return true;
    }

    private static void pressList(PanelLayout.Rect area, double lx, double ly) {
        TagOverviewState state = TagOverviewState.get();
        List<Row<ResourceLocation>> rows = TagOverviewContext.rows();
        TagOverviewLayout layout = TagOverviewContext.layout(area.w(), area.h(), rows.size());
        TagOverviewLayout.Hit hit = layout.hit(lx, ly);
        long now = System.currentTimeMillis();
        if (hit.kind() == TagOverviewLayout.Kind.CLEAR && hit.index() < rows.size()) {
            ResourceLocation key = rows.get(hit.index()).key();
            state.stopFilterEditing();
            if (state.confirm().confirm(key.toString(), now)) {
                send(Op.CLEAR, key, "");
            } else {
                state.confirm().arm(key.toString(), now);
            }
            return;
        }
        // Anything but a click on a clear button withdraws a pending confirmation.
        state.confirm().cancel();
        switch (hit.kind()) {
            case FILTER -> {
                if (!state.isFilterEditing()) {
                    state.startFilterEditing();
                } else {
                    placeCaret(state.filterInput(), lx, layout.filterRect().x());
                }
            }
            case FILTER_CLEAR -> {
                state.clearFilter();
                state.setListScroll(0);
            }
            case DROPDOWN -> {
                List<String> offered = TagOverviewContext.filterDropdown();
                if (hit.index() < offered.size()) {
                    chooseFilter(offered.get(hit.index()));
                }
            }
            case ROW -> {
                if (hit.index() < rows.size()) {
                    state.stopFilterEditing();
                    state.enterDetail(rows.get(hit.index()).key().toString());
                }
            }
            default -> state.stopFilterEditing();
        }
    }

    private static void pressDetail(PanelLayout.Rect area, double lx, double ly) {
        TagPanelState tags = TagPanelState.get();
        if (ly < TagPanelLayout.ROW && TagOverviewContext.BACK_RECT.contains(lx, ly)) {
            TagOverviewState.get().backToList();
            return;
        }
        ResourceLocation key = TagOverviewContext.detailKey();
        if (key == null) {
            return;
        }
        TagPanelModel<ResourceLocation> model = TagOverviewContext.detailModel();
        TagPanelLayout layout = TagOverviewContext.detailLayout(area.w(), area.h(), model);
        TagPanelLayout.Hit hit = layout.hit(lx, ly);
        switch (hit.kind()) {
            case TAG -> {
                tags.stopEditing();
                tags.setPendingDelete(model.commonTags().get(hit.index()));
            }
            case DELETE -> {
                String tag = model.commonTags().get(hit.index());
                tags.setPendingDelete(null);
                send(Op.REMOVE, key, tag);
            }
            case PLUS -> {
                if (model.canAdd()) {
                    tags.startEditing();
                }
            }
            case TEXTBOX -> placeCaret(tags.input(), lx, layout.textBoxRect().x());
            case DROPDOWN -> {
                // Clicking an entry adds that tag at once (the keyboard way is arrows + Enter).
                List<String> offered = TagOverviewContext.detailDropdown();
                if (hit.index() < offered.size()) {
                    addToDetail(offered.get(hit.index()));
                }
            }
            default -> tags.cancelTransient();
        }
    }

    /** The wheel over the main area while the overview covers it. @return true: it is always taken. */
    public static boolean onMainScroll(double scrollDeltaY) {
        TagOverviewState state = TagOverviewState.get();
        // One row per notch, up for wheel up; the layout clamps it to what the content allows.
        int delta = scrollDeltaY > 0 ? -1 : 1;
        if (state.view() == TagOverviewState.View.LIST) {
            state.confirm().cancel();
            state.setListScroll(state.listScroll() + delta);
        } else if (state.view() == TagOverviewState.View.DETAIL) {
            TagPanelState tags = TagPanelState.get();
            tags.setScroll(tags.scroll() + delta);
        }
        return true;
    }

    // ---------------------------------------------------------------- keyboard

    /** A key pressed while a text box has the focus; it is always taken. */
    public static boolean onKeyPressed(int keyCode, int scanCode, int modifiers) {
        Box box = activeBox();
        TagTextInput input = box.input();
        switch (keyCode) {
            case GLFW.GLFW_KEY_ESCAPE -> box.stopEditing();
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> commit(box);
            case GLFW.GLFW_KEY_BACKSPACE -> {
                input.backspace();
                box.setDropdownIndex(-1);
            }
            case GLFW.GLFW_KEY_DELETE -> {
                input.delete();
                box.setDropdownIndex(-1);
            }
            case GLFW.GLFW_KEY_LEFT -> input.left();
            case GLFW.GLFW_KEY_RIGHT -> input.right();
            case GLFW.GLFW_KEY_HOME -> input.home();
            case GLFW.GLFW_KEY_END -> input.end();
            case GLFW.GLFW_KEY_DOWN -> moveDropdown(box, 1);
            case GLFW.GLFW_KEY_UP -> moveDropdown(box, -1);
            default -> {
                if (Screen.isPaste(keyCode)) {
                    input.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
                    box.setDropdownIndex(-1);
                }
            }
        }
        return true;
    }

    /** A character typed while a text box has the focus; it is always taken. */
    public static boolean onCharTyped(char c) {
        Box box = activeBox();
        box.input().type(c);
        box.setDropdownIndex(-1);
        return true;
    }

    private static void moveDropdown(Box box, int delta) {
        int count = Math.min(box.dropdown().size(), TagPanelLayout.DROPDOWN_MAX);
        int next = box.dropdownIndex() + delta;
        box.setDropdownIndex(Math.max(-1, Math.min(count - 1, next)));
    }

    /** Enter: takes the highlighted entry, or else the typed text if it is a valid tag. */
    private static void commit(Box box) {
        List<String> dropdown = box.dropdown();
        int index = box.dropdownIndex();
        String tag = index >= 0 && index < Math.min(dropdown.size(), TagPanelLayout.DROPDOWN_MAX)
                ? dropdown.get(index)
                : box.input().text();
        box.commit(tag);
    }

    // ---------------------------------------------------------------- actions

    /** Makes the tag the list's filter (if it is valid) and leaves the filter box. */
    private static void chooseFilter(String tag) {
        TagOverviewState state = TagOverviewState.get();
        if (!TagRules.isValid(tag)) {
            return;
        }
        state.setFilter(tag);
        state.filterInput().clear();
        state.stopFilterEditing();
        state.setListScroll(0);
    }

    /** Asks the server to add the tag to the detail page's item (if it is valid, allowed and there is room), then leaves the box. */
    private static void addToDetail(String tag) {
        TagPanelState tags = TagPanelState.get();
        ResourceLocation key = TagOverviewContext.detailKey();
        if (key == null || !TagRules.isValid(tag)) {
            return;
        }
        TagPanelModel<ResourceLocation> model = TagOverviewContext.detailModel();
        if (model.blocks(tag)) {
            // 022: the item is protected and this is the trash tag. Nothing is sent; the box stays so another tag can be typed.
            FloatingPanelEvents.chat(Minecraft.getInstance(), TagPanelContext.protectedMessage(model));
            return;
        }
        if (!model.canAdd()) {
            tags.stopEditing();
            return;
        }
        send(Op.ADD, key, tag);
        tags.input().clear();
        tags.stopEditing();
    }

    private static void placeCaret(TagTextInput input, double localX, int boxX) {
        Font font = Minecraft.getInstance().font;
        double relative = localX - boxX - 3;
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

    private static void send(Op op, ResourceLocation key, String tag) {
        PacketDistributor.sendToServer(BackpackTagOverviewPayload.of(op, key, tag));
    }
}
