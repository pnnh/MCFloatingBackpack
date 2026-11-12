package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 048: which words are used for hidden items, hidden liquids, or both. */
class HiddenTextKeysTest {
    @Test
    void itemsOnlyKeepsTheWordsOf037() {
        assertEquals(".hidden.title", HiddenTextKeys.title(true, false));
        assertEquals(List.of(".hidden.hint"), HiddenTextKeys.hints(true, false));
    }

    @Test
    void liquidsOnlyHasItsOwnWords() {
        assertEquals(".hidden.liquid.title", HiddenTextKeys.title(false, true));
        assertEquals(List.of(".hidden.liquid.hint"), HiddenTextKeys.hints(false, true));
    }

    @Test
    void bothHaveAJointTitleAndBothHints() {
        assertEquals(".hidden.mixed.title", HiddenTextKeys.title(true, true));
        assertEquals(List.of(".hidden.hint", ".hidden.liquid.hint"), HiddenTextKeys.hints(true, true));
    }
}
