package pnnh.mcfloatingbackpack.client.snapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelRenderer;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenLayout.Layout;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenLayout.Rect;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenState.Pending;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenState.Preview;
import pnnh.mcfloatingbackpack.client.snapshot.SnapshotScreenState.Status;
import pnnh.mcfloatingbackpack.inventory.PanelArea;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIds;
import pnnh.mcfloatingbackpack.item.BackpackTier;
import pnnh.mcfloatingbackpack.snapshot.AgeText;
import pnnh.mcfloatingbackpack.snapshot.SnapshotRequestRules;
import pnnh.mcfloatingbackpack.snapshot.net.ActionKind;
import pnnh.mcfloatingbackpack.snapshot.net.ResultCode;
import pnnh.mcfloatingbackpack.snapshot.net.Row;
import pnnh.mcfloatingbackpack.snapshot.net.Scope;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotActionPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotListPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotListRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotPreviewPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotPreviewRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotResultPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotVersionsPayload;
import pnnh.mcfloatingbackpack.snapshot.net.SnapshotVersionsRequestPayload;
import pnnh.mcfloatingbackpack.snapshot.net.VersionRow;

/**
 * 031: the snapshot window, the graphical version of {@code /mcfb snapshot}. A screen of the mod's own: it needs no backpack, and it
 * does not replace, wrap or extend any other screen (the floating panel is simply hidden while it is open, as on any screen that
 * is not a container, and comes back as it was). Everything shown comes from the server, which decides who may see what; this
 * class only draws {@link SnapshotScreenState} and turns clicks into requests. What only permission level 2 may do is not
 * built at all for anyone else: the server's reply says which of the two this player is.
 */
@OnlyIn(Dist.CLIENT)
public final class SnapshotScreen extends Screen {
    private static final String K = "gui.mcfloatingbackpack.snapshot.";
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_TEXT_DIM = 0xFF707070;
    private static final int COLOR_ERROR = 0xFFA02020;
    private static final int COLOR_OK = 0xFF206020;
    private static final int COLOR_SELECTED = 0xFFFFD34D;
    private static final int COLOR_ROW_HOVER = 0x40FFFFFF;

    private final SnapshotScreenState<List<ItemStack>> state = new SnapshotScreenState<>();
    private Layout layout;
    private int previewScroll;

    private Button prev;
    private Button next;
    private Button scopeButton;
    private EditBox ownerBox;
    private EditBox daysBox;
    private Button restoreButton;
    private Button targetButton;
    private Button deleteOneButton;
    private Button deleteAllButton;
    private EditBox trimBox;
    private Button trimPreviewButton;
    /** The structure the widgets were last built for: when it changes they are built again. */
    private boolean builtOp;
    private Pending builtPending = Pending.NONE;
    private boolean requested;

    public SnapshotScreen() {
        super(Component.translatable(K + "title"));
    }

    // ---------------------------------------------------------------- building

    @Override
    protected void init() {
        layout = SnapshotScreenLayout.of(width, height, state.isOp());
        builtOp = state.isOp();
        builtPending = state.pending();

        addRenderableWidget(Button.builder(Component.literal("x"), button -> onClose())
                .bounds(layout.close().x(), layout.close().y(), layout.close().w(), layout.close().h())
                .build());

        Rect pager = layout.pager();
        int half = Math.max(12, Math.min(20, pager.w() / 4));
        prev = addRenderableWidget(Button.builder(Component.translatable(K + "button.prev"), button -> goToPage(state.page() - 1))
                .bounds(pager.x(), pager.y(), half, pager.h())
                .build());
        next = addRenderableWidget(Button.builder(Component.translatable(K + "button.next"), button -> goToPage(state.page() + 1))
                .bounds(pager.right() - half, pager.y(), half, pager.h())
                .build());

        scopeButton = null;
        ownerBox = null;
        daysBox = null;
        restoreButton = null;
        targetButton = null;
        deleteOneButton = null;
        deleteAllButton = null;
        trimBox = null;
        trimPreviewButton = null;
        if (state.isOp()) {
            buildOpRow();
            buildActions();
        }

        if (state.setPageSize(layout.listRows()) || !requested) {
            requested = true;
            requestList();
        }
    }

