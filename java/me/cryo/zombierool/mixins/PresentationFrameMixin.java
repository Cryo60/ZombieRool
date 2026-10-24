package me.cryo.zombierool.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class PresentationFrameMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void zr$prepareAtmosphereDepth(float partial,long time,boolean tick,CallbackInfo ci){me.cryo.zombierool.client.MapAtmosphereClient.prepareFrame();}
    @Inject(method="renderItemInHand",at=@At("TAIL"))
    private void zr$keepHandDepth(com.mojang.blaze3d.vertex.PoseStack pose,net.minecraft.client.Camera camera,float partial,CallbackInfo ci){me.cryo.zombierool.client.MapAtmosphereClient.captureHand();}
}
