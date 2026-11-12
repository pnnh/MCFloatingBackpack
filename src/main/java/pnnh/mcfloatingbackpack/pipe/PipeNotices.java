package pnnh.mcfloatingbackpack.pipe;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import pnnh.mcfloatingbackpack.pipe.PipePlanner.Notice;

/** 049: what a pipe says to the player who used it: a line in their own chat, led by the mod's name, the same line at most once a second. */
public final class PipeNotices {
    private PipeNotices() {
    }

    /** The language key of a notice. */
    public static String key(Notice notice) {
        return "message.mcfloatingbackpack.pipe." + switch (notice) {
            case NO_BACKPACK -> "no_backpack";
            case BACKPACK_PROTECTED -> "backpack_protected";
            case NO_TANK -> "no_tank";
            case OIL_LOCKED -> "oil_locked";
            case CANNOT_ABSORB_FULL -> "cannot_absorb";
            case CANNOT_PLACE_EMPTY -> "cannot_place";
            case TANK_FULL_NOW -> "full_now";
            case TANK_EMPTY_NOW -> "empty_now";
            case NO_FLUID -> "no_fluid";
            case NO_EMPTY_CONTAINER -> "no_container";
            case NO_BUCKET_FOR_FLUID -> "no_bucket";
            case NO_BLOCK_FORM -> "no_block_form";
        };
    }

    /**
     * Sends the notice unless the player had the same one a moment ago. {@code argument} is what the line names: the liquid (as its name in
     * the language) for most of them, the fluid's id for the locked oil.
     */
    public static void send(ServerPlayer player, Notice notice, Component argument) {
        if (notice == null) {
            return;
        }
        long now = player.getServer() == null ? player.level().getGameTime() : player.getServer().getTickCount();
        if (!PipeCooldowns.noticeMayGo(player.getUUID(), notice, now)) {
            return;
        }
        Component text = switch (notice) {
            case NO_BACKPACK, BACKPACK_PROTECTED -> Component.translatable(key(notice));
            default -> Component.translatable(key(notice), argument);
        };
        player.sendSystemMessage(Component.translatable("message.mcfloatingbackpack.prefix")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(" "))
                .append(text.copy().withStyle(ChatFormatting.YELLOW)));
    }
}
