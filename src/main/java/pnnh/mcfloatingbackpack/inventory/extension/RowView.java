package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.ArrayList;
import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * 035: what the client is told about the rows besides the cells of the visible ones (which come in the main area's list): the
 * tier capacity (where the rows start), the categories of the visible rows in order, and how many cells hold something in
 * the hidden rows, per category. The hidden items themselves are not part of it. 035 keeps it on the client; the panel's
 * layout and the hint about hidden items (036) read it.
 */
public record RowView(int baseCapacity, List<String> visibleCategories, List<RowSet.HiddenSummary> hidden) {
    public static final StreamCodec<ByteBuf, RowView> STREAM_CODEC = StreamCodec.of(RowView::write, RowView::read);

    public RowView {
        visibleCategories = List.copyOf(visibleCategories);
        hidden = List.copyOf(hidden);
    }

    public static RowView none(int baseCapacity) {
        return new RowView(baseCapacity, List.of(), List.of());
    }

    private static void write(ByteBuf buf, RowView view) {
        ByteBufCodecs.VAR_INT.encode(buf, view.baseCapacity());
        ByteBufCodecs.VAR_INT.encode(buf, view.visibleCategories().size());
        for (String category : view.visibleCategories()) {
            ByteBufCodecs.stringUtf8(ExtensionCategories.MAX_CATEGORY_LENGTH).encode(buf, category);
        }
        ByteBufCodecs.VAR_INT.encode(buf, view.hidden().size());
        for (RowSet.HiddenSummary summary : view.hidden()) {
            ByteBufCodecs.stringUtf8(ExtensionCategories.MAX_CATEGORY_LENGTH).encode(buf, summary.category());
            ByteBufCodecs.VAR_INT.encode(buf, summary.nonEmptyCells());
        }
    }

    private static RowView read(ByteBuf buf) {
        int base = ByteBufCodecs.VAR_INT.decode(buf);
        int visibleCount = ByteBufCodecs.VAR_INT.decode(buf);
        if (visibleCount < 0 || visibleCount > ExtensionCategories.MAX_ROWS) {
            throw new DecoderException("Visible row count " + visibleCount + " is out of range");
        }
        List<String> visible = new ArrayList<>(visibleCount);
        for (int i = 0; i < visibleCount; i++) {
            visible.add(ByteBufCodecs.stringUtf8(ExtensionCategories.MAX_CATEGORY_LENGTH).decode(buf));
        }
        int hiddenCount = ByteBufCodecs.VAR_INT.decode(buf);
        if (hiddenCount < 0 || hiddenCount > ExtensionCategories.MAX_ROWS) {
            throw new DecoderException("Hidden summary count " + hiddenCount + " is out of range");
        }
        List<RowSet.HiddenSummary> hidden = new ArrayList<>(hiddenCount);
        for (int i = 0; i < hiddenCount; i++) {
            String category = ByteBufCodecs.stringUtf8(ExtensionCategories.MAX_CATEGORY_LENGTH).decode(buf);
            hidden.add(new RowSet.HiddenSummary(category, ByteBufCodecs.VAR_INT.decode(buf)));
        }
        return new RowView(base, visible, hidden);
    }
}
