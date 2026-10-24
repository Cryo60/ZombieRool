package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={net.minecraft.world.entity.projectile.ThrowableProjectile.class,net.minecraft.world.entity.projectile.ThrownPotion.class,net.minecraft.world.entity.projectile.ThrownExperienceBottle.class},remap=false)
public abstract class LunarThrowableGravityMixin {
 @Inject(method={"getGravity","m_7139_"},at=@At("RETURN"),cancellable=true,remap=false)
 private void zr$gravity(CallbackInfoReturnable<Float> ci){ci.setReturnValue((float)(ci.getReturnValue()*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt((net.minecraft.world.entity.Entity)(Object)this)));}
}
