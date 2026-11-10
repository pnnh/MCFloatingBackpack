package pnnh.mcfloatingbackpack.diag;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.compat.CompatConfig;
import pnnh.mcfloatingbackpack.inventory.ModDataComponents;
import pnnh.mcfloatingbackpack.inventory.identity.BackpackIdentity;

/**
 * This mod's own diagnostics. Messages carry a level and are written to the log (always as lines that reach {@code latest.log},
 * not only {@code debug.log}) when the level of that side's config allows it; the client and the server have a setting each.
 * Apart from the log, every player's last panel actions are kept in memory ({@link #record}) whatever the setting is, so a
 * suspicious event can say what led to it ({@link #dump}).
 */
public final class DiagLog {
    public enum Side {
        CLIENT,
        SERVER
    }

    private static final int RING_SIZE = 40;
    private static final Map<UUID, EventRing> RINGS = new ConcurrentHashMap<>();

    private DiagLog() {
    }

    /** The configured level of a side; before its config has loaded, {@link LogLevel#WARN}. */
    public static LogLevel level(Side side) {
        try {
            return side == Side.CLIENT ? CompatConfig.LOG_LEVEL.get() : DiagConfig.SERVER_LEVEL.get();
        } catch (IllegalStateException notLoadedYet) {
            return LogLevel.WARN;
        }
    }

    public static boolean enabled(Side side, LogLevel message) {
        return level(side).allows(message);
    }

    public static void log(Side side, LogLevel message, String format, Object... args) {
        if (!enabled(side, message)) {
            return;
        }
        String prefix = side == Side.CLIENT ? "[diag client] " : "[diag server] ";
        switch (message) {
            case ERROR -> MCFloatingBackpack.LOGGER.error(prefix + format, args);
            case WARN -> MCFloatingBackpack.LOGGER.warn(prefix + format, args);
            case INFO -> MCFloatingBackpack.LOGGER.info(prefix + format, args);
            // DEBUG lines go out as INFO so that they reach latest.log; the prefix says what they are.
            default -> MCFloatingBackpack.LOGGER.info(prefix + "[debug] " + format, args);
        }
    }

    /** Notes a panel action of a player: kept in memory, and written when the server level is DEBUG. */
    public static void record(UUID player, String event) {
        RINGS.computeIfAbsent(player, id -> new EventRing(RING_SIZE)).add(event);
        log(Side.SERVER, LogLevel.DEBUG, "{} {}", shortId(player), event);
    }

    /** The player's last recorded actions, oldest first. */
    public static List<String> dump(UUID player) {
        EventRing ring = RINGS.get(player);
        return ring == null ? List.of() : ring.snapshot();
    }

    public static void forget(UUID player) {
        RINGS.remove(player);
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8);
    }

    /** A stack in a few words: {@code minecraft:cobblestone x12}, {@code -} for nothing, the backpack's short id for a backpack. */
    public static String stack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "-";
        }
        StringBuilder out = new StringBuilder(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString())
                .append(" x")
                .append(stack.getCount());
        BackpackIdentity identity = stack.get(ModDataComponents.BACKPACK_IDENTITY.get());
        if (identity != null) {
            out.append(" #").append(identity.uuid().toString(), 0, 8);
        }
        return out.toString();
    }
}
