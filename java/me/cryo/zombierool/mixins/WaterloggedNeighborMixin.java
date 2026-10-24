package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class WaterloggedNeighborMixin {
 @Inject(method={"updateShape","m_60728_"},at=@At("HEAD"),remap=false)
 private void zr$flow(net.minecraft.core.Direction direction,BlockState neighbor,net.minecraft.world.level.LevelAccessor level,net.minecraft.core.BlockPos pos,net.minecraft.core.BlockPos other,CallbackInfoReturnable<BlockState> ci){
  var state=(BlockState)(Object)this;
  if(state.getBlock() instanceof me.cryo.zombierool.block.WaterloggedElement&&state.getValue(BlockStateProperties.WATERLOGGED))level.scheduleTick(pos,Fluids.WATER,Fluids.WATER.getTickDelay(level));
 }
}
