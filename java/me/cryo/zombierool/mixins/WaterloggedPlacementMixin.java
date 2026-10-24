package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
@Mixin(Level.class)
public abstract class WaterloggedPlacementMixin {
 @ModifyVariable(method={"setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z","m_6933_(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"},at=@At("HEAD"),argsOnly=true,remap=false)
 private BlockState zr$preserveWater(BlockState state,BlockPos pos,BlockState original,int flags,int recursion){
  var level=(Level)(Object)this;
  if(state.getBlock() instanceof me.cryo.zombierool.block.WaterloggedElement&&!level.getBlockState(pos).is(state.getBlock()))
   return state.setValue(BlockStateProperties.WATERLOGGED,level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER));
  return state;
 }
}
