package net.mcreator.zombierool.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ArrowRenderer.class)
public class ArrowRendererMixin {

    @Inject(
        method = "render",
        at = @At("HEAD"),
        cancellable = true
    )
    private void zombierool_dontRenderInvisibleArrow(
            AbstractArrow arrow,
            float yaw,
            float partialTicks,
            PoseStack stack,
            MultiBufferSource buffer,
            int packedLight,
            CallbackInfo ci
    ) {
        if (arrow.isInvisible()) {
            ci.cancel();
        }
    }
}
