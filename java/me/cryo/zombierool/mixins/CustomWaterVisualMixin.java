package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=net.minecraftforge.fluids.FluidType.class,remap=false)
public abstract class CustomWaterVisualMixin {
 @Inject(method="getRenderPropertiesInternal",at=@At("RETURN"),cancellable=true,remap=false)
 private void zr$context(CallbackInfoReturnable<Object> ci){if((Object)this==net.minecraftforge.common.ForgeMod.WATER_TYPE.get())ci.setReturnValue(me.cryo.zombierool.client.CustomWaterVisuals.wrap(ci.getReturnValue()));}
}
