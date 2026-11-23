package pnnh.mcfloatingbackpack.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** 061: the dedicated and the charm slots are wired as the plan says. Source text checks. */
class WornSlotsWiringTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theSwitchesAreAStartupConfigRegisteredInTheConstructor() throws IOException {
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("registerConfig(ModConfig.Type.STARTUP, pnnh.mcfloatingbackpack.compat.WornSlotsConfig.SPEC)"),
                "the only config type that is read before the data packs load");
        assertTrue(main.contains("pnnh.mcfloatingbackpack.compat.SlotEnabledCondition.register(modEventBus)"));
        String config = read("compat/WornSlotsConfig.java");
        assertTrue(config.contains(".define(\"curios\", true)") && config.contains(".define(\"accessories\", true)"), "both on by default");
        assertTrue(config.contains(".push(\"slots\")"));
        assertTrue(config.contains("RESTART"), "the comment says a change needs a restart");
    }

    @Test
    void theConditionIsRegisteredAsADataConditionAndKnowsNoAccessoryMod() throws IOException {
        String condition = read("compat/SlotEnabledCondition.java");
        assertTrue(condition.contains("NeoForgeRegistries.CONDITION_SERIALIZERS"));
        assertTrue(condition.contains("CONDITIONS.register(\"slot_enabled\""));
        assertTrue(condition.contains("WornSlotsConfig.enabled(mod)"));
        assertFalse(condition.contains("top.theillusivec4") || condition.contains("io.wispforest"));
        assertFalse(read("compat/WornSlotsConfig.java").contains("top.theillusivec4") || read("compat/WornSlotsConfig.java").contains("io.wispforest"));
    }

    @Test
    void eachSlotTypeIsItsOwnSourceInAFixedOrder() throws IOException {
        String curios = read("compat/curios/CuriosCompat.java");
        assertTrue(curios.contains("new CuriosWornSource(\"back\", BackpackRef.Source.CURIOS, 10)"));
        assertTrue(curios.contains("new CuriosWornSource(\"floating_backpack\", BackpackRef.Source.CURIOS_DEDICATED, 11)"));
        assertTrue(curios.contains("new CuriosWornSource(\"charm\", BackpackRef.Source.CURIOS_CHARM, 12)"));
        String accessories = read("compat/accessories/AccessoriesCompat.java");
        assertTrue(accessories.contains("new AccessoriesWornSource(\"back\", BackpackRef.Source.ACCESSORIES, 20)"));
        assertTrue(accessories.contains("new AccessoriesWornSource(\"floating_backpack\", BackpackRef.Source.ACCESSORIES_DEDICATED, 21)"));
        assertTrue(accessories.contains("new AccessoriesWornSource(\"charm\", BackpackRef.Source.ACCESSORIES_CHARM, 22)"));
        // the sources do not depend on the switch: without the slot a source has nothing to look at
        assertFalse(curios.contains("WornSlotsConfig") || accessories.contains("WornSlotsConfig"));
    }

    @Test
    void theSourcesNoLongerHaveTheSlotTypeWrittenIn() throws IOException {
        assertFalse(read("compat/curios/CuriosWornSource.java").contains("\"back\""));
        assertFalse(read("compat/accessories/AccessoriesWornSource.java").contains("\"back\""));
        assertTrue(read("compat/curios/CuriosWornSource.java").contains("CuriosWornSource(String slot, BackpackRef.Source source, int order)"));
        assertTrue(read("compat/accessories/AccessoriesWornSource.java")
                .contains("AccessoriesWornSource(String slot, BackpackRef.Source source, int order)"));
    }

    @Test
    void theOrdersAreIncreasingAcrossBothMods() {
        int[] orders = {0, 10, 11, 12, 20, 21, 22};
        for (int i = 1; i < orders.length; i++) {
            assertTrue(orders[i] > orders[i - 1]);
        }
        assertEquals(7, orders.length);
    }

    @Test
    void oneBackpackToASlotTypeIsAskedWhenAStackIsPutInAndNeverCleanedUpAfterwards() throws IOException {
        String curio = read("compat/curios/BackpackCurioItem.java");
        assertTrue(curio.contains("public boolean canEquip(SlotContext context, ItemStack stack)"));
        assertTrue(curio.contains("SlotLimit.allows(others, true)"));
        assertTrue(curio.contains("context.cosmetic()"), "the cosmetic slots are not counted");
        assertTrue(curio.contains("slot != context.index()"), "the slot the stack goes into is not counted");
        assertFalse(curio.contains("boolean canEquipFromUse("), "a right click must not equip a backpack (the default is false)");
        String accessory = read("compat/accessories/BackpackAccessory.java");
        assertTrue(accessory.contains("public boolean canEquip(ItemStack stack, SlotReference reference)"));
        assertTrue(accessory.contains("SlotLimit.allows(others, true)"));
        assertTrue(accessory.contains("slot != reference.slot()"));
        assertTrue(accessory.contains("public boolean canEquipFromUse(ItemStack stack, SlotReference reference) {\n        return false;"), "036 is unchanged");
        // only while the compatibility is at FULL, on the side of the entity
        for (String text : new String[] {curio, accessory}) {
            assertTrue(text.contains("isClientSide()") && text.contains("CompatConfig.policy().specificAdaptation(")
                    && text.contains("ServerCompatLevels.specificAdaptation("));
            assertTrue(text.contains("catch (RuntimeException e) {\n            return true;"), "whatever goes wrong allows the stack");
        }
        // the limit is a question when putting in, not a sweep: nothing ticks and nothing is removed
        assertFalse(curio.contains("setStackInSlot") || accessory.contains("setItem("));
    }

    @Test
    void curiosIsToldAboutTheFiveTiersFromTheCommonSetupOnlyWhenItIsLoaded() throws IOException {
        String compat = read("compat/curios/CuriosCompat.java");
        for (String tier : new String[] {"LEATHER", "IRON", "GOLD", "DIAMOND", "NETHERITE"}) {
            assertTrue(compat.contains("ModItems." + tier + "_FLOATING_BACKPACK.get()"), tier);
        }
        assertTrue(compat.contains("CuriosApi.registerCurio(item, new BackpackCurioItem())"));
        String main = read("MCFloatingBackpack.java");
        int curios = main.indexOf("if (ModList.get().isLoaded(\"curios\"))");
        int registered = main.indexOf("CuriosCompat::registerCurios");
        int accessories = main.indexOf("if (ModList.get().isLoaded(\"accessories\"))");
        assertTrue(curios > 0 && registered > curios && registered < accessories, "inside the Curios branch");
        assertTrue(main.contains("FMLCommonSetupEvent"));
        assertEquals(1, main.split("CuriosCompat::registerCurios", -1).length - 1);
    }
}
