package pnnh.mcfloatingbackpack.compat.accessories;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/**
 * 036: Accessories equips any item that fits one of its slots on a right click, swapping it with what is worn there, which swapped
 * the backpack in the hand for the one on the back every time the panel was opened. The classes cannot be loaded here (they need
 * Accessories), so this checks the source: every tier is registered with an Accessory that refuses the equip, and the mod's
 * constructor registers them once the items exist.
 */
class BackpackNotEquippedFromUseTest {
    private static final Path SRC = Path.of("src/main/java/pnnh/mcfloatingbackpack");

    private static String read(String relative) throws IOException {
        return Files.readString(SRC.resolve(relative), StandardCharsets.UTF_8);
    }

    @Test
    void theAccessoryRefusesTheEquipFromUse() throws IOException {
        String accessory = read("compat/accessories/BackpackAccessory.java");
        assertTrue(accessory.contains("canEquipFromUse(ItemStack stack, SlotReference reference)"));
        assertTrue(accessory.replaceAll("\\s+", " ").contains("return false;"));
    }

    @Test
    void everyTierIsRegisteredWithIt() throws IOException {
        String compat = read("compat/accessories/AccessoriesCompat.java");
        for (String tier : new String[] {"LEATHER", "IRON", "GOLD", "DIAMOND", "NETHERITE"}) {
            assertTrue(compat.contains("ModItems." + tier + "_FLOATING_BACKPACK.get()"), tier);
        }
        assertTrue(compat.contains("AccessoriesAPI.registerAccessory(item, new BackpackAccessory())"));
    }

    @Test
    void theConstructorRegistersThemInTheCommonSetup() throws IOException {
        String main = read("MCFloatingBackpack.java");
        assertTrue(main.contains("FMLCommonSetupEvent"));
        assertTrue(main.contains("AccessoriesCompat::registerAccessories"));
    }
}
