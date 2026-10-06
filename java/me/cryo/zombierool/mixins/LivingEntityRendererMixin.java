package me.cryo.zombierool.mixins;

import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A corpse keeps deathTime above zero so it stays on the ground.
 * That value also forces the red hurt tint, which we skip here.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
    private static void zombierool_noDeathTint(LivingEntity entity, float partial, CallbackInfoReturnable<Integer> cir) {
        if (entity instanceof AbstractZombieRoolEntity && entity.deathTime > 0) {
            cir.setReturnValue(entity.hurtTime > 0
                    ? OverlayTexture.pack(OverlayTexture.u(0.0F), OverlayTexture.v(true))
                    : OverlayTexture.NO_OVERLAY);
        }
    }
}
