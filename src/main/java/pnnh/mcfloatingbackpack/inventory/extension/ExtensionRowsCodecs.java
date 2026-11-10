package pnnh.mcfloatingbackpack.inventory.extension;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 035: how the rows of a backpack are written, to disk (an optional field of the backpack's contents, so a backpack saved
 * before the rows reads as having none) and to the network. Whatever is read is made safe by {@link RowSet#sanitize}: nine
 * cells to a row, at most {@link ExtensionCategories#MAX_ROWS} rows, no empty hidden row; ids this version does not know are kept.
 */
public final class ExtensionRowsCodecs {
    private static final Codec<Row<ItemStack>> ROW_CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.STRING.fieldOf("category").forGetter(Row::category),
                    ItemStack.OPTIONAL_CODEC.listOf().fieldOf("cells").forGetter(Row::cells))
            .apply(instance, Row::new));

    public static final Codec<RowSet<ItemStack>> CODEC = RecordCodecBuilder.create(instance -> instance
            .group(
                    ROW_CODEC.listOf().optionalFieldOf("visible", List.of()).forGetter(RowSet::visible),
                    ROW_CODEC.listOf().optionalFieldOf("hidden", List.of()).forGetter(RowSet::hidden))
            .apply(instance, ExtensionRowsCodecs::sanitize));

    public static final StreamCodec<RegistryFriendlyByteBuf, RowSet<ItemStack>> STREAM_CODEC = StreamCodec.of(
            ExtensionRowsCodecs::write, ExtensionRowsCodecs::read);

    private ExtensionRowsCodecs() {
    }

    /** The rows made safe, with a log line when something had to be cut. */
    public static RowSet<ItemStack> sanitize(List<Row<ItemStack>> visible, List<Row<ItemStack>> hidden) {
        RowSet<ItemStack> set = RowSet.sanitize(visible, hidden, ItemStack::isEmpty, () -> ItemStack.EMPTY);
        int before = (visible == null ? 0 : visible.size()) + (hidden == null ? 0 : hidden.size());
        if (set.total() < before) {
            MCFloatingBackpack.LOGGER.warn("A backpack held {} extension rows; {} were dropped (the limit is {}, or they were empty hidden rows)",
                    before, before - set.total(), ExtensionCategories.MAX_ROWS);
        }
        return set;
    }

    private static void write(RegistryFriendlyByteBuf buf, RowSet<ItemStack> rows) {
        writeRows(buf, rows.visible());
        writeRows(buf, rows.hidden());
    }

    private static void writeRows(RegistryFriendlyByteBuf buf, List<Row<ItemStack>> rows) {
        buf.writeVarInt(rows.size());
        for (Row<ItemStack> row : rows) {
            ByteBufCodecs.stringUtf8(ExtensionCategories.MAX_CATEGORY_LENGTH).encode(buf, row.category());
            for (int i = 0; i < ExtensionCategories.ROW_CELLS; i++) {
                ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, i < row.cells().size() ? row.cells().get(i) : ItemStack.EMPTY);
            }
        }
    }

    private static RowSet<ItemStack> read(RegistryFriendlyByteBuf buf) {
        List<Row<ItemStack>> visible = readRows(buf);
        List<Row<ItemStack>> hidden = readRows(buf);
        return sanitize(visible, hidden);
    }

    private static List<Row<ItemStack>> readRows(RegistryFriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > ExtensionCategories.MAX_ROWS) {
            throw new DecoderException("Extension row count " + count + " is not in 0.." + ExtensionCategories.MAX_ROWS);
        }
        List<Row<ItemStack>> rows = new ArrayList<>(count);
        for (int r = 0; r < count; r++) {
            String category = ByteBufCodecs.stringUtf8(ExtensionCategories.MAX_CATEGORY_LENGTH).decode(buf);
            List<ItemStack> cells = new ArrayList<>(ExtensionCategories.ROW_CELLS);
            for (int i = 0; i < ExtensionCategories.ROW_CELLS; i++) {
                cells.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
            }
            rows.add(new Row<>(category, cells));
        }
        return rows;
    }
}
