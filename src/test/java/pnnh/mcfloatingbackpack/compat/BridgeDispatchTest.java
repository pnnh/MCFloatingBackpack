package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.QuickTransferLogic;

/** 058: the bridge dispatch, with fake players, menus and stacks. */
class BridgeDispatchTest {
    record Stack(int kind, int count) {
        static final Stack EMPTY = new Stack(0, 0);
    }

    static final QuickTransferLogic.StackOps<Stack> OPS = new QuickTransferLogic.StackOps<>() {
        @Override
        public Stack empty() {
            return Stack.EMPTY;
        }

        @Override
        public boolean isEmpty(Stack s) {
            return s.count() <= 0;
        }

        @Override
        public int count(Stack s) {
            return s.count();
        }

        @Override
        public int maxStack(Stack s) {
            return 64;
        }

        @Override
        public boolean sameKind(Stack a, Stack b) {
            return a.kind() == b.kind();
        }

        @Override
        public Stack withCount(Stack s, int count) {
            return count <= 0 ? Stack.EMPTY : new Stack(s.kind(), count);
        }
    };

    /** A bridge that takes menus named "terminal" and answers with what the test says. */
    static final class FakeBridge implements BridgeDispatch.Bridge<String, String, Stack> {
        final String id;
        final Predicate<String> takes;
        final BiFunction<String, Stack, Stack> answer;
        int handlesCalls;
        int insertCalls;
        Stack lastGiven;

        FakeBridge(String id, Predicate<String> takes, BiFunction<String, Stack, Stack> answer) {
            this.id = id;
            this.takes = takes;
            this.answer = answer;
        }

        @Override
        public String modId() {
            return id;
        }

        @Override
        public boolean handles(String menu) {
            handlesCalls++;
            return takes.test(menu);
        }

        @Override
        public Stack insert(String player, String menu, Stack stack) {
            insertCalls++;
            lastGiven = stack;
            return answer.apply(menu, stack);
        }
    }

    private static final Stack STONE = new Stack(1, 32);

    private final List<String> failures = new ArrayList<>();

    private BridgeDispatch<String, String, Stack> dispatch(CompatRuntimeState state, FakeBridge... bridges) {
        return new BridgeDispatch<>(() -> List.of(bridges), () -> state, OPS, (modId, cause) -> failures.add(modId + ":" + cause.getClass().getSimpleName()));
    }

    private static CompatRuntimeState stateWith(boolean allowUntested, String version, String... modIds) {
        List<ThirdPartyCompat> table = new ArrayList<>();
        Map<String, String> installed = new java.util.HashMap<>();
        for (String id : modIds) {
            table.add(new ThirdPartyCompat(id, id, List.of(VersionRange.of("1.0.0", "2.0.0")), List.of(), null));
            installed.put(id, version);
        }
        return CompatRuntimeState.evaluate(table, id -> Optional.ofNullable(installed.get(id)), allowUntested);
    }

    private static CompatRuntimeState active(String... modIds) {
        return stateWith(false, "1.5.0", modIds);
    }

    @Test
    void noBridgesMeansNobodyTookIt() {
        assertEquals(Optional.empty(), dispatch(active("m")).tryInsert("p", "terminal", STONE));
    }

