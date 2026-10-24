package me.cryo.zombierool.mixins;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.gameplay.TraversalMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class TraversalViewMixin {
    @Inject(method="renderItemInHand",at=@At("HEAD"),cancellable=true)
    private void zr$unlockHands(PoseStack pose,net.minecraft.client.Camera camera,float partialTick,CallbackInfo ci){
        if(me.cryo.zombierool.client.BrutusUnlockClient.hideHeldItems())ci.cancel();
    }
    @Inject(method="bobView",at=@At("HEAD"),cancellable=true)
    private void zr$stableRide(PoseStack pose,float partialTick,CallbackInfo ci){
        var player=Minecraft.getInstance().player;
        if(player!=null&&TraversalMotion.active(player))ci.cancel();
    }
}
