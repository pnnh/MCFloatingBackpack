package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.search.HiddenSummaryFit.Plan;

/** 044: what of the {@code /hidden} summary fits in the lines the main area has. */
class HiddenSummaryFitTest {
    @Test
    void everythingFitsWhenThereIsRoom() {
        assertEquals(new Plan(3, true, true, false), HiddenSummaryFit.fit(3, 1, 2, 10));
        assertEquals(new Plan(3, true, true, false), HiddenSummaryFit.fit(3, 1, 2, 6), "exactly enough");
    }

    @Test
    void theHintGoesFirstThenTheTitle() {
        assertEquals(new Plan(3, true, false, false), HiddenSummaryFit.fit(3, 1, 2, 5), "the hint needs 2 lines, only 1 is left");
        assertEquals(new Plan(3, false, false, false), HiddenSummaryFit.fit(3, 2, 2, 4), "the title needs 2 lines, only 1 is left");
    }

    @Test
    void aHintDoesNotHangInTheAirWithoutItsTitle() {
        // the title needs 3 lines (does not fit), the hint needs 1 (would fit): neither is drawn
        assertEquals(new Plan(2, false, false, false), HiddenSummaryFit.fit(2, 3, 1, 4));
    }

    @Test
    void theCategoryLinesComeFirstAndAnEllipsisEndsAListThatIsCut() {
        assertEquals(new Plan(2, false, false, true), HiddenSummaryFit.fit(5, 1, 2, 3));
        assertEquals(new Plan(5, false, false, false), HiddenSummaryFit.fit(5, 1, 2, 5), "exactly the category lines: they fit, nothing is left");
    }

    @Test
    void withASingleLineOnlyTheEllipsisIsLeftWhenThereAreSeveral() {
        assertEquals(new Plan(0, false, false, true), HiddenSummaryFit.fit(2, 1, 1, 1));
    }

    @Test
    void oneCategoryLineThatFitsExactlyLeavesNoTitle() {
        assertEquals(new Plan(1, false, false, false), HiddenSummaryFit.fit(1, 1, 1, 1));
    }

    @Test
    void noRoomMeansNothing() {
        assertEquals(new Plan(0, false, false, false), HiddenSummaryFit.fit(3, 1, 1, 0));
        assertEquals(new Plan(0, false, false, false), HiddenSummaryFit.fit(3, 1, 1, -2));
    }

    @Test
    void theMessageForNothingHiddenIsJustCategoryLinesWithNoTitleOrHint() {
        assertEquals(new Plan(2, true, true, false), HiddenSummaryFit.fit(2, 0, 0, 5));
        assertEquals(new Plan(1, false, false, true), HiddenSummaryFit.fit(3, 0, 0, 2));
    }
}
