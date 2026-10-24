package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={com.tacz.guns.entity.EntityKineticBullet.class},remap=false)
public abstract class TacZLunarBulletGravityMixin {
 @Shadow(remap=false) private float gravity;
 @Unique private float zr$originalGravity;
 @Inject(method={"tick","m_8119_"},at=@At("HEAD"),remap=false)
 private void zr$before(CallbackInfo ci){zr$originalGravity=gravity;gravity*=me.cryo.zombierool.gameplay.LunarPhysics.gravityAt((net.minecraft.world.entity.Entity)(Object)this);}
 @Inject(method={"tick","m_8119_"},at=@At("RETURN"),remap=false)
 private void zr$after(CallbackInfo ci){gravity=zr$originalGravity;}
}
