package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={com.tacz.guns.client.gameplay.LocalPlayerShoot.class},remap=false)
public abstract class TacZLunarClientShootMixin {
 @Inject(method="shoot",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$shoot(CallbackInfoReturnable<com.tacz.guns.api.entity.ShootResult> ci){if(me.cryo.zombierool.client.LunarActionInput.active()||me.cryo.zombierool.client.TacZAutoReload.blocksHeldShot())ci.setReturnValue(com.tacz.guns.api.entity.ShootResult.COOL_DOWN);}
 @Inject(method="chargeShoot",at=@At("HEAD"),cancellable=true,remap=false)
 private void zr$charge(boolean charge,CallbackInfoReturnable<Boolean> ci){if(me.cryo.zombierool.client.LunarActionInput.active())ci.setReturnValue(false);}
}
