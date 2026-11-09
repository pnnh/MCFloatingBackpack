package pnnh.mcfloatingbackpack.compat.accessories;

import com.mojang.blaze3d.vertex.PoseStack;

import io.wispforest.accessories.api.client.AccessoryRenderer;
import io.wispforest.accessories.api.client.DefaultAccessoryRenderer;
import io.wispforest.accessories.api.slot.SlotReference;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import pnnh.mcfloatingbackpack.compat.CompatConfig;

/**
 * 034: what Accessories draws for a floating backpack worn in its slots: nothing, because the backpack is drawn on the back by our own
 * layer (only the first one worn). Without a renderer of its own Accessories would draw its default look on top of ours.
 *
 * <p>It is deliberately <em>not</em> Accessories' own {@code EmptyRenderer}: a client option ({@code forceNullRenderReplacement}) swaps
 * that one for the default renderer. This one reads {@code modLevels} every time, so a changed setting takes effect at once: when
 * {@code accessories} is not at FULL it does what Accessories would do for any item (the default look), as our code is then meant to stay
 * out of it.
 */
@OnlyIn(Dist.CLIENT)
final class BackpackAccessoryRenderer implements AccessoryRenderer {
    private static boolean ours() {
        return CompatConfig.policy().specificAdaptation("accessories");
    }

    @Override
    public <M extends LivingEntity> void render(
            ItemStack stack,
            SlotReference reference,
            PoseStack matrices,
            EntityModel<M> model,
            MultiBufferSource multiBufferSource,
            int light,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        if (ours()) {
            return;
        }
        DefaultAccessoryRenderer.INSTANCE.render(
                stack, reference, matrices, model, multiBufferSource, light, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }

    @Override
    public boolean shouldRender(boolean isRendering) {
        return !ours() && isRendering;
    }

    @Override
    public boolean shouldRenderInFirstPerson(HumanoidArm arm, ItemStack stack, SlotReference reference) {
        return !ours() && DefaultAccessoryRenderer.INSTANCE.shouldRenderInFirstPerson(arm, stack, reference);
    }
}
