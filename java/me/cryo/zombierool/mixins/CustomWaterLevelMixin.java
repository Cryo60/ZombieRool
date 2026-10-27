package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
@Mixin(Level.class)
public abstract class CustomWaterLevelMixin {
 @ModifyVariable(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("HEAD"),argsOnly=true)
 private BlockState zr$waterSlot(BlockState next,BlockPos pos,BlockState original,int flags,int recursion){return me.cryo.zombierool.maptexture.CustomWaterlogging.placement((Level)(Object)this,pos,next);}
 @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",at=@At("RETURN"))
 private void zr$changed(BlockPos pos,BlockState state,int flags,int recursion,CallbackInfoReturnable<Boolean> ci){if(ci.getReturnValueZ())me.cryo.zombierool.maptexture.CustomWaterlogging.changed((Level)(Object)this,pos);}
 @Inject(method="getFluidState",at=@At("HEAD"),cancellable=true)
 private void zr$fluid(BlockPos pos,CallbackInfoReturnable<FluidState> ci){int slot=me.cryo.zombierool.maptexture.CustomWaterlogging.slot((Level)(Object)this,pos);if(slot>=0)ci.setReturnValue(me.cryo.zombierool.maptexture.MapTextureWater.SOURCE[slot].get().getSource(false));}
}
