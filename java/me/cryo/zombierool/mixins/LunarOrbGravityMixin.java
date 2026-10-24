package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={net.minecraft.world.entity.ExperienceOrb.class},remap=false)
public abstract class LunarOrbGravityMixin {
 @ModifyConstant(method={"tick","m_8119_"},constant=@Constant(doubleValue=-0.03),remap=false)
 private double zr$gravity(double value){return value*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt((net.minecraft.world.entity.Entity)(Object)this);}
}
