package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
/** Select the pack's existing stripper-clip animation without touching ammunition or attachments. */
@Mixin(value=com.tacz.guns.client.animation.statemachine.GunAnimationStateContext.class,remap=false)
public final class TacZKarClipContextMixin {
 @Inject(method="hasBulletInBarrel",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$clipBarrel(CallbackInfoReturnable<Boolean> c){if(me.cryo.zombierool.integration.TacZManualReload.CLIP_ANIMATION.get())c.setReturnValue(false);}
 @Inject(method="getAmmoCount",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$clipAmmo(CallbackInfoReturnable<Integer> c){if(me.cryo.zombierool.integration.TacZManualReload.CLIP_ANIMATION.get())c.setReturnValue(0);}
 @Inject(method="getAttachment",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$clipAttachment(String type,CallbackInfoReturnable<String> c){if("SCOPE".equals(type)&&me.cryo.zombierool.integration.TacZManualReload.CLIP_ANIMATION.get())c.setReturnValue("tacz:empty");}
}
