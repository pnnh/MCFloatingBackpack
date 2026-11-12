package pnnh.mcfloatingbackpack.client.panel.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery.Alternative;
import pnnh.mcfloatingbackpack.client.panel.search.SearchQuery.Command;

/** 044: the prefix rows of the grammar table in contracts/query-syntax.md, line by line. */
class SearchQueryPrefixTest {
    /** Compact form: terms joined by ',', alternatives by '/'; an alternative is KIND:text with a leading '!' for an exclusion (W, M, T). */
    private static String show(String input) {
        List<String> terms = new ArrayList<>();
        for (SearchQuery.Term term : SearchQuery.parse(input).terms()) {
            List<String> alternatives = new ArrayList<>();
            for (Alternative a : term.alternatives()) {
                String kind = switch (a.kind()) {
                    case WORD -> "W";
                    case MOD -> "M";
                    case TAG -> "T";
                };
                alternatives.add((a.negated() ? "!" : "") + kind + ":" + a.text());
            }
            terms.add(String.join("/", alternatives));
        }
        return String.join(",", terms);
    }

    private static Command command(String input) {
        return SearchQuery.parse(input).command();
    }

    // --- @ and # ---------------------------------------------------------------------------------------------------

    @Test
    void aPrefixMakesAModOrTagAlternative() {
        assertEquals("M:minecraft", show("@minecraft"));
        assertEquals("T:logs", show("#logs"));
    }

    @Test
    void anExclusionCanCarryAPrefix() {
        assertEquals("!M:mod", show("-@mod"));
        assertEquals("!T:tag", show("-#tag"));
    }

    @Test
    void aPrefixWithNothingAfterItIsAnEmptyAlternativeAndDropped() {
        for (String input : new String[] {"@", "#", "-@", "-#", "@ ", "# ", "@|#", "@\"\""}) {
            assertTrue(SearchQuery.parse(input).isEmpty(), input);
        }
    }

    @Test
    void aBlankAfterThePrefixEndsItSoTheNextWordIsAnOrdinaryWord() {
        assertEquals("W:mekanism", show("@ mekanism"));
    }

    @Test
    void aQuoteRightAfterThePrefixIsAWholePhrase() {
        assertEquals("M:a b", show("@\"a b\""));
        assertEquals("T:my tools", show("#\"my tools\""));
        assertEquals("!M:a b", show("-@\"a b\""));
    }

    @Test
    void aPrefixInsideQuotesOrInTheMiddleOfAWordIsAnOrdinaryCharacter() {
        assertEquals("W:@x", show("\"@x\""));
        assertEquals("W:#x", show("\"#x\""));
        assertEquals("W:a@b", show("a@b"));
        assertEquals("W:a#b", show("a#b"));
        assertEquals("W:ab@", show("ab@"));
    }

    @Test
    void aQuoteBeforeThePrefixTakesItsRightToBeOne() {
        // "a"@b: the quote came first, so the @ is not at the start of the alternative any more
        assertEquals("W:a@b", show("\"a\"@b"));
    }

    @Test
    void aSecondPrefixOrADashAfterThePrefixIsOrdinary() {
        assertEquals("M:@x", show("@@x"));
        assertEquals("M:-x", show("@-x"));
        assertEquals("T:@x", show("#@x"));
    }

    @Test
    void prefixesWorkInEveryAlternativeOfATerm() {
        assertEquals("M:a/T:b", show("@a|#b"));
        assertEquals("M:a/!T:b", show("@a|-#b"));
        assertEquals("W:x/M:y", show("x|@y"));
    }

    @Test
    void prefixTextsAreLowerCased() {
        assertEquals("M:minecraft,T:logs", show("@Minecraft #LOGS"));
    }

    @Test
    void prefixesMixWithWordsInTerms() {
        assertEquals("M:minecraft,T:logs,W:oak", show("@minecraft #logs oak"));
        assertEquals("M:mekanism,!W:ingot", show("@mekanism -ingot"));
    }

