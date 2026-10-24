package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={net.minecraft.world.entity.vehicle.Boat.class},remap=false)
public abstract class LunarBoatGravityMixin {
 @ModifyConstant(method={"floatBoat","m_38395_"},constant=@Constant(doubleValue=-0.03999999910593033),remap=false)
 private double zr$gravity(double value){return value*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt((net.minecraft.world.entity.Entity)(Object)this);}
}
