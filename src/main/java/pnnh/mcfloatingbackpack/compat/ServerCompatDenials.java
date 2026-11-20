package pnnh.mcfloatingbackpack.compat;

import java.util.UUID;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;

/**
 * 059: the question every server entry that reaches into another mod's menu asks first: "may the panel do this with this menu?" Answers
 * from {@link ServerCompatGate} for the menu's mod; when the answer is no, the player is told once for each screen they open (nothing in
 * the protocol tells their client, so the panel is still shown there and the chat line is how they learn it is the server's setting), and
 * the refusal is written to the log at DEBUG every time.
 */
public final class ServerCompatDenials {
    private static final DenialThrottle THROTTLE = new DenialThrottle();

    private ServerCompatDenials() {
    }

    /** @return false when the server's level for the menu's mod refuses {@code action} (nothing may be changed then) */
    public static boolean allows(ServerPlayer player, AbstractContainerMenu menu, ServerCompatGate.Action action) {
        String owner = MenuOwner.ownerOf(menu);
        if (ServerCompatGate.allows(action, owner)) {
            return true;
        }
        MCFloatingBackpack.LOGGER.debug("Server compat level OFF for {}: {} refused for {}", owner, action, player.getGameProfile().getName());
        if (menu != null && THROTTLE.shouldNotify(player.getUUID(), menu.containerId, owner)) {
            player.sendSystemMessage(Component.translatable("message.mcfloatingbackpack.prefix")
                    .withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(" "))
                    .append(Component.translatable("message.mcfloatingbackpack.compat.server_off", owner).withStyle(ChatFormatting.YELLOW)));
        }
        return false;
    }

    /** The player closed the screen or left: the next refusal is told again. */
    public static void forget(UUID player) {
        THROTTLE.forget(player);
    }
}