    @Test
    void anEmptyPrefixAlternativeDoesNotSpoilItsTerm() {
        assertEquals("M:a", show("@a|@"));
        assertEquals("", show("@|#"));
    }

    // --- the command ----------------------------------------------------------------------------------------------------

    @Test
    void hiddenIsACommandWhateverTheCaseAndTheLeadingBlanks() {
        for (String input : new String[] {"/hidden", "  /hidden", "/HIDDEN", "/Hidden", "/hidden ", "\t/hidden", " /hidden   "}) {
            SearchQuery query = SearchQuery.parse(input);
            assertEquals(Command.Type.HIDDEN, query.command().type(), input);
            assertEquals(List.of(), query.terms(), input);
            assertFalse(query.isEmpty(), input);
        }
    }

    @Test
    void whatFollowsHiddenIsParsedAsTerms() {
        SearchQuery query = SearchQuery.parse("/hidden fish");
        assertEquals(Command.Type.HIDDEN, query.command().type());
        assertEquals("W:fish", show("/hidden fish"));
        assertEquals("M:x", show("/hidden @x"));
        assertEquals("!W:fish", show("/hidden -fish"));
        assertEquals("W:a b", show("/hidden \"a b\""));
    }

    @Test
    void anythingElseStartingWithASlashIsAnUnknownCommandThatKeepsTheTypedWord() {
        for (String input : new String[] {"/", "/h", "/foo", "/hiddenx", "/hidden2", "/Foo bar"}) {
            Command command = command(input);
            assertEquals(Command.Type.UNKNOWN, command.type(), input);
            assertFalse(SearchQuery.parse(input).isEmpty(), input);
        }
        assertEquals("/", command("/").raw());
        assertEquals("/Foo", command("/Foo bar").raw());
        assertEquals("W:bar", show("/Foo bar"));
        assertEquals("/hiddenx", command("  /hiddenx  y").raw());
    }

    @Test
    void aSlashAnywhereButTheStartIsAnOrdinaryCharacter() {
        for (String input : new String[] {"a /hidden", "\"/hidden\"", "a/b", "-/hidden", "|/hidden", "@/hidden", "x|/hidden"}) {
            assertEquals(Command.NONE, command(input), input);
        }
        assertEquals("W:a,W:/hidden", show("a /hidden"));
        assertEquals("W:/hidden", show("\"/hidden\""));
        assertEquals("W:a/b", show("a/b"));
        assertEquals("!W:/hidden", show("-/hidden"));
    }

    @Test
    void aCommandsRestIsNotACommandAgain() {
        assertEquals("W:/foo", show("/hidden /foo"));
        assertEquals(Command.Type.HIDDEN, command("/hidden /foo").type());
    }

    @Test
    void queriesWithoutACommandHaveNone() {
        assertEquals(Command.NONE, command(""));
        assertEquals(Command.NONE, command("oak log"));
        assertEquals(Command.NONE, command(null));
        assertEquals(Command.NONE, SearchQuery.empty().command());
    }

    @Test
    void theLimitOf100CodePointsStillBelongsToTheBox() {
        assertEquals(100, SearchQuery.MAX_CODEPOINTS);
    }

    // --- 042 is untouched ---------------------------------------------------------------------------------------------

    @Test
    void the042FormOfAnAlternativeIsAWord() {
        assertEquals(new Alternative(SearchQuery.Kind.WORD, true, "x"), new Alternative(true, "x"));
    }

    @Test
    void theOrdinary042QueriesParseAsBefore() {
        assertEquals("W:pick", show("pick"));
        assertEquals("W:iron,W:pick", show("Iron  PICK"));
        assertEquals("W:iron/W:gold", show("iron|gold"));
        assertEquals("W:iron", show("iron|"));
        assertEquals("!W:planks", show("-planks"));
        assertEquals("W:oak log", show("\"oak log\""));
        assertEquals("!W:oak log", show("-\"oak log\""));
        assertEquals("W:oak lo", show("\"oak lo"));
        assertEquals("W:a | b,W:c", show("\"a | b\" c"));
        assertEquals("W:iron/!W:gold", show("iron|-gold"));
    }
}
