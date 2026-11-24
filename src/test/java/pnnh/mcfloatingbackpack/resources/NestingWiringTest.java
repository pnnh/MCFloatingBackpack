package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 064: every way an item gets into a floating backpack asks the nesting rule; what the rule does not stop. */
class NestingWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theBackpackItemDoesNotFitInsideContainerItems() throws IOException {
        String item = read("item/FloatingBackpackItem.java");
        assertTrue(item.contains("public boolean canFitInsideContainerItems() {\n        return false;"));
    }

    @Test
    void theMainAreaTargetsAreEmptyForNestingAndThereIsAnInternalVersion() throws IOException {
        String inventory = read("inventory/BackpackInventory.java");
        int outer = inventory.indexOf("public List<int[]> mainTargetOrder(ItemStack stack)");
        int internal = inventory.indexOf("public List<int[]> mainTargetOrderInternal(ItemStack stack)");
        assertTrue(outer > 0 && internal > outer);
        String outerBody = inventory.substring(outer, internal);
        assertTrue(outerBody.contains("BackpackNesting.isRisky(stack)") && outerBody.contains("return mainTargetOrderInternal(stack)"));
    }

    @Test
    void onlyTheTrashCansRestoreUsesTheInternalVersion() throws IOException {
        assertTrue(read("network/BackpackTrashPayload.java").contains("backpack::mainTargetOrderInternal"));
        for (String file : new String[] {"network/BackpackTakeFromContainerPayload.java", "inventory/pickup/AutoPickupEvents.java",
                "network/BackpackNetworkTakePayload.java", "network/BackpackQuickTransferPayload.java"}) {
            assertTrue(!read(file).contains("mainTargetOrderInternal"), file);
        }
    }

    @Test
    void theClickAndTheDragAskTheRuleWithTheBackpackAsOrigin() throws IOException {
        String click = read("network/BackpackClickPayload.java");
        assertTrue(click.contains("BackpackNesting.refusal(serverPlayer, ref, nestingHeld)"));
        assertTrue(click.contains("BackpackNesting.rememberTakenOut(serverPlayer, ref, nestingSlotBefore)"));
        assertTrue(read("network/BackpackCursorGesturePayload.java").contains("BackpackNesting.refusal(player, ref, held)"));
    }

    @Test
    void theTrashCanRefusesWhateverTheOriginAndTakingOutRemembersIt() throws IOException {
        String trash = read("network/BackpackTrashPayload.java");
        assertTrue(trash.contains("BackpackNesting.refusalAlways(held)"));
        assertTrue(trash.contains("BackpackNesting.rememberTakenOut(player, ref, taken.taken())"));
        assertTrue(read("network/BackpackTrashSendPayload.java").contains("BackpackNesting.refusalAlways(take.stack())"));
    }

    @Test
    void theMovesFromOutsideCheckBeforeTakingAnything() throws IOException {
        assertTrue(read("network/BackpackQuickTransferPayload.java").contains("BackpackNesting.isRisky(stack)"));
        assertTrue(read("network/BackpackTakeFromContainerPayload.java").contains("BackpackNesting.isRisky(before)"));
        String network = read("network/BackpackNetworkTakePayload.java");
        int check = network.indexOf("BackpackNesting.check(payload.what())");
        int take = network.indexOf("NetworkStorageBridges.tryTake(");
        assertTrue(check > 0 && take > check, "the network is asked for nothing that would be refused");
    }

    @Test
    void theRecordEndsWithTheScreenTheLogoutAndTheWorld() throws IOException {
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("BackpackNesting.forget(player.getUUID())"));
        assertTrue(main.contains("BackpackNesting.forgetNoted(player.getUUID())"));
        assertTrue(main.contains("BackpackNesting.clear()"));
    }

    @Test
    void theNestingCodeNamesNoThirdPartyType() throws IOException {
        for (String file : new String[] {"inventory/nesting/BackpackNesting.java", "inventory/nesting/NestingRules.java",
                "inventory/nesting/NestedOrigins.java"}) {
            String text = read(file);
            for (String forbidden : new String[] {"appeng", "integratedterminals", "sophisticated", "travelersbackpack", "top.theillusivec4", "io.wispforest"}) {
                assertTrue(!text.contains(forbidden), file + " mentions " + forbidden);
            }
        }
    }

    @Test
    void theMessagesAreTranslated() throws IOException {
        for (String lang : new String[] {"en_us", "zh_cn"}) {
            String json = Files.readString(Path.of("src/main/resources/assets/mcfloatingbackpack/lang/" + lang + ".json"), StandardCharsets.UTF_8);
            assertTrue(json.contains("\"message.mcfloatingbackpack.nesting.refused\""), lang);
            assertTrue(json.contains("\"message.mcfloatingbackpack.nesting.contains\""), lang);
        }
    }
}
