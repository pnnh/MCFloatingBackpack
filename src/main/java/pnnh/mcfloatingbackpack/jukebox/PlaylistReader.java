package pnnh.mcfloatingbackpack.jukebox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import pnnh.mcfloatingbackpack.inventory.extension.Row;

/**
 * 053: the playlist of the mobile jukebox: the discs in the visible rows of one category (the disc extension rows), in the order of the rows
 * and, within a row, of the cells. Rows of any other category are skipped wherever they lie between the disc rows, so disc rows that are not
 * next to each other are read in full; an empty cell, and one that holds something that is not a disc, is skipped too. Hidden rows are not
 * given to it at all. Pure logic over an abstract cell type.
 */
public final class PlaylistReader {
    /** One track: the key that tells what song it is (two discs of the same song have the same key), what the cell held, and where it was. */
    public record Track<T>(String key, T item, int row, int cell) {
    }

    private PlaylistReader() {
    }

    /**
     * @param visibleRows the visible rows, in order
     * @param category    the category whose rows make the playlist
     * @param keyOf       the key of a cell's content, or {@code null} when it is empty or not a disc
     */
    public static <T> List<Track<T>> read(List<Row<T>> visibleRows, String category, Function<T, String> keyOf) {
        List<Track<T>> out = new ArrayList<>();
        for (int r = 0; r < visibleRows.size(); r++) {
            Row<T> row = visibleRows.get(r);
            if (!row.category().equals(category)) {
                continue;
            }
            for (int c = 0; c < row.cells().size(); c++) {
                T cell = row.cells().get(c);
                String key = cell == null ? null : keyOf.apply(cell);
                if (key != null) {
                    out.add(new Track<>(key, cell, r, c));
                }
            }
        }
        return out;
    }

    /** The keys of a playlist, in order (what {@link JukeboxPlayer} works on). */
    public static <T> List<String> keys(List<Track<T>> tracks) {
        List<String> keys = new ArrayList<>(tracks.size());
        for (Track<T> track : tracks) {
            keys.add(track.key());
        }
        return keys;
    }
}
