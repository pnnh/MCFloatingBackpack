package pnnh.mcfloatingbackpack.inventory.trash;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/** 022: a stack counts as plain when the only way it differs from the default stack is wear. */
class StackExtraDataTest {
    @Test
    void noPatchOrOnlyDamageIsPlain() {
        assertTrue(StackExtraData.onlyDamage(List.of()));
        assertTrue(StackExtraData.onlyDamage(null));
        assertTrue(StackExtraData.onlyDamage(List.of("minecraft:damage")));
    }

    @Test
    void anyOtherDataMakesTheStackSpecial() {
        for (String id : List.of(
                "minecraft:enchantments",
                "minecraft:custom_name",
                "minecraft:container",
                "minecraft:potion_contents",
                "minecraft:banner_patterns",
                "minecraft:profile",
                "minecraft:map_id",
                "minecraft:written_book_content")) {
            assertFalse(StackExtraData.onlyDamage(List.of(id)), id);
            assertFalse(StackExtraData.onlyDamage(List.of("minecraft:damage", id)), id);
        }
    }
}