    /** The scope switch and the two filters (permission level 2 only). */
    private void buildOpRow() {
        Rect row = layout.opRow();
        if (row.isEmpty()) {
            return;
        }
        int x = row.x();
        int w = row.w();
        int gap = 4;
        int scopeW = Math.min(60, w / 4);
        int ownerW = Math.min(90, (w - scopeW - 2 * gap) * 2 / 3);
        int daysW = Math.max(20, w - scopeW - ownerW - 2 * gap);
        scopeButton = addRenderableWidget(Button.builder(scopeLabel(), button -> {
                    if (state.setScope(state.scope() == Scope.MINE ? Scope.ALL : Scope.MINE)) {
                        button.setMessage(scopeLabel());
                        requestList();
                    }
                })
                .bounds(x, row.y(), scopeW, row.h())
                .build());
        ownerBox = new EditBox(font, x + scopeW + gap, row.y(), ownerW, row.h(), Component.translatable(K + "owner.hint"));
        ownerBox.setMaxLength(SnapshotRequestRules.MAX_NAME);
        ownerBox.setHint(Component.translatable(K + "owner.hint"));
        ownerBox.setValue(state.ownerFilter());
        ownerBox.setFilter(text -> SnapshotRequestRules.validName(text));
        addRenderableWidget(ownerBox);
        daysBox = new EditBox(font, x + scopeW + ownerW + 2 * gap, row.y(), daysW, row.h(), Component.translatable(K + "days.hint"));
        daysBox.setMaxLength(5);
        daysBox.setHint(Component.translatable(K + "days.hint"));
        daysBox.setValue(state.inactiveDays() < 0 ? "" : Integer.toString(state.inactiveDays()));
        daysBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        addRenderableWidget(daysBox);
    }

    /** Restore, delete and clean-up (permission level 2 only), or the question that is waiting for a yes. */
    private void buildActions() {
        Rect row = layout.actions();
        if (row.isEmpty()) {
            return;
        }
        int gap = 4;
        if (state.pending() != Pending.NONE) {
            int w = Math.min(90, (row.w() - gap) / 2);
            addRenderableWidget(Button.builder(Component.translatable(K + "button.confirm"), button -> confirm())
                    .bounds(row.x(), row.y(), w, row.h())
                    .build());
            addRenderableWidget(Button.builder(Component.translatable(K + "button.cancel"), button -> {
                        state.cancelPending();
                        rebuildWidgets();
                    })
                    .bounds(row.x() + w + gap, row.y(), w, row.h())
                    .build());
            return;
        }
        // five buttons and the clean-up's days box share the row; they shrink together when it is narrow
        int[] want = {56, 78, 76, 66, 30, 52};
        int total = 0;
        for (int w : want) {
            total += w;
        }
        int room = row.w() - gap * (want.length - 1);
        double scale = total > room ? (double) room / total : 1.0;
        int x = row.x();
        int[] widths = new int[want.length];
        for (int i = 0; i < want.length; i++) {
            widths[i] = Math.max(16, (int) (want[i] * scale));
        }
        restoreButton = addRenderableWidget(Button.builder(Component.translatable(K + "button.restore"), button -> restore())
                .bounds(x, row.y(), widths[0], row.h())
                .build());
        x += widths[0] + gap;
        targetButton = addRenderableWidget(Button.builder(targetLabel(), button -> {
                    if (state.cycleTarget(onlinePlayers(), selfName())) {
                        button.setMessage(targetLabel());
                    }
                })
                .bounds(x, row.y(), widths[1], row.h())
                .build());
        x += widths[1] + gap;
        deleteOneButton = addRenderableWidget(Button.builder(Component.translatable(K + "button.delete_one"), button -> deleteOne())
                .bounds(x, row.y(), widths[2], row.h())
                .build());
        x += widths[2] + gap;
        deleteAllButton = addRenderableWidget(Button.builder(Component.translatable(K + "button.delete_all"), button -> {
                    if (state.beginDeleteAll()) {
                        rebuildWidgets();
                    }
                })
                .bounds(x, row.y(), widths[3], row.h())
                .build());
        x += widths[3] + gap;
        trimBox = new EditBox(font, x, row.y(), widths[4], row.h(), Component.translatable(K + "button.trim"));
        trimBox.setMaxLength(5);
        trimBox.setHint(Component.translatable(K + "trim.hint"));
        trimBox.setFilter(text -> text.chars().allMatch(Character::isDigit));
        addRenderableWidget(trimBox);
        x += widths[4] + gap;
        trimPreviewButton = addRenderableWidget(Button.builder(Component.translatable(K + "button.trim"), button -> trimPreview())
                .bounds(x, row.y(), widths[5], row.h())
                .build());
    }

