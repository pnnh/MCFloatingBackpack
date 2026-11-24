package pnnh.mcfloatingbackpack.inventory.nesting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import pnnh.mcfloatingbackpack.inventory.nesting.NestingRules.Verdict;

class NestingRulesTest {
    /** A stack as the rules see it: a kind, what it holds piece by piece, and data that cannot be read piece by piece. */
    private record Fake(String kind, List<Fake> inside, boolean container, boolean opaque, boolean opaqueMentionsBackpack) {
        static Fake plain(String kind) {
            return new Fake(kind, List.of(), false, false, false);
        }

        static Fake backpack() {
            return plain("backpack");
        }

        static Fake box(Fake... contents) {
            return new Fake("box", List.of(contents), true, false, false);
        }

        static Fake modded(boolean mentions) {
            return new Fake("modded", List.of(), false, true, mentions);
        }
    }

    private static final NestingRules.Probe<Fake> PROBE = new NestingRules.Probe<>() {
        @Override
        public boolean isEmpty(Fake stack) {
            return stack == null || stack.kind().isEmpty();
        }

        @Override
        public boolean isBackpack(Fake stack) {
            return stack.kind().equals("backpack");
        }

        @Override
        public boolean hasContainerData(Fake stack) {
            return stack.container();
        }

        @Override
        public boolean hasOpaqueData(Fake stack) {
            return stack.opaque();
        }

        @Override
        public List<Fake> children(Fake stack) {
            return stack.inside();
        }

        @Override
        public boolean mentionsBackpack(Fake stack) {
            return stack.opaqueMentionsBackpack();
        }
    };

    private static Verdict check(Fake stack) {
        return NestingRules.check(PROBE, stack);
    }

    @Test
    void aBackpackIsABackpack() {
        assertEquals(Verdict.IS_BACKPACK, check(Fake.backpack()));
    }

    @Test
    void anOrdinaryItemAndNothingAreFine() {
        assertEquals(Verdict.OK, check(Fake.plain("stone")));
        assertEquals(Verdict.OK, check(Fake.plain("")));
    }

    @Test
    void anEmptyBoxAndABoxWithOrdinaryThingsAreFine() {
        assertEquals(Verdict.OK, check(Fake.box()));
        assertEquals(Verdict.OK, check(Fake.box(Fake.plain("stone"), Fake.plain("dirt"))));
    }

    @Test
    void aBoxWithABackpackInItIsRefused() {
        assertEquals(Verdict.CONTAINS_BACKPACK, check(Fake.box(Fake.plain("stone"), Fake.backpack())));
    }

    @Test
    void aBackpackSeveralLevelsDownIsFound() {
        assertEquals(Verdict.CONTAINS_BACKPACK, check(Fake.box(Fake.box(Fake.box(Fake.backpack())))));
        assertEquals(Verdict.OK, check(Fake.box(Fake.box(Fake.box(Fake.plain("stone"))))));
    }

    @Test
    void emptyChildrenAreSkipped() {
        assertEquals(Verdict.OK, check(Fake.box(Fake.plain(""), Fake.plain("stone"))));
    }

    @Test
    void anotherModsDataIsSearchedAsAFallback() {
        assertEquals(Verdict.CONTAINS_BACKPACK, check(Fake.modded(true)));
        assertEquals(Verdict.OK, check(Fake.modded(false)));
    }

    @Test
    void aBoxHoldingAnotherModsContainerWithABackpackIsRefused() {
        assertEquals(Verdict.CONTAINS_BACKPACK, check(Fake.box(Fake.modded(true))));
        assertEquals(Verdict.OK, check(Fake.box(Fake.modded(false))));
    }

    @Test
    void theFallbackIsNotUsedWithoutOpaqueData() {
        // a box whose data is fully readable never asks the fallback, even if it would say yes
        Fake readableOnly = new Fake("box", List.of(Fake.plain("stone")), true, false, true);
        assertEquals(Verdict.OK, check(readableOnly));
    }

    @Test
    void nestingDeeperThanTheLimitIsRefusedAsTheSafeAnswer() {
        Fake deep = Fake.plain("stone");
        for (int i = 0; i < NestingRules.MAX_DEPTH + 2; i++) {
            deep = Fake.box(deep);
        }
        assertEquals(Verdict.CONTAINS_BACKPACK, check(deep));
        Fake atTheLimit = Fake.plain("stone");
        for (int i = 0; i < NestingRules.MAX_DEPTH; i++) {
            atTheLimit = Fake.box(atTheLimit);
        }
        assertEquals(Verdict.OK, check(atTheLimit));
    }
}
