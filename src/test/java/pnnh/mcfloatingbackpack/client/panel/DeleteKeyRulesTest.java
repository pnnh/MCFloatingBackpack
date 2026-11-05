package pnnh.mcfloatingbackpack.client.panel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.DeleteKeyRules.Inputs;
import pnnh.mcfloatingbackpack.client.panel.DeleteKeyRules.ScreenKind;
import pnnh.mcfloatingbackpack.client.panel.DeleteKeyRules.Target;

/** 022: what the delete key does, and above all when it does nothing and leaves the key alone. */
class DeleteKeyRulesTest {
    /** Everything in order; each test switches off or on what it is about. */
    private static Inputs ok(ScreenKind kind, boolean selection, boolean hoverMain, boolean hoverMenu) {
        return new Inputs(true, true, false, false, false, kind, selection, hoverMain, hoverMenu);
    }

    @Test
    void aSelectionIsDeletedBeforeAnythingUnderTheMouse() {
        assertEquals(Target.MAIN_SELECTION, DeleteKeyRules.decide(ok(ScreenKind.HOST, true, true, false)));
        assertEquals(Target.MAIN_SELECTION, DeleteKeyRules.decide(ok(ScreenKind.CHEST, true, false, true)));
    }

    @Test
    void withoutASelectionTheMainSlotUnderTheMouseIsDeleted() {
        assertEquals(Target.MAIN_HOVER, DeleteKeyRules.decide(ok(ScreenKind.HOST, false, true, false)));
        assertEquals(Target.MAIN_HOVER, DeleteKeyRules.decide(ok(ScreenKind.INVENTORY, false, true, true)));
    }

    @Test
    void theMenuSlotCountsOnlyOnTheInventoryAndChestScreens() {
        assertEquals(Target.MENU_SLOT, DeleteKeyRules.decide(ok(ScreenKind.INVENTORY, false, false, true)));
        assertEquals(Target.MENU_SLOT, DeleteKeyRules.decide(ok(ScreenKind.CHEST, false, false, true)));
        assertEquals(Target.NONE, DeleteKeyRules.decide(ok(ScreenKind.HOST, false, false, true)));
        assertEquals(Target.NONE, DeleteKeyRules.decide(ok(ScreenKind.OTHER, false, false, true)));
    }

    @Test
    void otherScreensStillOfferTheMainArea() {
        assertEquals(Target.MAIN_SELECTION, DeleteKeyRules.decide(ok(ScreenKind.OTHER, true, false, false)));
        assertEquals(Target.MAIN_HOVER, DeleteKeyRules.decide(ok(ScreenKind.OTHER, false, true, false)));
    }

    @Test
    void nothingToDeleteMeansNoTargetSoTheKeyIsLeftAlone() {
        for (ScreenKind kind : ScreenKind.values()) {
            assertEquals(Target.NONE, DeleteKeyRules.decide(ok(kind, false, false, false)), kind.name());
        }
    }

    @Test
    void thePanelHiddenOrNoTrashCanOrAPendingConfirmationOrTheTagPanelDisablesIt() {
        assertEquals(Target.NONE, DeleteKeyRules.decide(new Inputs(false, true, false, false, false, ScreenKind.HOST, true, true, true)));
        assertEquals(Target.NONE, DeleteKeyRules.decide(new Inputs(true, false, false, false, false, ScreenKind.HOST, true, true, true)));
        assertEquals(Target.NONE, DeleteKeyRules.decide(new Inputs(true, true, true, false, false, ScreenKind.HOST, true, true, true)));
        assertEquals(Target.NONE, DeleteKeyRules.decide(new Inputs(true, true, false, true, false, ScreenKind.HOST, true, true, true)));
        assertEquals(Target.NONE, DeleteKeyRules.decide(new Inputs(true, true, false, false, true, ScreenKind.HOST, true, true, true)));
    }
}
