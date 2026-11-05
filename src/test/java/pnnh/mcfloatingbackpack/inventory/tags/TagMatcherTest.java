package pnnh.mcfloatingbackpack.inventory.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.tags.TagMatcher.Entry;

class TagMatcherTest {
    private static List<Entry> entries(boolean chinese) {
        List<Entry> list = new ArrayList<>();
        for (String id : PresetTags.ids()) {
            list.add(new Entry(id, chinese ? "名" + id : Character.toUpperCase(id.charAt(0)) + id.substring(1)));
        }
        return list;
    }

    @Test
    void emptyQueryReturnsEverythingInPresetOrder() {
        assertEquals(PresetTags.ids(), TagMatcher.match("", entries(false)));
        assertEquals(PresetTags.ids(), TagMatcher.match(null, entries(false)));
    }

    @Test
    void prefixBeatsSubsequence() {
        List<String> result = TagMatcher.match("wo", entries(false));
        assertEquals("wood", result.get(0));
    }

    @Test
    void prefixThenSubstringThenSubsequence() {
        List<Entry> list = List.of(
                new Entry("redstone", "Redstone"), new Entry("stone", "Stone"), new Entry("sto_x", "Sto"));
        // "sto": prefix of stone and sto_x (in input order), substring of redstone
        assertEquals(List.of("stone", "sto_x", "redstone"), TagMatcher.match("sto", list));
    }

    @Test
    void subsequenceMatches() {
        assertEquals(List.of("redstone"), TagMatcher.match("rds", entries(false)));
    }

    @Test
    void caseInsensitive() {
        assertEquals(TagMatcher.match("wood", entries(false)), TagMatcher.match("WOOD", entries(false)));
    }

    @Test
    void matchesTheDisplayNameToo() {
        List<Entry> list = List.of(new Entry("wood", "木材"), new Entry("stone", "石头"));
        assertEquals(List.of("wood"), TagMatcher.match("木", list));
    }

    @Test
    void noMatchIsEmpty() {
        assertEquals(List.of(), TagMatcher.match("zzz", entries(false)));
    }
}
