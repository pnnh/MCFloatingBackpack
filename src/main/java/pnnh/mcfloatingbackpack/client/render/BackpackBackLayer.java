package pnnh.mcfloatingbackpack.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Backpack body, flap, and straps on a player wearing one in the chest slot.
 */
public class BackpackBackLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    public BackpackBackLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof FloatingBackpackItem backpack)) {
            return;
        }
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityCutoutNoCull(backpack.texture()));
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // Body local +Z points out of the chest. 180° X keeps the mesh upright;
        // negative Z sits the pack on the back instead of the face.
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        // Strap side (lower Z than the body) faces a camera behind the player.
        // The flap stays toward the spine.
        float y = -0.45F;
        float z = -0.32F;
        drawPart(poseStack, consumer, packedLight, 0.0F, y, z, 0.55F, 0.62F, 0.28F);
        drawPart(poseStack, consumer, packedLight, 0.0F, y - 0.33F, z + 0.10F, 0.50F, 0.22F, 0.08F);
        drawPart(poseStack, consumer, packedLight, -0.22F, y - 0.20F, z - 0.12F, 0.10F, 0.55F, 0.10F);
        drawPart(poseStack, consumer, packedLight, 0.22F, y - 0.20F, z - 0.12F, 0.10F, 0.55F, 0.10F);
        poseStack.popPose();
    }

    private static void drawPart(
            PoseStack poseStack,
            VertexConsumer consumer,
            int light,
            float x,
            float y,
            float z,
            float scaleX,
            float scaleY,
            float scaleZ) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scaleX, scaleY, scaleZ);
        renderBox(poseStack, consumer, light);
        poseStack.popPose();
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, int light) {
        PoseStack.Pose pose = poseStack.last();
        float x0 = -0.5F;
        float x1 = 0.5F;
        float y0 = -0.5F;
        float y1 = 0.5F;
        float z0 = -0.5F;
        float z1 = 0.5F;
        // front
        quad(pose, consumer, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1);
        // back
        quad(pose, consumer, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1);
        // left
        quad(pose, consumer, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0);
        // right
        quad(pose, consumer, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0);
        // top
        quad(pose, consumer, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0);
        // bottom
        quad(pose, consumer, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0);
    }

    private static void quad(
            PoseStack.Pose pose,
            VertexConsumer consumer,
            int light,
            float x1,
            float y1,
            float z1,
            float x2,
            float y2,
            float z2,
            float x3,
            float y3,
            float z3,
            float x4,
            float y4,
            float z4,
            float nx,
            float ny,
            float nz) {
        consumer.addVertex(pose, x1, y1, z1)
                .setColor(255, 255, 255, 255)
                .setUv(0.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x2, y2, z2)
                .setColor(255, 255, 255, 255)
                .setUv(1.0F, 1.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x3, y3, z3)
                .setColor(255, 255, 255, 255)
                .setUv(1.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x4, y4, z4)
                .setColor(255, 255, 255, 255)
                .setUv(0.0F, 0.0F)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
