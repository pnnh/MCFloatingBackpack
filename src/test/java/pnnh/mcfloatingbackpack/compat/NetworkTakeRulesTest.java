package pnnh.mcfloatingbackpack.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.compat.BridgeDispatchTest.Stack;

/** 060: the amounts and the conservation of taking a stack out of a network terminal. */
class NetworkTakeRulesTest {
    private static final Stack PEARL = new Stack(2, 1);

    private static final pnnh.mcfloatingbackpack.inventory.QuickTransferLogic.StackOps<Stack> OPS = new BridgeDispatchTest.MaxOps(16);

    @Test
    void theAmountIsAFullStackOfTheItemNotWhatTheClientSays() {
        assertEquals(16, NetworkTakeRules.amountFor(OPS, PEARL));
        assertEquals(64, NetworkTakeRules.amountFor(BridgeDispatchTest.OPS, new Stack(1, 1)));
        assertEquals(0, NetworkTakeRules.amountFor(OPS, Stack.EMPTY));
        // an amount the client names (the item held on Integrated Terminals' own cursor): never more than a full stack, none means a full stack
        assertEquals(16, NetworkTakeRules.amountFor(OPS, PEARL, 0));
        assertEquals(16, NetworkTakeRules.amountFor(OPS, PEARL, -3));
        assertEquals(5, NetworkTakeRules.amountFor(OPS, PEARL, 5));
        assertEquals(16, NetworkTakeRules.amountFor(OPS, PEARL, 40));
        assertEquals(0, NetworkTakeRules.amountFor(OPS, Stack.EMPTY, 5));
    }

    @Test
    void aLegalAnswerIsNothingOrTheSameItemAndNotMore() {
        assertTrue(NetworkTakeRules.legalTake(OPS, PEARL, Stack.EMPTY, 16));
        assertTrue(NetworkTakeRules.legalTake(OPS, PEARL, new Stack(2, 16), 16));
        assertTrue(NetworkTakeRules.legalTake(OPS, PEARL, new Stack(2, 3), 16));
        assertFalse(NetworkTakeRules.legalTake(OPS, PEARL, new Stack(2, 17), 16));
        assertFalse(NetworkTakeRules.legalTake(OPS, PEARL, new Stack(9, 4), 16));
        assertFalse(NetworkTakeRules.legalTake(OPS, PEARL, null, 16));
    }

    @Test
    void everythingFitsInTheBackpack() {
        NetworkTakeRules.Split split = NetworkTakeRules.distribute(64, 0, 0);
        assertEquals(new NetworkTakeRules.Split(64, 0, 0), split);
    }

    @Test
    void aPartGoesBackToTheNetwork() {
        assertEquals(new NetworkTakeRules.Split(54, 10, 0), NetworkTakeRules.distribute(64, 10, 0));
    }

    @Test
    void nothingFitsAndAllGoesBack() {
        assertEquals(new NetworkTakeRules.Split(0, 64, 0), NetworkTakeRules.distribute(64, 64, 0));
    }

    @Test
    void whatTheNetworkWillNotTakeBackGoesToTheFallback() {
        assertEquals(new NetworkTakeRules.Split(54, 4, 6), NetworkTakeRules.distribute(64, 10, 6));
        assertEquals(new NetworkTakeRules.Split(0, 0, 64), NetworkTakeRules.distribute(64, 64, 64));
        // a give back that was not even tried leaves all of it
        assertEquals(new NetworkTakeRules.Split(54, 0, 10), NetworkTakeRules.distribute(64, 10, 10));
    }

    @Test
    void nothingTakenIsNothingEverywhere() {
        assertEquals(new NetworkTakeRules.Split(0, 0, 0), NetworkTakeRules.distribute(0, 0, 0));
    }

    @Test
    void outOfRangeAnswersAreClampedAndTheTotalStaysTheSame() {
        assertEquals(64, NetworkTakeRules.distribute(64, 100, 100).total());
        assertEquals(64, NetworkTakeRules.distribute(64, -5, -5).total());
        assertEquals(new NetworkTakeRules.Split(64, 0, 0), NetworkTakeRules.distribute(64, -1, 7));
    }

    @Test
    void conservationHoldsForAnyAnswers() {
        Random random = new Random(60);
        for (int i = 0; i < 2000; i++) {
            int taken = random.nextInt(65);
            int a = random.nextInt(80) - 5;
            int b = random.nextInt(80) - 5;
            NetworkTakeRules.Split split = NetworkTakeRules.distribute(taken, a, b);
            assertEquals(taken, split.total(), taken + " " + a + " " + b);
            assertTrue(split.toBackpack() >= 0 && split.backToNetwork() >= 0 && split.fallback() >= 0);
        }
    }
}
