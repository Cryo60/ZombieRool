package me.cryo.zombierool.mixins;
import org.spongepowered.asm.mixin.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.*;
@Mixin(net.minecraft.world.level.block.SimpleWaterloggedBlock.class)
public interface CustomWaterContainerMixin {
 /** @author ZombieRool @reason Accept map water while retaining the vanilla water contract. */
 @Overwrite default boolean canPlaceLiquid(BlockGetter level,BlockPos pos,BlockState state,Fluid fluid){return !state.getValue(BlockStateProperties.WATERLOGGED)&&(fluid==Fluids.WATER||me.cryo.zombierool.maptexture.CustomWaterlogging.fluidSlot(fluid)>=0);}
 /** @author ZombieRool @reason Keep the custom fluid identity when filling a waterlogged block. */
 @Overwrite default boolean placeLiquid(LevelAccessor level,BlockPos pos,BlockState state,FluidState fluid){
  if(!canPlaceLiquid(level,pos,state,fluid.getType()))return false;
  if(!level.isClientSide()){int slot=me.cryo.zombierool.maptexture.CustomWaterlogging.fluidSlot(fluid.getType());if(slot>=0)me.cryo.zombierool.maptexture.CustomWaterlogging.put(level,pos,slot);level.setBlock(pos,state.setValue(BlockStateProperties.WATERLOGGED,true),3);level.scheduleTick(pos,fluid.getType(),fluid.getType().getTickDelay(level));}return true;
 }
 /** @author ZombieRool @reason Return the matching map-water bucket instead of a blue-water bucket. */
 @Overwrite default net.minecraft.world.item.ItemStack pickupBlock(LevelAccessor level,BlockPos pos,BlockState state){
  if(!state.getValue(BlockStateProperties.WATERLOGGED))return net.minecraft.world.item.ItemStack.EMPTY;
  int slot=me.cryo.zombierool.maptexture.CustomWaterlogging.slot(level,pos);level.setBlock(pos,state.setValue(BlockStateProperties.WATERLOGGED,false),3);me.cryo.zombierool.maptexture.CustomWaterlogging.put(level,pos,-1);if(!state.canSurvive(level,pos))level.destroyBlock(pos,true);
  return new net.minecraft.world.item.ItemStack(slot>=0?me.cryo.zombierool.maptexture.MapTextureWater.BUCKETS[slot].get():net.minecraft.world.item.Items.WATER_BUCKET);
 }
}