    private Component scopeLabel() {
        return Component.translatable(K + (state.scope() == Scope.ALL ? "scope.all" : "scope.mine"));
    }

    private Component targetLabel() {
        String target = state.target();
        return Component.translatable(K + "button.target", target.isEmpty() ? Component.translatable(K + "target.self") : Component.literal(target));
    }

    private List<String> onlinePlayers() {
        List<String> names = new ArrayList<>();
        if (minecraft != null && minecraft.getConnection() != null) {
            for (PlayerInfo info : minecraft.getConnection().getOnlinePlayers()) {
                names.add(info.getProfile().getName());
            }
        }
        return names;
    }

    private String selfName() {
        return minecraft != null && minecraft.player != null ? minecraft.player.getGameProfile().getName() : "";
    }

    // ---------------------------------------------------------------- requests

    private void requestList() {
        PacketDistributor.sendToServer(new SnapshotListRequestPayload(
                state.scope().code(), state.ownerFilter(), state.inactiveDays(), state.page(), state.pageSize()));
    }

    private void requestVersions() {
        if (state.selected() != null) {
            PacketDistributor.sendToServer(new SnapshotVersionsRequestPayload(state.selected()));
        }
    }

    private void requestPreview() {
        Long time = state.versionTime();
        if (state.selected() != null && time != null) {
            PacketDistributor.sendToServer(new SnapshotPreviewRequestPayload(state.selected(), time));
        }
    }

    private void goToPage(int number) {
        if (state.gotoPage(number)) {
            requestList();
        }
    }

    private void applyFilters() {
        if (ownerBox == null || daysBox == null) {
            return;
        }
        boolean changed = state.setOwnerFilter(ownerBox.getValue());
        String days = daysBox.getValue();
        int parsed = days.isEmpty() ? -1 : parseDays(days);
        if (parsed == 0 || !SnapshotRequestRules.validFilterDays(parsed)) {
            parsed = state.inactiveDays();
        }
        changed |= state.setInactiveDays(parsed);
        if (changed) {
            requestList();
        }
    }

