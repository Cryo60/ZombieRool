package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={net.minecraft.world.entity.projectile.AbstractArrow.class},remap=false)
public abstract class LunarArrowGravityMixin {
 @ModifyConstant(method={"tick","m_8119_"},constant=@Constant(doubleValue=0.05000000074505806),remap=false)
 private double zr$gravity(double value){return value*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt((net.minecraft.world.entity.Entity)(Object)this);}
}
