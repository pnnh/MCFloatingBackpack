package pnnh.mcfloatingbackpack.network;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import pnnh.mcfloatingbackpack.MCFloatingBackpack;
import pnnh.mcfloatingbackpack.tool.MobileMenu;
import pnnh.mcfloatingbackpack.tool.MobileTool;
import pnnh.mcfloatingbackpack.tool.MobileTools;
import pnnh.mcfloatingbackpack.tool.ToolOpenPlanner;

/**
 * 054: the client says which mobile tool the player clicked (the mobile crafting table now, the mobile furnace in 055) and asks for its vanilla
 * menu. One byte, nothing else: no item, no slot, no backpack. The server believes none of it: it reads the byte as a tool (an unknown one is
 * ignored), looks for itself whether the backpack the B key opens has that tool in its right area and which of the tools' menus the player has
 * open, and then {@link ToolOpenPlanner} says to open it, to close it (clicked again) or to do nothing.
 */
public record OpenToolPayload(byte tool) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenToolPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(MCFloatingBackpack.MOD_ID, "open_tool"));

    public static final StreamCodec<ByteBuf, OpenToolPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeByte(payload.tool()), buf -> new OpenToolPayload(buf.readByte()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(OpenToolPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            MobileTool tool = MobileTool.fromByte(payload.tool());
            boolean inRightArea = tool != null && MobileTools.has(player, tool);
            MobileTool open = player.containerMenu instanceof MobileMenu menu ? menu.tool() : null;
            switch (ToolOpenPlanner.decide(tool, inRightArea, open)) {
                case OPEN -> MobileTools.open(player, tool);
                case CLOSE -> player.closeContainer();
                case IGNORE -> {
                }
            }
        });
    }
}
