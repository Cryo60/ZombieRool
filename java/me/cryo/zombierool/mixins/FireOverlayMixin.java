package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderDispatcher.class)
public class FireOverlayMixin {

    @Inject(method = "renderFlame", at = @At("HEAD"), cancellable = true)
    private void zombierool_renderBlueFlame(PoseStack pPoseStack, MultiBufferSource pBuffer, Entity pEntity, CallbackInfo ci) {
        // Si l'entité possède le tag BlueFire on annule le feu orange classique
        if (pEntity.getPersistentData().getBoolean("BlueFire")) {
            ci.cancel(); 
            EntityRenderDispatcher dispatcher = (EntityRenderDispatcher)(Object)this;
            // On dessine le feu bleu à la place en lui passant l'orientation de la caméra du Dispatcher
            me.cryo.zombierool.client.render.BlueFireRenderer.renderFlame(pPoseStack, pBuffer, pEntity, dispatcher.cameraOrientation());
        }
    }
}