package pnnh.mcfloatingbackpack.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** 056: an element that cannot be read leaves an empty place and the others stay where they are. */
class LenientListsTest {
    /** A stand-in for a stack: an id that must be one of the known ones, and a count. */
    private record Stack(String id, int count) {
    }

    private static final Stack EMPTY = new Stack("", 0);
    private static final Set<String> KNOWN = Set.of("a", "b", "c");

    private static final Codec<Stack> STACK = RecordCodecBuilder.create(instance -> instance
            .group(
                    Codec.STRING
                            .validate(id -> KNOWN.contains(id) ? DataResult.success(id) : DataResult.<String>error(() -> "Unknown registry key " + id))
                            .fieldOf("id")
                            .forGetter(Stack::id),
                    Codec.INT.fieldOf("count").forGetter(Stack::count))
            .apply(instance, Stack::new));

    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    @Test
    void theOrdinaryListCodecShiftsTheOthersUpWhichIsWhatThisOneAvoids() {
        JsonElement input = json("[{\"id\":\"a\",\"count\":1},{\"id\":\"gone\",\"count\":2},{\"id\":\"c\",\"count\":3}]");
        List<Stack> strict = STACK.listOf().parse(JsonOps.INSTANCE, input).resultOrPartial(e -> { }).orElseThrow();
        assertEquals(List.of(new Stack("a", 1), new Stack("c", 3)), strict, "the premise: the ordinary codec shortens the list");
        List<Stack> lenient = LenientLists.of(STACK, EMPTY, n -> { }).parse(JsonOps.INSTANCE, input).getOrThrow();
        assertEquals(List.of(new Stack("a", 1), EMPTY, new Stack("c", 3)), lenient);
    }

    @Test
    void everyPlaceOfAListThatCannotBeReadIsKeptAndCounted() {
        AtomicInteger lost = new AtomicInteger();
        Codec<List<Stack>> codec = LenientLists.of(STACK, EMPTY, lost::addAndGet);
        JsonElement input = json("[{\"id\":\"x\",\"count\":1},{\"id\":\"b\",\"count\":2},{\"id\":\"y\",\"count\":3},{\"id\":\"z\",\"count\":4}]");
        List<Stack> read = codec.parse(JsonOps.INSTANCE, input).getOrThrow();
        assertEquals(List.of(EMPTY, new Stack("b", 2), EMPTY, EMPTY), read);
        assertEquals(3, lost.get());
    }

    @Test
    void aListThatIsAllReadableIsUntouchedAndNothingIsReported() {
        AtomicInteger lost = new AtomicInteger();
        Codec<List<Stack>> codec = LenientLists.of(STACK, EMPTY, lost::addAndGet);
        List<Stack> stacks = List.of(new Stack("a", 1), new Stack("b", 2), new Stack("c", 3));
        JsonElement written = codec.encodeStart(JsonOps.INSTANCE, stacks).getOrThrow();
        assertEquals(stacks, codec.parse(JsonOps.INSTANCE, written).getOrThrow());
        assertEquals(0, lost.get());
    }

    @Test
    void anEmptyListStaysEmptyAndAThingThatIsNotAListIsStillAnError() {
        Codec<List<Stack>> codec = LenientLists.of(STACK, EMPTY, n -> { });
        assertEquals(List.of(), codec.parse(JsonOps.INSTANCE, json("[]")).getOrThrow());
        assertTrue(codec.parse(JsonOps.INSTANCE, json("5")).error().isPresent());
    }

    @Test
    void anElementThatIsReadOnlyPartlyIsKeptAsFarAsItWasReadAndCounted() {
        // an element codec that reads a value but reports a problem with it (as a stack that lost a component does)
        Codec<Integer> partly = new Codec<>() {
            @Override
            public <T> DataResult<T> encode(Integer input, com.mojang.serialization.DynamicOps<T> ops, T prefix) {
                return DataResult.success(ops.createInt(input));
            }

            @Override
            public <T> DataResult<com.mojang.datafixers.util.Pair<Integer, T>> decode(com.mojang.serialization.DynamicOps<T> ops, T input) {
                return ops.getNumberValue(input).flatMap(n -> n.intValue() < 0
                        ? DataResult.error(() -> "negative", com.mojang.datafixers.util.Pair.of(0, input))
                        : DataResult.success(com.mojang.datafixers.util.Pair.of(n.intValue(), input)));
            }
        };
        AtomicInteger lost = new AtomicInteger();
        List<Integer> read = LenientLists.of(partly, -1, lost::addAndGet).parse(JsonOps.INSTANCE, json("[5,-2,7]")).getOrThrow();
        assertEquals(List.of(5, 0, 7), read);
        assertEquals(1, lost.get());
    }
}
