package me.cryo.zombierool.mixins;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(LivingEntity.class)
public abstract class LunarKnockbackMixin {
 @Unique private Vec3 zr$beforeKnockback;
 @Inject(method={"knockback","m_147240_"},at=@At("HEAD"),remap=false)
 private void zr$before(double strength,double x,double z,CallbackInfo ci){zr$beforeKnockback=((LivingEntity)(Object)this).getDeltaMovement();}
 @Inject(method={"knockback","m_147240_"},at=@At("RETURN"),remap=false)
 private void zr$after(double strength,double x,double z,CallbackInfo ci){
  var e=(LivingEntity)(Object)this;if(!(e instanceof Player)||zr$beforeKnockback==null)return;
  double g=me.cryo.zombierool.gameplay.LunarPhysics.gravityAt(e);if(g>=1)return;
  var now=e.getDeltaMovement();var delta=now.subtract(zr$beforeKnockback);double scale=Math.sqrt(g);
  e.setDeltaMovement(zr$beforeKnockback.add(delta.x*scale,delta.y*scale,delta.z*scale));
 }
}
