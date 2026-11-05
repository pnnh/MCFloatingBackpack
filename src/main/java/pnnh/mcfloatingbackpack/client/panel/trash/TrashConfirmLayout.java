package pnnh.mcfloatingbackpack.client.panel.trash;

import pnnh.mcfloatingbackpack.client.panel.PanelLayout.Rect;

/**
 * 022: where the confirmation dialog sits. A translucent mask covers the whole floating panel and the dialog stands in
 * its middle, always inside the panel's own rectangle, so a narrow window cannot cut it off. The caller says how many
 * lines the (wrapped) question takes, which is what makes the dialog fit even the smallest panel. Pure arithmetic with
 * no Minecraft types, so it can be unit-tested; the renderer and the input both read this one model.
 */
public final class TrashConfirmLayout {
    public static final int SIDE_MARGIN = 6;
    public static final int MAX_WIDTH = 120;
    public static final int PAD = 8;
    public static final int TEXT_GAP = 6;
    public static final int BUTTON_H = 14;
    private static final int BUTTON_SIDE = 5;
    private static final int BUTTON_GAP = 6;

    public enum Hit {
        CONFIRM,
        CANCEL,
        /** Elsewhere on the dialog. */
        DIALOG,
        /** On the mask, outside the dialog. */
        MASK,
        /** Outside the floating panel altogether. */
        OUTSIDE
    }

    private final Rect mask;
    private final Rect dialog;
    private final Rect confirm;
    private final Rect cancel;

    private TrashConfirmLayout(Rect mask, Rect dialog, Rect confirm, Rect cancel) {
        this.mask = mask;
        this.dialog = dialog;
        this.confirm = confirm;
        this.cancel = cancel;
    }

    /** @param textLines how many lines the question takes at the dialog's text width ({@link #textWidth}) */
    public static TrashConfirmLayout of(int panelX, int panelY, int panelW, int panelH, int textLines, int lineHeight) {
        Rect mask = new Rect(panelX, panelY, panelW, panelH);
        int w = dialogWidth(panelW);
        int h = PAD + Math.max(1, textLines) * lineHeight + TEXT_GAP + BUTTON_H + PAD;
        Rect dialog = new Rect(panelX + (panelW - w) / 2, panelY + Math.max(0, (panelH - h) / 2), w, h);
        int buttonW = (w - 2 * BUTTON_SIDE - BUTTON_GAP) / 2;
        int buttonY = dialog.y() + h - PAD - BUTTON_H;
        Rect confirm = new Rect(dialog.x() + BUTTON_SIDE, buttonY, buttonW, BUTTON_H);
        Rect cancel = new Rect(confirm.x() + buttonW + BUTTON_GAP, buttonY, buttonW, BUTTON_H);
        return new TrashConfirmLayout(mask, dialog, confirm, cancel);
    }

    private static int dialogWidth(int panelW) {
        return Math.min(panelW - 2 * SIDE_MARGIN, MAX_WIDTH);
    }

    /** The width the question may wrap at inside a dialog on a panel this wide. */
    public static int textWidth(int panelW) {
        return dialogWidth(panelW) - 2 * (PAD / 2 + 1);
    }

    public Rect mask() {
        return mask;
    }

    public Rect dialog() {
        return dialog;
    }

    public Rect confirmButton() {
        return confirm;
    }

    public Rect cancelButton() {
        return cancel;
    }

    public Hit hit(double x, double y) {
        if (confirm.contains(x, y)) {
            return Hit.CONFIRM;
        }
        if (cancel.contains(x, y)) {
            return Hit.CANCEL;
        }
        if (dialog.contains(x, y)) {
            return Hit.DIALOG;
        }
        return mask.contains(x, y) ? Hit.MASK : Hit.OUTSIDE;
    }
}
