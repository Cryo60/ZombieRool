package me.cryo.zombierool.mixins;

import com.tacz.guns.api.event.server.AmmoHitBlockEvent;
import com.tacz.guns.event.ammo.DestroyGlassBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DestroyGlassBlock.class)
public abstract class TacZGlassProtectionMixin {
    
    @Inject(method = "onAmmoHitBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void zombierool_protectGlass(AmmoHitBlockEvent event, CallbackInfo ci) {
        ci.cancel();
    }
}