package me.cryo.zombierool.gameplay;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.*;
import me.cryo.zombierool.block.system.BuildableBlocks;
/** Display props are shootable without gaining movement collision. */
public final class ScriptedPropHits {
 private ScriptedPropHits(){}
 public static VoxelShape shape(BlockState state,BlockGetter level,BlockPos pos){
  if(state.is(BuildableBlocks.DECO.get())&&level.getBlockEntity(pos) instanceof BuildableBlocks.PartSupport part&&!part.visual.isEmpty())return Shapes.create(me.cryo.zombierool.api.BuildableVisuals.bounds(part.visual,state.getValue(BuildableBlocks.PartBlock.FACE)));
  return null;
 }
 public static void projectile(net.minecraft.world.entity.projectile.Projectile bullet){
  if(bullet.level().isClientSide||!(bullet.getOwner() instanceof net.minecraft.server.level.ServerPlayer player)||bullet.getDeltaMovement().lengthSqr()<.000001)return;
  var from=bullet.position();var to=from.add(bullet.getDeltaMovement());
  var hit=CompatibilityFixes.clip(bullet.level(),new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,bullet));
  if(hit.getType()==HitResult.Type.BLOCK&&bullet.level().getBlockState(hit.getBlockPos()).is(BuildableBlocks.DECO.get())){var p=hit.getBlockPos();me.cryo.zombierool.scripting.LuaScriptManager.callEvent("OnBlockShot",player.getUUID().toString(),p.getX(),p.getY(),p.getZ());}
 }
}
