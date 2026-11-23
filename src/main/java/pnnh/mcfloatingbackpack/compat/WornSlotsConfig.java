package pnnh.mcfloatingbackpack.compat;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 061: the start-up config ({@code config/mcfloatingbackpack-startup.toml}) that decides whether this mod adds its own slot to the
 * accessory mods (Curios, Accessories): one switch for each. Registering content in another mod's data is decided when the data packs load,
 * before the server config (and so the per-mod levels of 059) is read, and the client config is not loaded on a dedicated server; FML's
 * {@code STARTUP} config type is read the moment it is registered, on both sides, so it is the only one that is ready in time. A change
 * needs a restart. On a multiplayer client this file only matters for the client's own data (the slots a player sees are the server's,
 * which it sends over). The levels FULL, GENERIC and OFF are not involved: they decide what this mod's code does, not what it registers.
 */
public final class WornSlotsConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.BooleanValue CURIOS;
    private static final ModConfigSpec.BooleanValue ACCESSORIES;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment(
                        "Whether this mod adds its own \"Floating Backpack\" slot to the accessory mods below. One switch for each. The slot",
                        "only takes floating backpacks, so they no longer compete with other backpacks for the back slot. Floating backpacks",
                        "can always go into the back slot and the charm slot of these mods; this does not change that.",
                        "Read before the data packs are loaded, so a change needs a RESTART of the game or the server. On a multiplayer",
                        "server the server's value decides what the players see (the server sends the slots over).",
                        "This is not the FULL / GENERIC / OFF level of compat.modLevels: it decides what this mod adds, not what its code does.",
                        "WARNING: turning a switch off removes the slot, and what lies in it is left to the accessory mod. Tested: Curios hands the",
                        "item back to the player; Accessories does NOT (the item is gone). TAKE EVERY BACKPACK OUT OF THE SLOT FIRST. (A backpack",
                        "that is lost this way can still be restored from its snapshot: see the snapshot window and /mcfloatingbackpack snapshot.)")
                .push("slots");
        CURIOS = builder.comment("Add the Floating Backpack slot to Curios (only has an effect when Curios is installed).")
                .define("curios", true);
        ACCESSORIES = builder.comment("Add the Floating Backpack slot to Accessories (only has an effect when Accessories is installed).")
                .define("accessories", true);
        builder.pop();
        SPEC = builder.build();
    }

    private WornSlotsConfig() {
    }

    /**
     * Whether the slot of that accessory mod is to be added. True when the config is not loaded (the default) and for any other mod id.
     */
    public static boolean enabled(String modId) {
        return WornSlotsSwitches.enabled(modId, CURIOS::get, ACCESSORIES::get);
    }
}