    private static int parseDays(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void restore() {
        Long time = state.versionTime();
        if (state.canRestore() && time != null) {
            PacketDistributor.sendToServer(SnapshotActionPayload.restore(state.selected(), time, state.target()));
        }
    }

    private void deleteOne() {
        Long time = state.versionTime();
        if (state.canDeleteOne() && time != null) {
            PacketDistributor.sendToServer(SnapshotActionPayload.deleteOne(state.selected(), time));
        }
    }

    private void trimPreview() {
        int days = trimDays();
        if (state.canTrimPreview(days)) {
            PacketDistributor.sendToServer(SnapshotActionPayload.trim(ActionKind.TRIM_PREVIEW, days));
        }
    }

    /** The days typed into the clean-up box, or -1 if there are none. */
    private int trimDays() {
        if (trimBox == null || trimBox.getValue().isEmpty()) {
            return -1;
        }
        return parseDays(trimBox.getValue());
    }

    private void confirm() {
        switch (state.pending()) {
            case DELETE_ALL -> {
                if (state.selected() != null) {
                    PacketDistributor.sendToServer(SnapshotActionPayload.deleteAll(state.selected()));
                }
            }
            case TRIM -> PacketDistributor.sendToServer(SnapshotActionPayload.trim(ActionKind.TRIM_CONFIRM, state.pendingDays()));
            default -> {
            }
        }
        state.cancelPending();
        rebuildWidgets();
    }

    // ---------------------------------------------------------------- what the server says

    void onList(SnapshotListPayload payload) {
        boolean wasOp = state.isOp();
        Scope scope = Scope.fromByte(payload.scope());
        state.applyList(payload.op(), scope == null ? Scope.MINE : scope, payload.page(), payload.pages(), payload.total(), payload.rows());
        if (wasOp != state.isOp() || builtPending != state.pending()) {
            rebuildWidgets();
        } else if (scopeButton != null) {
            scopeButton.setMessage(scopeLabel());
        }
    }

    void onVersions(SnapshotVersionsPayload payload) {
        state.applyVersions(payload.backpack(), payload.versions());
        previewScroll = 0;
    }

    void onPreview(SnapshotPreviewPayload payload) {
        if (payload.reason() != null) {
            state.applyPreviewFailure(payload.backpack(), payload.time(), payload.reason());
        } else {
            state.applyPreview(
                    payload.backpack(), payload.time(), new Preview<>(payload.time(), payload.capacity(), payload.slots(), payload.items()));
        }
        previewScroll = 0;
    }

    void onResult(SnapshotResultPayload payload) {
        state.setStatus(new Status(payload.code(), payload.a(), payload.b(), payload.n1(), payload.n2()));
        switch (payload.code()) {
            case TRIM_PREVIEWED -> {
                if (payload.n1() > 0 && state.beginTrim(trimDays(), payload.n1(), payload.n2())) {
                    rebuildWidgets();
                }
            }
            case DELETED_ONE, DELETED_ALL, TRIM_DONE -> {
                requestList();
                requestVersions();
            }
            default -> {
            }
        }
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            int at = rowAt(mouseX, mouseY);
            if (at >= 0 && at < state.rows().size()) {
                Row row = state.rows().get(at);
                if (state.select(row.uuid())) {
                    previewScroll = 0;
                    requestVersions();
                }
                return true;
            }
            int version = versionAt(mouseX, mouseY);
            if (version >= 1 && state.selectVersion(version)) {
                previewScroll = 0;
                requestPreview();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (layout.preview().contains(mouseX, mouseY) && state.preview() != null) {
            int max = SnapshotScreenLayout.maxScroll(state.preview().items().size(), layout.previewRows());
            previewScroll = Math.max(0, Math.min(max, previewScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (layout.list().contains(mouseX, mouseY)) {
            goToPage(state.page() - (int) Math.signum(scrollY));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == InputConstants.KEY_RETURN || keyCode == InputConstants.KEY_NUMPADENTER;
        if (enter && (ownerBox != null && ownerBox.isFocused() || daysBox != null && daysBox.isFocused())) {
            applyFilters();
            return true;
        }
        if (enter && trimBox != null && trimBox.isFocused()) {
            trimPreview();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The list row under the mouse, or -1. */
    private int rowAt(double mouseX, double mouseY) {
        Rect list = layout.list();
        int rowsTop = list.y();
        int rowsBottom = rowsTop + layout.listRows() * SnapshotScreenLayout.ROW_H;
        if (mouseX < list.x() || mouseX >= list.right() || mouseY < rowsTop || mouseY >= rowsBottom) {
            return -1;
        }
        return (int) ((mouseY - rowsTop) / SnapshotScreenLayout.ROW_H);
    }

    /** The version (from 1) whose row is under the mouse, or 0. */
    private int versionAt(double mouseX, double mouseY) {
        Rect versions = layout.versions();
        int top = versions.y() + SnapshotScreenLayout.VERSION_HEADER_H;
        if (mouseX < versions.x() || mouseX >= versions.right() || mouseY < top) {
            return 0;
        }
        int index = (int) ((mouseY - top) / SnapshotScreenLayout.VERSION_ROW_H);
        return index < layout.versionRows() && index < state.versions().size() ? index + 1 : 0;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        // the structure of the controls follows the permission and the question; their state follows the selection
        if (builtOp != state.isOp() || builtPending != state.pending()) {
            rebuildWidgets();
        }
        prev.active = state.page() > 1;
        next.active = state.page() < state.pages();
        if (restoreButton != null) {
            restoreButton.active = state.canRestore();
            deleteOneButton.active = state.canDeleteOne();
            deleteAllButton.active = state.canDeleteAll();
            trimPreviewButton.active = state.canTrimPreview(trimDays());
        }
    }

    /** Everything that is not a control is drawn here, so it lies over the dimmed background and under the controls. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        Rect dialog = layout.dialog();
        FloatingPanelRenderer.drawVanillaPanel(graphics, dialog.x(), dialog.y(), dialog.w(), dialog.h());
        graphics.drawString(font, title, dialog.x() + SnapshotScreenLayout.PAD, dialog.y() + 5, COLOR_TEXT, false);
        drawList(graphics, mouseX, mouseY);
        drawVersions(graphics);
        drawPreview(graphics, mouseX, mouseY);
        drawStatus(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        drawTooltips(graphics, mouseX, mouseY);
    }

    private static Component tierText(int capacity) {
        BackpackTier tier = BackpackTier.byCapacity(capacity);
        return tier == null ? Component.literal("?") : Component.translatable(K + "tier." + tier.name().toLowerCase(Locale.ROOT));
    }

    private Component rowText(Row row, long now) {
        return Component.translatable(
                K + "list.row",
                BackpackIds.of(row.uuid()),
                tierText(row.capacity()),
                row.versions(),
                row.ownerName(),
                AgeText.ago(now - row.newestTime()));
    }

    private void drawList(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect list = layout.list();
        long now = System.currentTimeMillis();
        if (state.rows().isEmpty()) {
            graphics.drawString(font, Component.translatable(K + "list.empty"), list.x() + 2, list.y() + 2, COLOR_TEXT_DIM, false);
        }
        int hovered = rowAt(mouseX, mouseY);
        for (int i = 0; i < state.rows().size() && i < layout.listRows(); i++) {
            Row row = state.rows().get(i);
            int y = list.y() + i * SnapshotScreenLayout.ROW_H;
            if (row.uuid().equals(state.selected())) {
                graphics.fill(list.x(), y, list.right(), y + SnapshotScreenLayout.ROW_H, 0x60FFD34D);
            } else if (i == hovered) {
                graphics.fill(list.x(), y, list.right(), y + SnapshotScreenLayout.ROW_H, COLOR_ROW_HOVER);
            }
            String text = font.plainSubstrByWidth(rowText(row, now).getString(), list.w() - 4);
            graphics.drawString(font, text, list.x() + 2, y + 2, COLOR_TEXT, false);
        }
        Rect pager = layout.pager();
        Component pageText = Component.translatable(K + "page", state.page(), state.pages());
        int center = pager.x() + pager.w() / 2 - font.width(pageText) / 2;
        graphics.drawString(font, pageText, center, pager.y() + 2, COLOR_TEXT, false);
    }

    private void drawVersions(GuiGraphics graphics) {
        Rect versions = layout.versions();
        if (state.selected() == null) {
            graphics.drawString(font, Component.translatable(K + "version.none"), versions.x() + 2, versions.y() + 2, COLOR_TEXT_DIM, false);
            return;
        }
        Row row = selectedRow();
        UUID uuid = state.selected();
        graphics.drawString(
                font,
                font.plainSubstrByWidth(Component.translatable(K + "version.header", BackpackIds.of(uuid), uuid.toString().substring(0, 8)).getString(), versions.w()),
                versions.x() + 2,
                versions.y() + 2,
                COLOR_TEXT,
                false);
        if (row != null) {
            graphics.drawString(
                    font,
                    font.plainSubstrByWidth(
                            Component.translatable(K + "version.owner", tierText(row.capacity()), row.ownerName()).getString(), versions.w()),
                    versions.x() + 2,
                    versions.y() + 12,
                    COLOR_TEXT_DIM,
                    false);
        }
        long now = System.currentTimeMillis();
        int top = versions.y() + SnapshotScreenLayout.VERSION_HEADER_H;
        for (int i = 0; i < state.versions().size() && i < layout.versionRows(); i++) {
            VersionRow version = state.versions().get(i);
            int y = top + i * SnapshotScreenLayout.VERSION_ROW_H;
            if (i + 1 == state.selectedVersion()) {
                graphics.fill(versions.x(), y, versions.right(), y + SnapshotScreenLayout.VERSION_ROW_H, 0x60FFD34D);
            }
            Component text = Component.translatable(K + "version.row", i + 1, AgeText.ago(now - version.time()), version.slots());
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), versions.w() - 4), versions.x() + 2, y + 1, COLOR_TEXT, false);
        }
    }

    private Row selectedRow() {
        for (Row row : state.rows()) {
            if (row.uuid().equals(state.selected())) {
                return row;
            }
        }
        return null;
    }

    private void drawPreview(GuiGraphics graphics, int mouseX, int mouseY) {
        Rect area = layout.preview();
        if (area.isEmpty()) {
            return;
        }
        Preview<List<ItemStack>> preview = state.preview();
        if (preview == null) {
            Component text = state.previewFailure() != null
                    ? Component.translatable(SnapshotResultText.key(state.previewFailure()))
                    : Component.translatable(K + (state.selectedVersion() == 0 ? "preview.none" : "preview.loading"));
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), area.w()), area.x() + 2, area.y() + 2, COLOR_TEXT_DIM, false);
            return;
        }
        int slot = layout.slot();
        int rows = Math.min(layout.previewRows(), SnapshotScreenLayout.gridRows(preview.items().size()));
        int max = SnapshotScreenLayout.maxScroll(preview.items().size(), layout.previewRows());
        previewScroll = Math.max(0, Math.min(max, previewScroll));
        int well = FloatingPanelRenderer.wellColor(PanelArea.MAIN);
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < SnapshotScreenLayout.GRID_COLS; c++) {
                int index = (r + previewScroll) * SnapshotScreenLayout.GRID_COLS + c;
                if (index >= preview.items().size()) {
                    break;
                }
                int x = area.x() + c * slot;
                int y = area.y() + r * slot;
                graphics.fill(x, y, x + slot - 1, y + slot - 1, well);
                ItemStack stack = preview.items().get(index);
                if (!stack.isEmpty()) {
                    drawItem(graphics, stack, x, y, slot);
                }
            }
        }
        if (max > 0) {
            // a thin mark at the right edge says there is more to scroll to
            int trackH = rows * slot;
            int thumbH = Math.max(6, trackH * layout.previewRows() / SnapshotScreenLayout.gridRows(preview.items().size()));
            int thumbY = area.y() + (trackH - thumbH) * previewScroll / max;
            int x = area.x() + SnapshotScreenLayout.GRID_COLS * slot + 1;
            graphics.fill(x, area.y(), x + 2, area.y() + trackH, 0x40000000);
            graphics.fill(x, thumbY, x + 2, thumbY + thumbH, 0xFF404040);
        }
    }

    /** An item in a preview cell: scaled when the cell is smaller than the usual 18 pixels. */
    private void drawItem(GuiGraphics graphics, ItemStack stack, int x, int y, int slot) {
        if (slot >= SnapshotScreenLayout.MAX_SLOT) {
            graphics.renderItem(stack, x + 1, y + 1);
            graphics.renderItemDecorations(font, stack, x + 1, y + 1);
            return;
        }
        float scale = slot / (float) SnapshotScreenLayout.MAX_SLOT;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1.0f);
        graphics.renderItem(stack, 1, 1);
        graphics.renderItemDecorations(font, stack, 1, 1);
        graphics.pose().popPose();
    }

    private void drawStatus(GuiGraphics graphics) {
        Rect area = layout.status();
        Component text = null;
        int color = COLOR_TEXT;
        switch (state.pending()) {
            case DELETE_ALL -> text = Component.translatable(K + "pending.delete_all", state.pendingFiles());
            case TRIM -> text = Component.translatable(K + "pending.trim", state.pendingBackpacks(), state.pendingFiles(), state.pendingDays());
            default -> {
                Status status = state.status();
                if (status != null) {
                    text = Component.translatable(SnapshotResultText.key(status.code()), SnapshotResultText.args(status));
                    color = status.code().success() ? COLOR_OK : COLOR_ERROR;
                }
            }
        }
        if (text != null && !area.isEmpty()) {
            graphics.drawString(font, font.plainSubstrByWidth(text.getString(), area.w()), area.x() + 2, area.y() + 2, color, false);
        }
    }

    private void drawTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        int at = rowAt(mouseX, mouseY);
        if (at >= 0 && at < state.rows().size()) {
            Row row = state.rows().get(at);
            graphics.renderTooltip(font, Component.translatable(K + "list.tooltip", BackpackIds.of(row.uuid()), row.uuid().toString()), mouseX, mouseY);
            return;
        }
        Preview<List<ItemStack>> preview = state.preview();
        Rect area = layout.preview();
        if (preview != null && area.contains(mouseX, mouseY)) {
            int slot = layout.slot();
            int c = (mouseX - area.x()) / slot;
            int r = (mouseY - area.y()) / slot;
            int index = (r + previewScroll) * SnapshotScreenLayout.GRID_COLS + c;
            if (c < SnapshotScreenLayout.GRID_COLS && r < layout.previewRows() && index >= 0 && index < preview.items().size()) {
                ItemStack stack = preview.items().get(index);
                if (!stack.isEmpty()) {
                    graphics.renderTooltip(font, stack, mouseX, mouseY);
                }
            }
        }
    }
}
