package pnnh.mcfloatingbackpack.client.panel.combined;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import pnnh.mcfloatingbackpack.client.panel.FloatingPanelState;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;

/** 065: what the combined bars' drawing and input share: the layout for the panel's place now, and what a tab says. */
public final class CombinedBars {
    /** Room a tab takes besides its text: swatch, icon, gaps. */
    private static final int TAB_EXTRA = 23;
    private static final int MAX_TEXT = 56;

    private CombinedBars() {
    }

    /** The tab's text: the name given in an anvil, else the backpack's number. */
    public static String label(ClientCombinedState.MemberView member) {
        if (!member.name().isEmpty()) {
            return member.name();
        }
        return member.identity() == null ? "#----" : "#" + new BackpackIdentity(member.identity()).id();
    }

    /** The label cut to what a tab shows. */
    public static String shown(Font font, ClientCombinedState.MemberView member) {
        return font.plainSubstrByWidth(label(member), MAX_TEXT);
    }

    /** The geometry for the panel where it is now and the tabs the server sent. */
    public static CombinedBarLayout layout() {
        Font font = Minecraft.getInstance().font;
        ClientCombinedState state = ClientCombinedState.get();
        int[] widths = new int[state.members().size()];
        for (int i = 0; i < widths.length; i++) {
            widths[i] = font.width(shown(font, state.members().get(i))) + TAB_EXTRA;
        }
        FloatingPanelState panel = FloatingPanelState.get();
        return CombinedBarLayout.of(panel.getPosX(), panel.getPosY(), panel.getWidth(), widths);
    }

    /** Whether the bars are there to draw and to press: the panel is up and the combined panel is on. */
    public static boolean shown() {
        return FloatingPanelState.get().isVisible() && ClientCombinedState.get().isActive();
    }
}
