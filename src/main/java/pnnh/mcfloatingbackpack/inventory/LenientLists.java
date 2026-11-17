package pnnh.mcfloatingbackpack.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;

/**
 * 056: a list codec that keeps every element in its place. The ordinary list codec drops an element it cannot read (for example a
 * stack of an item this version no longer has) and hands back a shortened list, so everything after it moves up one place: in a
 * backpack that changes where the player's things are, silently. This one reads each element on its own and puts the {@code empty}
 * value where one cannot be read, so the places stay and only that one stack is lost. An element that decodes only partly (a stack
 * that lost a component) is kept as far as it was read. Writing is the ordinary list codec's. Pure logic apart from the codecs given.
 */
public final class LenientLists {
    private LenientLists() {
    }

    /**
     * @param element the codec of one element
     * @param empty   what stands in for an element that cannot be read
     * @param onLost  told how many elements of a list were replaced (only when there were some), so the caller can log it
     */
    public static <E> Codec<List<E>> of(Codec<E> element, E empty, IntConsumer onLost) {
        Codec<List<E>> strict = element.listOf();
        return new Codec<>() {
            @Override
            public <T> DataResult<T> encode(List<E> input, DynamicOps<T> ops, T prefix) {
                return strict.encode(input, ops, prefix);
            }

            @Override
            public <T> DataResult<Pair<List<E>, T>> decode(DynamicOps<T> ops, T input) {
                return ops.getStream(input).map(stream -> {
                    List<E> out = new ArrayList<>();
                    int[] lost = {0};
                    Stream<T> elements = stream;
                    elements.forEach(one -> {
                        DataResult<E> read = element.parse(ops, one);
                        E value = read.resultOrPartial(error -> { }).orElse(null);
                        if (value == null) {
                            lost[0]++;
                            value = empty;
                        } else if (read.error().isPresent()) {
                            lost[0]++;
                        }
                        out.add(value);
                    });
                    if (lost[0] > 0) {
                        onLost.accept(lost[0]);
                    }
                    return Pair.of(out, input);
                });
            }
        };
    }
}
