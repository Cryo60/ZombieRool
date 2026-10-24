package me.cryo.zombierool.gameplay;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import net.minecraft.core.BlockPos;
public final class WeaponEffectTrace {
 private WeaponEffectTrace(){}
 public static boolean permeable(BlockState state){
  var b=state.getBlock();return b instanceof me.cryo.zombierool.block.system.DefenseDoorSystem.BaseDefenseDoor||b instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallBlock||b instanceof me.cryo.zombierool.block.system.DefenseWallSystem.DefenseWallDummyBlock;
 }
 public static BlockHitResult clip(Level level,Vec3 from,Vec3 to,Entity entity){
  return level.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,entity){
   @Override public VoxelShape getBlockShape(BlockState state,net.minecraft.world.level.BlockGetter world,BlockPos pos){return permeable(state)?Shapes.empty():super.getBlockShape(state,world,pos);}
  });
 }
}
