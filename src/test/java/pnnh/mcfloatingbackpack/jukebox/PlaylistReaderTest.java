package pnnh.mcfloatingbackpack.jukebox;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.extension.Row;
import pnnh.mcfloatingbackpack.jukebox.PlaylistReader.Track;

class PlaylistReaderTest {
    /** A cell is a string: "" empty, "disc:x" a disc of song x, anything else is some other item. */
    private static final Function<String, String> KEY = s -> s.startsWith("disc:") ? s.substring(5) : null;

    private static Row<String> row(String category, String... cells) {
        List<String> all = new ArrayList<>(List.of(cells));
        while (all.size() < 9) {
            all.add("");
        }
        return new Row<>(category, all);
    }

    private static List<String> keys(List<Row<String>> rows) {
        return PlaylistReader.keys(PlaylistReader.read(rows, "disc", KEY));
    }

    @Test
    void oneRowIsReadLeftToRightAndEmptyCellsAreSkipped() {
        assertEquals(List.of("a", "b", "c"), keys(List.of(row("disc", "disc:a", "", "disc:b", "disc:c"))));
    }

    @Test
    void discRowsWithAnotherKindOfRowBetweenThemAreReadInFull() {
        assertEquals(List.of("a", "b", "c", "d"), keys(List.of(
                row("disc", "disc:a", "disc:b"),
                row("food", "disc:never", "bread"),
                row("disc", "disc:c"),
                row("tool"),
                row("disc", "", "disc:d"))));
    }

    @Test
    void theOrderIsTheRowsThenTheCells() {
        List<Track<String>> tracks = PlaylistReader.read(List.of(row("disc", "disc:a"), row("disc", "disc:b", "disc:c")), "disc", KEY);
        assertEquals(List.of(0, 1, 1), tracks.stream().map(Track::row).toList());
        assertEquals(List.of(0, 0, 1), tracks.stream().map(Track::cell).toList());
    }

    @Test
    void anItemThatIsNotADiscInADiscRowIsSkipped() {
        assertEquals(List.of("a"), keys(List.of(row("disc", "stick", "disc:a", "bread"))));
    }

    @Test
    void thereIsNothingWithoutDiscRows() {
        assertTrue(keys(List.of(row("food", "disc:a"), row("general", "disc:b"))).isEmpty());
        assertTrue(keys(List.of()).isEmpty());
    }

    @Test
    void twoDiscsOfTheSameSongAreTwoTracks() {
        assertEquals(List.of("a", "a", "b"), keys(List.of(row("disc", "disc:a", "disc:a", "disc:b"))));
    }
}
