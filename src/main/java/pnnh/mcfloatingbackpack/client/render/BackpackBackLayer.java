package pnnh.mcfloatingbackpack.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Backpack worn on a player's back when equipped in the chest slot.
 *
 * <p>Renders the item's own baked model (the same one the GUI icon and held-in-hand views use)
 * via {@link ItemRenderer#renderStatic}, instead of approximating its shape with hand-drawn boxes
 * (the previous approach here): the real model's "fixed" display entry already has an identity
 * rotation (see generate_leather_prototype.py) so its front/buckle side faces the same way as
 * every other view, and {@code ItemRenderer.render()} already recenters any baked model by
 * {@code -0.5} on each axis, so it lines up with this layer's existing position/scale constants
 * without any extra bookkeeping. This keeps the back view in sync with whatever the model
 * actually is -- stepped-taper body, straps, flap, buckle, and eventually the detachable pouches
 * -- with no geometry duplicated here to go stale.
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
        if (!(chest.getItem() instanceof FloatingBackpackItem)) {
            return;
        }
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // Body local +Z points out of the chest. 180° X keeps the mesh upright and puts the
        // model's own front (the strap/buckle side, "north" -- see the "fixed" display entry)
        // toward a camera behind the player, matching the GUI icon and held-in-hand orientation.
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.translate(0.0F, -0.45F, -0.32F);
        poseStack.scale(0.55F, 0.55F, 0.55F);
        Minecraft.getInstance()
                .getItemRenderer()
                .renderStatic(
                        chest,
                        ItemDisplayContext.FIXED,
                        packedLight,
                        OverlayTexture.NO_OVERLAY,
                        poseStack,
                        buffer,
                        player.level(),
                        player.getId());
        poseStack.popPose();
    }
}
