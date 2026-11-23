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

    private Predicate<String> levelAllows = modId -> true;

    @SafeVarargs
    private BridgeDispatch<String, String, Stack> dispatch(CompatRuntimeState state, BridgeDispatch.Bridge<String, String, Stack>... bridges) {
        return new BridgeDispatch<>(() -> List.of(bridges), () -> state, OPS,
                (modId, cause) -> failures.add(modId + ":" + cause.getClass().getSimpleName()), modId -> levelAllows.test(modId));
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
        BridgeDispatch<String, String, Stack> noState = new BridgeDispatch<>(() -> List.of(bridge), () -> null, OPS, (id, e) -> { }, id -> true);
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

    // --- 059: the server's level ---------------------------------------------------------------------------------

    @Test
    void aBridgeWhoseModIsNotAtFullIsSkippedWithoutAFailure() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        CompatRuntimeState state = active("m");
        levelAllows = modId -> false;
        assertEquals(Optional.empty(), dispatch(state, bridge).tryInsert("p", "terminal", STONE));
        assertEquals(0, bridge.handlesCalls);
        assertTrue(failures.isEmpty());
        assertEquals(Optional.of(CompatStatus.ACTIVE), state.status("m"));
    }

    @Test
    void aBridgeIsAskedAgainWhenTheLevelAllowsItAgain() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        BridgeDispatch<String, String, Stack> dispatch = dispatch(active("m"), bridge);
        levelAllows = modId -> false;
        assertEquals(Optional.empty(), dispatch.tryInsert("p", "terminal", STONE));
        levelAllows = modId -> true;
        assertEquals(Optional.of(Stack.EMPTY), dispatch.tryInsert("p", "terminal", STONE));
    }

    @Test
    void theLevelIsAskedPerMod() {
        FakeBridge first = new FakeBridge("a", menu -> true, (menu, stack) -> Stack.EMPTY);
        FakeBridge second = new FakeBridge("b", menu -> true, (menu, stack) -> Stack.EMPTY);
        levelAllows = modId -> "b".equals(modId);
        assertEquals(Optional.of(Stack.EMPTY), dispatch(active("a", "b"), first, second).tryInsert("p", "terminal", STONE));
        assertEquals(0, first.insertCalls);
        assertEquals(1, second.insertCalls);
    }

    @Test
    void aForcedBridgeStillNeedsTheLevel() {
        FakeBridge bridge = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        levelAllows = modId -> false;
        assertEquals(Optional.empty(), dispatch(stateWith(true, "3.0.0", "m"), bridge).tryInsert("p", "terminal", STONE));
    }

    // --- 060: taking out of a network and giving back -------------------------------------------------------------

    /** The same stacks with another maximum stack size. */
    static final class MaxOps implements QuickTransferLogic.StackOps<Stack> {
        private final int max;

        MaxOps(int max) {
            this.max = max;
        }

        @Override
        public Stack empty() {
            return OPS.empty();
        }

        @Override
        public boolean isEmpty(Stack s) {
            return OPS.isEmpty(s);
        }

        @Override
        public int count(Stack s) {
            return OPS.count(s);
        }

        @Override
        public int maxStack(Stack s) {
            return max;
        }

        @Override
        public boolean sameKind(Stack a, Stack b) {
            return OPS.sameKind(a, b);
        }

        @Override
        public Stack withCount(Stack s, int count) {
            return OPS.withCount(s, count);
        }
    }

    /** A bridge that takes with the answers the test gives. */
    static final class TakeBridge implements BridgeDispatch.Bridge<String, String, Stack> {
        final String id;
        final java.util.function.BiFunction<Stack, Long, Stack> takes;
        final java.util.function.BiFunction<Stack, Long, Stack> givesBack;
        boolean handlesTake = true;
        int takeCalls;
        int giveBackCalls;
        Stack lastWhat;
        long lastHandle = -1;

        TakeBridge(String id, java.util.function.BiFunction<Stack, Long, Stack> takes, java.util.function.BiFunction<Stack, Long, Stack> givesBack) {
            this.id = id;
            this.takes = takes;
            this.givesBack = givesBack;
        }

        @Override
        public String modId() {
            return id;
        }

        @Override
        public boolean handles(String menu) {
            return false;
        }

        @Override
        public Stack insert(String player, String menu, Stack stack) {
            throw new AssertionError("the store path is not this bridge's");
        }

        @Override
        public boolean handlesTake(String menu) {
            return handlesTake;
        }

        @Override
        public Stack take(String player, String menu, Stack what, long handle) {
            takeCalls++;
            lastWhat = what;
            lastHandle = handle;
            return takes.apply(what, handle);
        }

        @Override
        public Stack giveBack(String player, String menu, Stack stack, long handle) {
            giveBackCalls++;
            return givesBack.apply(stack, handle);
        }
    }

    /** The count of a request is how much is asked for (the server clamps it to a full stack of 64 first). */
    private static final Stack ASK = new Stack(1, 64);

    @Test
    void aBridgeThatTakesAnswersWithWhatItTook() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> new Stack(what.kind(), 64), (s, h) -> Stack.EMPTY);
        assertEquals(Optional.of(new Stack(1, 64)), dispatch(active("m"), bridge).tryTake("p", "terminal", ASK, 7L));
        assertEquals(7L, bridge.lastHandle);
        assertEquals(ASK, bridge.lastWhat);
    }

    @Test
    void theCountOfTheRequestIsTheMostTheBridgeMayGive() {
        Stack tenAsked = new Stack(1, 10);
        TakeBridge exact = new TakeBridge("m", (what, handle) -> new Stack(what.kind(), 10), (s, h) -> Stack.EMPTY);
        assertEquals(Optional.of(new Stack(1, 10)), dispatch(active("m"), exact).tryTake("p", "terminal", tenAsked, 0L));
        assertEquals(tenAsked, exact.lastWhat, "the bridge is asked for exactly that many");
        TakeBridge greedy = new TakeBridge("m", (what, handle) -> new Stack(what.kind(), 11), (s, h) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), greedy).tryTake("p", "terminal", tenAsked, 0L), "more than asked is a broken bridge");
    }

    @Test
    void aTakeThatFindsNothingIsStillHandled() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> Stack.EMPTY, (s, h) -> Stack.EMPTY);
        assertEquals(Optional.of(Stack.EMPTY), dispatch(active("m"), bridge).tryTake("p", "terminal", ASK, 0L));
    }

    @Test
    void noBridgeNotActiveOrNotHandlingMeansNobodyTookIt() {
        assertEquals(Optional.empty(), dispatch(active("m")).tryTake("p", "terminal", ASK, 0L));
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> new Stack(what.kind(), 5), (s, h) -> Stack.EMPTY);
        bridge.handlesTake = false;
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryTake("p", "terminal", ASK, 0L));
        bridge.handlesTake = true;
        assertEquals(Optional.empty(), dispatch(stateWith(false, "3.0.0", "m"), bridge).tryTake("p", "terminal", ASK, 0L));
        assertEquals(Optional.empty(), dispatch(CompatRuntimeState.empty(), bridge).tryTake("p", "terminal", ASK, 0L));
        assertEquals(0, bridge.takeCalls);
    }

    @Test
    void aTakeOfAnEmptyStackIsNeverAsked() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> new Stack(what.kind(), 5), (s, h) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryTake("p", "terminal", Stack.EMPTY, 0L));
        assertEquals(0, bridge.takeCalls);
    }

    @Test
    void aTakeThatReturnsSomethingIllegalIsAFailure() {
        TakeBridge otherItem = new TakeBridge("a", (what, handle) -> new Stack(99, 5), (s, h) -> Stack.EMPTY);
        TakeBridge tooMuch = new TakeBridge("b", (what, handle) -> new Stack(what.kind(), 65), (s, h) -> Stack.EMPTY);
        TakeBridge nothingAtAll = new TakeBridge("c", (what, handle) -> null, (s, h) -> Stack.EMPTY);
        for (TakeBridge bridge : new TakeBridge[] {otherItem, tooMuch, nothingAtAll}) {
            failures.clear();
            CompatRuntimeState state = active(bridge.id);
            assertEquals(Optional.empty(), dispatch(state, bridge).tryTake("p", "terminal", ASK, 0L), bridge.id);
            assertEquals(1, failures.size(), bridge.id);
            assertEquals(Optional.of(CompatStatus.FAILED), state.status(bridge.id));
        }
    }

    @Test
    void aTakeThatThrowsSwitchesTheBridgeOffAndItIsNotAskedAgain() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> {
            throw new NoSuchMethodError("gone");
        }, (s, h) -> Stack.EMPTY);
        BridgeDispatch<String, String, Stack> dispatch = dispatch(active("m"), bridge);
        assertEquals(Optional.empty(), dispatch.tryTake("p", "terminal", ASK, 0L));
        assertEquals(Optional.empty(), dispatch.tryTake("p", "terminal", ASK, 0L));
        assertEquals(1, bridge.takeCalls);
        assertEquals(List.of("m:NoSuchMethodError"), failures);
    }

    @Test
    void theBridgeIsSkippedWhileTheLevelDoesNotAllowItAndIsNotAFailure() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> new Stack(what.kind(), 5), (s, h) -> Stack.EMPTY);
        levelAllows = modId -> false;
        CompatRuntimeState state = active("m");
        assertEquals(Optional.empty(), dispatch(state, bridge).tryTake("p", "terminal", ASK, 0L));
        assertEquals(Optional.empty(), dispatch(state, bridge).tryGiveBack("p", "terminal", new Stack(1, 3), 0L));
        assertEquals(0, bridge.takeCalls + bridge.giveBackCalls);
        assertTrue(failures.isEmpty());
        assertEquals(Optional.of(CompatStatus.ACTIVE), state.status("m"));
    }

    @Test
    void aGiveBackAnswersWithWhatTheNetworkDidNotTake() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> Stack.EMPTY, (s, h) -> new Stack(s.kind(), 2));
        assertEquals(Optional.of(new Stack(1, 2)), dispatch(active("m"), bridge).tryGiveBack("p", "terminal", new Stack(1, 10), 3L));
        TakeBridge all = new TakeBridge("m", (what, handle) -> Stack.EMPTY, (s, h) -> Stack.EMPTY);
        assertEquals(Optional.of(Stack.EMPTY), dispatch(active("m"), all).tryGiveBack("p", "terminal", new Stack(1, 10), 3L));
    }

    @Test
    void aGiveBackThatReturnsMoreOrAnotherItemOrNullIsAFailure() {
        TakeBridge more = new TakeBridge("a", (what, handle) -> Stack.EMPTY, (s, h) -> new Stack(s.kind(), s.count() + 1));
        TakeBridge other = new TakeBridge("b", (what, handle) -> Stack.EMPTY, (s, h) -> new Stack(99, 1));
        TakeBridge nothing = new TakeBridge("c", (what, handle) -> Stack.EMPTY, (s, h) -> null);
        for (TakeBridge bridge : new TakeBridge[] {more, other, nothing}) {
            failures.clear();
            assertEquals(Optional.empty(), dispatch(active(bridge.id), bridge).tryGiveBack("p", "terminal", new Stack(1, 10), 0L), bridge.id);
            assertEquals(1, failures.size(), bridge.id);
        }
    }

    @Test
    void aGiveBackOfNothingIsNeverAsked() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> Stack.EMPTY, (s, h) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryGiveBack("p", "terminal", Stack.EMPTY, 0L));
        assertEquals(0, bridge.giveBackCalls);
    }

    @Test
    void theStorePathIsNotAskedWhenABridgeOnlyTakes() {
        TakeBridge bridge = new TakeBridge("m", (what, handle) -> Stack.EMPTY, (s, h) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), bridge).tryInsert("p", "terminal", STONE));
    }

    @Test
    void aBridgeWithoutTakeSupportIsNotAFailureWhenOnlyTheStorePathExists() {
        FakeBridge storeOnly = new FakeBridge("m", menu -> true, (menu, stack) -> Stack.EMPTY);
        assertEquals(Optional.empty(), dispatch(active("m"), storeOnly).tryTake("p", "terminal", ASK, 0L));
        assertTrue(failures.isEmpty(), "the default handlesTake is false, so take is never called");
    }
}
