package pnnh.mcfloatingbackpack.client.panel.tags;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * 018: where everything in the tag panel goes. Coordinates are relative to the panel's top-left corner (the main area
 * the panel covers). A status row sits on top; under it the tag blocks flow left to right and wrap, then either the
 * plus button or (when editing) a text box on a row of its own with the drop-down below it. The content scrolls in
 * whole rows under the status row. Pure arithmetic with no Minecraft types: the text width comes from the caller.
 */
public final class TagPanelLayout {
    public static final int ROW = 18;
    public static final int PAD = 2;
    public static final int BLOCK_H = 14;
    public static final int BLOCK_PAD = 4;
    public static final int GAP = 2;
    public static final int DELETE_W = 10;
    public static final int PLUS_W = 14;
    public static final int DROPDOWN_MAX = 5;

    public record Rect(int x, int y, int w, int h) {
        public boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }
    }

    public enum Kind {
        TAG,
        DELETE,
        PLUS,
        TEXTBOX,
        DROPDOWN,
        STATUS,
        NONE
    }

    public record Hit(Kind kind, int index) {
    }

    private final int width;
    private final int height;
    private final Rect status;
    private final List<Rect> tags = new ArrayList<>();
    private final List<Rect> dropdown = new ArrayList<>();
    private Rect delete;
    private Rect none;
    private Rect plus;
    private Rect textBox;
    private int maxScroll;

    private TagPanelLayout(int width, int height) {
        this.width = width;
        this.height = height;
        this.status = new Rect(0, 0, width, ROW);
    }

    /**
     * @param tags the label shown for each tag
     * @param noneLabel the text shown when there are no tags
     * @param dropdownCount how many drop-down entries exist (only shown while editing, at most {@link #DROPDOWN_MAX})
     * @param scroll rows scrolled (clamped to the possible range)
     * @param pendingDeleteIndex the tag showing a delete mark, or -1
     */
    public static TagPanelLayout of(
            int width,
            int height,
            ToIntFunction<String> textWidth,
            List<String> tags,
            String noneLabel,
            boolean editing,
            int dropdownCount,
            int scroll,
            int pendingDeleteIndex) {
        TagPanelLayout layout = new TagPanelLayout(width, height);
        layout.build(textWidth, tags, noneLabel, editing, Math.min(Math.max(0, dropdownCount), DROPDOWN_MAX), pendingDeleteIndex);
        int clamped = Math.min(Math.max(0, scroll), layout.maxScroll);
        layout.shift(-clamped * ROW);
        return layout;
    }

    private static int rowTop(int row) {
        return ROW + row * ROW + (ROW - BLOCK_H) / 2;
    }

    private void build(
            ToIntFunction<String> textWidth,
            List<String> labels,
            String noneLabel,
            boolean editing,
            int dropdownCount,
            int pendingDeleteIndex) {
        int x = PAD;
        int row = 0;
        int bottom = ROW;
        for (int i = 0; i < labels.size(); i++) {
            int textW = textWidth.applyAsInt(labels.get(i)) + 2 * BLOCK_PAD;
            int blockW = textW + (i == pendingDeleteIndex ? DELETE_W : 0);
            if (x > PAD && x + blockW > width - PAD) {
                row++;
                x = PAD;
            }
            tags.add(new Rect(x, rowTop(row), textW, BLOCK_H));
            if (i == pendingDeleteIndex) {
                delete = new Rect(x + textW, rowTop(row), DELETE_W, BLOCK_H);
            }
            x += blockW + GAP;
        }
        if (labels.isEmpty()) {
            int noneW = textWidth.applyAsInt(noneLabel) + 2 * BLOCK_PAD;
            none = new Rect(PAD, rowTop(0), noneW, BLOCK_H);
            x = PAD + noneW + GAP;
        }
        if (editing) {
            if (x > PAD) {
                row++;
            }
            textBox = new Rect(PAD, rowTop(row), width - 2 * PAD, BLOCK_H);
            int y = textBox.y() + BLOCK_H + 1;
            for (int i = 0; i < dropdownCount; i++) {
                dropdown.add(new Rect(PAD, y, width - 2 * PAD, BLOCK_H));
                y += BLOCK_H + 1;
            }
            bottom = Math.max(textBox.y() + BLOCK_H, y - 1);
        } else {
            if (x > PAD && x + PLUS_W > width - PAD) {
                row++;
                x = PAD;
            }
            plus = new Rect(x, rowTop(row), PLUS_W, BLOCK_H);
            bottom = plus.y() + BLOCK_H;
        }
        for (Rect tag : tags) {
            bottom = Math.max(bottom, tag.y() + BLOCK_H);
        }
        int contentBottom = bottom + PAD;
        maxScroll = Math.max(0, (contentBottom - height + ROW - 1) / ROW);
    }

    private void shift(int dy) {
        if (dy == 0) {
            return;
        }
        tags.replaceAll(r -> moved(r, dy));
        dropdown.replaceAll(r -> moved(r, dy));
        delete = moved(delete, dy);
        none = moved(none, dy);
        plus = moved(plus, dy);
        textBox = moved(textBox, dy);
    }

    private static Rect moved(Rect r, int dy) {
        return r == null ? null : new Rect(r.x(), r.y() + dy, r.w(), r.h());
    }

    public Rect statusRect() {
        return status;
    }

    public Rect tagRect(int index) {
        return tags.get(index);
    }

    /** The delete mark area of the tag showing one, or null. */
    public Rect deleteRect() {
        return delete;
    }

    /** The "none" label shown when there are no tags, or null. */
    public Rect noneRect() {
        return none;
    }

    /** The plus button, or null while editing. */
    public Rect plusRect() {
        return plus;
    }

    /** The text box, or null when not editing. */
    public Rect textBoxRect() {
        return textBox;
    }

    public int dropdownCount() {
        return dropdown.size();
    }

    public Rect dropdownRect(int index) {
        return dropdown.get(index);
    }

    public int maxScroll() {
        return maxScroll;
    }

    public int tagCount() {
        return tags.size();
    }

    /** What is at a point (panel-relative); anything scrolled up under the status row cannot be hit. */
    public Hit hit(double x, double y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return new Hit(Kind.NONE, 0);
        }
        if (y < ROW) {
            return new Hit(Kind.STATUS, 0);
        }
        for (int i = 0; i < dropdown.size(); i++) {
            if (dropdown.get(i).contains(x, y)) {
                return new Hit(Kind.DROPDOWN, i);
            }
        }
        if (textBox != null && textBox.contains(x, y)) {
            return new Hit(Kind.TEXTBOX, 0);
        }
        if (plus != null && plus.contains(x, y)) {
            return new Hit(Kind.PLUS, 0);
        }
        for (int i = 0; i < tags.size(); i++) {
            if (delete != null && delete.contains(x, y) && delete.x() == tags.get(i).x() + tags.get(i).w()
                    && delete.y() == tags.get(i).y()) {
                return new Hit(Kind.DELETE, i);
            }
            if (tags.get(i).contains(x, y)) {
                return new Hit(Kind.TAG, i);
            }
        }
        return new Hit(Kind.NONE, 0);
    }
}
