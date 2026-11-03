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
 * (the previous approach here). Each tier's model defines its own "fixed" display entry so its
 * recognizable front faces the same way as its GUI icon and held-in-hand views (see each model's
 * JSON under models/item/); this class is model-agnostic and doesn't know or care whether a given
 * tier is the cuboid-element style or an imported mesh (loader: neoforge:obj). {@code
 * ItemRenderer.render()} already recenters any baked model by {@code -0.5} on each axis, so this
 * layer's translate/scale constants apply uniformly regardless of which kind of model a tier
 * uses. This keeps the back view in sync with whatever each tier's model actually is, with no
 * geometry duplicated here to go stale.
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
        // model's own front (whichever local face its own "fixed" display entry turns toward the
        // camera) toward a camera behind the player, matching the GUI icon and held-in-hand
        // orientation. Z nudged closer to the body (-0.32 -> -0.40) for the strapless OBJ-based
        // leather model specifically, so it sits snug against the back with no visible gap
        // instead of looking like it could slide off -- there's nothing modeled to visually
        // "hold" it on.
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.translate(0.0F, -0.45F, -0.40F);
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
