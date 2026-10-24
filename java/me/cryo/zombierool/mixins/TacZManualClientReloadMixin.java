package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=com.tacz.guns.client.gameplay.LocalPlayerReload.class,remap=false)
public final class TacZManualClientReloadMixin {
 @Inject(method="reload",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$waitForAction(CallbackInfo ci){if(me.cryo.zombierool.client.TacZPendingReload.defer())ci.cancel();}

}
