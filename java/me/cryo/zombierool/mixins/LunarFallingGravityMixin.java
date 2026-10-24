package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(value={net.minecraft.world.entity.item.ItemEntity.class,net.minecraft.world.entity.item.PrimedTnt.class,net.minecraft.world.entity.item.FallingBlockEntity.class,net.minecraft.world.entity.vehicle.AbstractMinecart.class},remap=false)
public abstract class LunarFallingGravityMixin {
 @ModifyConstant(method={"tick","m_8119_"},constant=@Constant(doubleValue=-0.04),remap=false)
 private double zr$gravity(double value){return value*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt((net.minecraft.world.entity.Entity)(Object)this);}
}
