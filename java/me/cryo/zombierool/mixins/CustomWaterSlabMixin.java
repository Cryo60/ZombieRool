package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(net.minecraft.world.level.block.SlabBlock.class)
public abstract class CustomWaterSlabMixin {
 @Inject(method="canPlaceLiquid",at=@At("HEAD"),cancellable=true)
 private void zr$mapWater(net.minecraft.world.level.BlockGetter level,net.minecraft.core.BlockPos pos,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.material.Fluid fluid,CallbackInfoReturnable<Boolean> ci){if(me.cryo.zombierool.maptexture.CustomWaterlogging.fluidSlot(fluid)>=0)ci.setReturnValue(state.getValue(net.minecraft.world.level.block.SlabBlock.TYPE)!=net.minecraft.world.level.block.state.properties.SlabType.DOUBLE&&!state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED));}
}
