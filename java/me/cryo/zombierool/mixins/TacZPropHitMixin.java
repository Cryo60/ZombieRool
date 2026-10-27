package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(targets="com.tacz.guns.entity.EntityKineticBullet",remap=false)
public abstract class TacZPropHitMixin {
 @Inject(method="onBulletTick",at=@At("HEAD"),require=1,remap=false)
 private void zr$props(CallbackInfo ci){me.cryo.zombierool.gameplay.ScriptedPropHits.projectile((net.minecraft.world.entity.projectile.Projectile)(Object)this);}
}
