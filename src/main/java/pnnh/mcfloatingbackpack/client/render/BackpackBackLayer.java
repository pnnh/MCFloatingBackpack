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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import pnnh.mcfloatingbackpack.inventory.worn.WornBackpacks;
import pnnh.mcfloatingbackpack.item.FloatingBackpackItem;

/**
 * Backpack worn on a player's back (032: the first one {@link WornBackpacks#first} finds: the chest slot now, an accessories mod's back slot later).
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
        // 032: the first backpack the player wears (the chest slot, and later an accessories mod's back slot): the same rule that picks
        // the one the panel key opens. Only one is ever drawn, and none when the player has switched drawing off for its slot.
        WornBackpacks.Found worn = WornBackpacks.first(player);
        if (worn == null || worn.hidden() || !(worn.stack().getItem() instanceof FloatingBackpackItem)) {
            return;
        }
        ItemStack chest = worn.stack();
        poseStack.pushPose();
        getParentModel().body.translateAndRotate(poseStack);
        // Body local +Z points out of the chest. 180° X keeps the mesh upright and puts the
        // model's own front (whichever local face its own "fixed" display entry turns toward the
        // camera) toward a camera behind the player, matching the GUI icon and held-in-hand
        // orientation. After the 180° X flip, a more negative Z moves the pack AWAY from the body
        // (it maps to +Z of the body bone = out of the back). The strapless OBJ-based leather
        // model has nothing visually holding it on, so its near face is placed flush with the
        // torso's back surface (body half-depth 0.125). Net scale S = FIXED display scale (1.1)
        // times this layer's scale (0.68) = 0.748; the pack spans z' in [-0.281*S, 0.262*S], so
        // z = -(0.12 + 0.262*S) = -0.316 puts the near face at ~0.12. Re-derive z whenever the
        // scale changes. Y raised slightly (-0.45 -> -0.42) so the taller pack still sits
        // within the torso's height.
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0F));
        poseStack.translate(0.0F, -0.42F, -0.316F);
        poseStack.scale(0.68F, 0.68F, 0.68F);
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
