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
 *
 * <p>This is a simplified 4-box approximation of the real item model (which has a stepped-taper
 * body and detailed per-face UVs) -- it predates the design/backpack-redesign/ model and isn't
 * meant to be pixel-perfect. Each box's 6 faces are textured with one representative UV rect
 * (not the real per-face mapping), picked from the actual item model's "body_bottom"/"flap"/
 * "strap_left" north faces at generation time, so a box reads as a coherent material swatch
 * instead of the entire multi-panel texture atlas squished onto every face. Every tier shares the
 * same base-variant model layout (same geometry, only colors differ -- see
 * design/backpack-redesign/generate_leather_prototype.py), so one fixed set of rects works for
 * all 5 tiers' textures. If that script's ELEMENTS list or packing order ever changes, regenerate
 * these from the new leather_floating_backpack.json and update the constants below.
 */
public class BackpackBackLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
    // uv = {u0, v0, u1, v1} in the standard 0-1 texture-fraction space, taken from the base-variant
    // model's body_bottom.north / flap.north / strap_left.north face UVs (each divided by 16).
    private static final float[] BODY_UV = {0.0F, 0.6323F, 0.3594F, 0.75F};
    private static final float[] FLAP_UV = {0.0F, 0.9559F, 0.2656F, 0.9927F};
    private static final float[] STRAP_UV = {0.0F, 0.0F, 0.0469F, 0.2647F};

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
        drawPart(poseStack, consumer, packedLight, 0.0F, y, z, 0.55F, 0.62F, 0.28F, BODY_UV);
        drawPart(poseStack, consumer, packedLight, 0.0F, y - 0.33F, z + 0.10F, 0.50F, 0.22F, 0.08F, FLAP_UV);
        // Straps centered on the body (not offset further down from it) and a bit taller than
        // the body box, so they run its full height with a slight overlap past the top/bottom
        // edges -- like shoulder straps -- instead of reading as two separate bars dangling
        // below a disconnected body box.
        drawPart(poseStack, consumer, packedLight, -0.22F, y, z - 0.12F, 0.10F, 0.70F, 0.10F, STRAP_UV);
        drawPart(poseStack, consumer, packedLight, 0.22F, y, z - 0.12F, 0.10F, 0.70F, 0.10F, STRAP_UV);
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
            float scaleZ,
            float[] uv) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(scaleX, scaleY, scaleZ);
        renderBox(poseStack, consumer, light, uv);
        poseStack.popPose();
    }

    private static void renderBox(PoseStack poseStack, VertexConsumer consumer, int light, float[] uv) {
        PoseStack.Pose pose = poseStack.last();
        float x0 = -0.5F;
        float x1 = 0.5F;
        float y0 = -0.5F;
        float y1 = 0.5F;
        float z0 = -0.5F;
        float z1 = 0.5F;
        // front
        quad(pose, consumer, light, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, 0, 1, uv);
        // back
        quad(pose, consumer, light, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, 0, -1, uv);
        // left
        quad(pose, consumer, light, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, -1, 0, 0, uv);
        // right
        quad(pose, consumer, light, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 1, 0, 0, uv);
        // top
        quad(pose, consumer, light, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, 1, 0, uv);
        // bottom
        quad(pose, consumer, light, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, uv);
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
            float nz,
            float[] uv) {
        float u0 = uv[0];
        float v0 = uv[1];
        float u1 = uv[2];
        float v1 = uv[3];
        consumer.addVertex(pose, x1, y1, z1)
                .setColor(255, 255, 255, 255)
                .setUv(u0, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x2, y2, z2)
                .setColor(255, 255, 255, 255)
                .setUv(u1, v1)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x3, y3, z3)
                .setColor(255, 255, 255, 255)
                .setUv(u1, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
        consumer.addVertex(pose, x4, y4, z4)
                .setColor(255, 255, 255, 255)
                .setUv(u0, v0)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
