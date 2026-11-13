package pnnh.mcfloatingbackpack.client.panel.jukebox;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;

/**
 * 053: geometry of the player page of the mobile jukebox: it hangs off the floating panel's right edge at the same place as the trash can
 * page, top aligned with the panel, and is a title bar over a line with the title of the track, a line with its number, a row of four
 * buttons (previous, play or stop, rewind, next) and a row of two switches (repeat one, shuffle). Pure arithmetic with no Minecraft
 * types, so it can be unit-tested; the renderer and the input both read this one model. The frame's left outline pixel is the floating
 * panel's own right outline pixel, so the two look joined; the page lies entirely outside the floating panel's rectangle.
 */
public final class JukeboxPanelLayout {
    public static final int WIDTH = 112;
    public static final int TITLE_H = 14;
    public static final int PAD = 4;
    public static final int NAME_Y = TITLE_H + 3;
    public static final int LINE_H = 10;
    public static final int COUNT_Y = NAME_Y + LINE_H + 1;
    public static final int BUTTON_Y = COUNT_Y + LINE_H + 3;
    public static final int BUTTON_W = 22;
    public static final int BUTTON_H = 16;
    public static final int BUTTON_GAP = 4;
    public static final int TOGGLE_Y = BUTTON_Y + BUTTON_H + 4;
    public static final int TOGGLE_H = 14;
    public static final int TOGGLE_GAP = 4;
    public static final int HEIGHT = TOGGLE_Y + TOGGLE_H + PAD;

    public enum Button { PREVIOUS, PLAY_STOP, REWIND, NEXT }

    public enum Toggle { LOOP_ONE, SHUFFLE }

    private final int x;
    private final int y;

    private JukeboxPanelLayout(int x, int y) {
        this.x = x;
        this.y = y;
    }

    /** @param panelWidth the floating panel's whole width in GUI pixels */
    public static JukeboxPanelLayout of(int panelX, int panelY, int panelWidth) {
        return new JukeboxPanelLayout(panelX + panelWidth + 1, panelY);
    }

    /** The whole frame. */
    public Rect rect() {
        return new Rect(x, y, WIDTH, HEIGHT);
    }

    public Rect titleRect() {
        return new Rect(x, y, WIDTH, TITLE_H);
    }

    /** The line with the title of the track (or the hint that there is no disc). */
    public Rect nameRect() {
        return new Rect(x + PAD, y + NAME_Y, WIDTH - 2 * PAD, LINE_H);
    }

    /** The line with "track n of m". */
    public Rect countRect() {
        return new Rect(x + PAD, y + COUNT_Y, WIDTH - 2 * PAD, LINE_H);
    }

    /** The four buttons are spread over the width with the same gap and the whole row is centred. */
    public Rect buttonRect(Button button) {
        int total = Button.values().length * BUTTON_W + (Button.values().length - 1) * BUTTON_GAP;
        int left = x + (WIDTH - total) / 2;
        return new Rect(left + button.ordinal() * (BUTTON_W + BUTTON_GAP), y + BUTTON_Y, BUTTON_W, BUTTON_H);
    }

    /** The two switches share the width between the paddings. */
    public Rect toggleRect(Toggle toggle) {
        int w = (WIDTH - 2 * PAD - TOGGLE_GAP) / 2;
        return new Rect(x + PAD + toggle.ordinal() * (w + TOGGLE_GAP), y + TOGGLE_Y, w, TOGGLE_H);
    }

    /** The button under the point, or null. */
    public Button buttonAt(double px, double py) {
        for (Button button : Button.values()) {
            if (buttonRect(button).contains(px, py)) {
                return button;
            }
        }
        return null;
    }

    /** The switch under the point, or null. */
    public Toggle toggleAt(double px, double py) {
        for (Toggle toggle : Toggle.values()) {
            if (toggleRect(toggle).contains(px, py)) {
                return toggle;
            }
        }
        return null;
    }

    public boolean contains(double px, double py) {
        return rect().contains(px, py);
    }
}