    @Test
    void aBridgeThatTakesTheMenuPutsEverythingIn() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        Optional<Stack> result = dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE);
        assertEquals(Optional.of(Stack.EMPTY), result);
        assertEquals(1, bridge.insertCalls);
    }

    @Test
    void aPartialInsertLeavesTheDifference() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> new Stack(stack.kind(), 10));
        assertEquals(Optional.of(new Stack(1, 10)), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
    }

    @Test
    void aBridgeThatDoesNotTakeTheMenuIsNotUsed() {
        FakeBridge bridge = new FakeBridge("m", menu -> false, (menu, stack) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "chest", STONE));
        assertEquals(0, bridge.insertCalls);
    }

    @Test
    void aBridgeThatIsNotActiveIsNotAsked() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(stateWith(false, "3.0.0", "m"), bridge).tryInsert("p", "terminal", STONE));
        assertEquals(0, bridge.handlesCalls);
        assertEquals(Optional.empty(), dispatch(CompatRuntimeState.empty(), bridge).tryInsert("p", "terminal", STONE));
    }

    @Test
    void aForcedBridgeIsAskedLikeAnActiveOne() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        assertEquals(Optional.of(Stack.EMPTY), dispatch(stateWith(true, "3.0.0", "m"), bridge).tryInsert("p", "terminal", STONE));
    }

    @Test
    void noStateYetMeansNothingIsOn() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        BridgeDispatch<String, String, Stack> noState = new BridgeDispatch<>(() -> List.of(bridge), () -> null, OPS, (id, e) -> { });
        assertEquals(Optional.empty(), noState.tryInsert("p", "terminal", STONE));
    }

    @Test
    void anEmptyStackIsLeftAlone() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "terminal", Stack.EMPTY));
        assertEquals(0, bridge.handlesCalls);
    }

    @Test
    void theBridgeGetsACopyNotTheOriginal() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE);
        assertNotSame(STONE, bridge.lastGiven);
        assertEquals(STONE, bridge.lastGiven);
    }

    @Test
    void theNextBridgeIsTriedWhenTheFirstDoesNotTakeTheMenu() {
        FakeBridge first = new FakeBridge("a", menu -> false, (menu, stack) -> Stack.EMPTY);
        FakeBridge second = new FakeBridge("b", menu -> true, (menu, stack) -> Stack.EMPTY);
        assertEquals(Optional.of(Stack.EMPTY), dispatch(active("a", "b"), first, second).tryInsert("p", "terminal", STONE));
        assertEquals(1, first.handlesCalls);
        assertEquals(1, second.insertCalls);
    }

    // --- failures ------------------------------------------------------------------------------------------------

    @Test
    void aRuntimeExceptionSwitchesTheBridgeOffAndLeavesTheStack() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> {
            throw new IllegalArgumentException("boom");
        });
        CompatRuntimeState state = active("m");
        BridgeDispatch<String, String, Stack> dispatch = dispatch(state, bridge);
        assertEquals(Optional.empty(), dispatch.tryInsert("p", "terminal", STONE));
        assertEquals(List.of("m:IllegalArgumentException"), failures);
        assertEquals(Optional.of(CompatStatus.FAILED), state.status("m"));
    }

    @Test
    void aLinkageErrorIsCaughtToo() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> {
            throw new NoSuchMethodError("gone");
        });
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
        assertEquals(List.of("m:NoSuchMethodError"), failures);
    }

    @Test
    void aMissingClassInHandlesIsCaughtToo() {
        FakeBridge bridge = new FakeBridge("m", menu -> {
            throw new NoClassDefFoundError("org/example/Gone");
        }, (menu, stack) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
        assertEquals(List.of("m:NoClassDefFoundError"), failures);
    }

    @Test
    void afterAFailureTheBridgeIsNotCalledAgainAndNothingIsReportedAgain() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> {
            throw new IllegalStateException("boom");
        });
        BridgeDispatch<String, String, Stack> dispatch = dispatch(active("m"), bridge);
        dispatch.tryInsert("p", "terminal", STONE);
        dispatch.tryInsert("p", "terminal", STONE);
        dispatch.tryInsert("p", "terminal", STONE);
        assertEquals(1, bridge.insertCalls);
        assertEquals(1, failures.size());
    }

    @Test
    void aRemainderOfAnotherItemIsAFailure() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> new Stack(99, 5));
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
        assertEquals(1, failures.size());
    }

    @Test
    void aRemainderLargerThanTheStackIsAFailure() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> new Stack(stack.kind(), stack.count() + 1));
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
        assertEquals(1, failures.size());
    }

    @Test
    void aNullRemainderIsAFailure() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> null);
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
        assertEquals(1, failures.size());
    }

    @Test
    void aRemainderTheSizeOfTheStackIsFine() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> stack);
        assertEquals(Optional.of(STONE), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
        assertTrue(failures.isEmpty());
    }

    @Test
    void anErrorThatIsNotALinkageErrorIsNotSwallowed() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> {
            throw new OutOfMemoryError("not ours");
        });
        try {
            dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE);
            throw new AssertionError("the OutOfMemoryError should have passed through");
        } catch (OutOfMemoryError expected) {
            assertTrue(failures.isEmpty());
        }
    }
}
