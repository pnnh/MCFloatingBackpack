package pnnh.mcfloatingbackpack.inventory.creative;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.inventory.creative.CreativeCursorRules.Verdict;
import pnnh.mcfloatingbackpack.network.CreativeCursorPushPayload;

/**
 * 027: the server's side of the creative cursor. In the creative inventory screen the cursor exists only on the client, but
 * the panel's cursor operations (click, drag, gather, trash can, merge) all read and write the server's menu cursor. So while
 * that screen is open the server's {@code inventoryMenu} cursor <em>mirrors</em> the client's: the client reports it
 * ({@link #onReport}), the existing server code works on it as it would in a survival screen, and the result goes back
 * ({@link #pushIfChanged}).
 *
 * <p>Closing the screen needs no code of ours: the game's {@code AbstractContainerMenu.removed} puts whatever the menu cursor
 * holds back into the player's inventory (drops it if it does not fit), which is what keeps items taken out of the panel from
 * vanishing. (The creative inventory itself would discard the cursor; with the panel visible we do not, see research D4.)
 *
 * <p>Only the server's main thread calls this.
 */
public final class CreativeCursorBridge {
    private static final Map<UUID, CursorMirror<ItemStack>> MIRRORS = new HashMap<>();

    private CreativeCursorBridge() {
    }

    private static CursorMirror<ItemStack> mirrorOf(ServerPlayer player) {
        return MIRRORS.computeIfAbsent(
                player.getUUID(), id -> new CursorMirror<>(ItemStack::matches, ItemStack::copy, ItemStack.EMPTY));
    }

    /** The client says what is on its cursor. Anything but a legal report from a creative player changes nothing. */
    public static void onReport(ServerPlayer player, ItemStack stack) {
        Verdict verdict = CreativeCursorRules.check(
                player.isCreative(),
                player.containerMenu == player.inventoryMenu,
                stack.isEmpty(),
                stack.getCount(),
                stack.getMaxStackSize(),
                stack.isItemEnabled(player.level().enabledFeatures()));
        if (verdict != Verdict.ACCEPT) {
            MCFloatingBackpack.LOGGER.debug(
                    "Ignored a creative cursor report from {}: {}", player.getGameProfile().getName(), verdict);
            return;
        }
        player.inventoryMenu.setCarried(stack.copy());
        mirrorOf(player).received(stack);
    }

    /**
     * A panel operation may have changed the menu cursor; if the client does not have that value yet, send it. A no-op for
     * anyone but a creative player with their own inventory open, and when nothing changed.
     */
    public static void pushIfChanged(ServerPlayer player) {
        if (!player.isCreative()) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu != player.inventoryMenu) {
            return;
        }
        CursorMirror<ItemStack> mirror = mirrorOf(player);
        ItemStack carried = menu.getCarried();
        if (mirror.shouldSend(carried)) {
            PacketDistributor.sendToPlayer(player, new CreativeCursorPushPayload(carried.copy()));
            mirror.sent(carried);
        }
    }

    /** The player's inventory screen closed (the game has already returned the cursor to the inventory). */
    public static void onClose(ServerPlayer player) {
        CursorMirror<ItemStack> mirror = MIRRORS.get(player.getUUID());
        if (mirror != null) {
            mirror.reset();
        }
    }

    public static void onLogout(ServerPlayer player) {
        MIRRORS.remove(player.getUUID());
    }
}
