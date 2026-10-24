package local.skingalleryclient;
import me.cryo.zombierool.block.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import java.util.*;

public final class WaterMapFixesTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("WATER MAP CHECK: "+label);}
 private static me.cryo.zombierool.entity.ZombieEntity zombie(ServerLevel l,double x,double z,double speed){var m=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);m.setPos(x,65,z);l.addFreshEntity(m);m.setEmergenceTicks(0);m.getPersistentData().remove("zr_emerge_no_ai");m.setNoAi(false);m.setOnGround(true);m.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(speed);return m;}
 private static double walk(ServerLevel l,int z,double speed){var m=zombie(l,280.5,z+.5,speed);try{double start=0;for(int t=0;t<100;t++){m.baseTick();m.getMoveControl().setWantedPosition(m.getX()+30,65,z+.5,1);m.getMoveControl().tick();m.travel(new Vec3(m.xxa,m.yya,m.zza));if(t==19)start=m.getX();}return m.getX()-start;}finally{m.discard();}}
 private static void place(ServerPlayer p,net.minecraft.world.level.block.Block block,BlockPos pos){var stack=new ItemStack(block.asItem());p.setItemInHand(InteractionHand.MAIN_HAND,stack);var ctx=new BlockPlaceContext(p,InteractionHand.MAIN_HAND,stack,new BlockHitResult(Vec3.atCenterOf(pos.below()).add(0,.5,0),Direction.UP,pos.below(),false));var result=((BlockItem)stack.getItem()).place(ctx);check(result.consumesAction(),"Actual item placement succeeds: "+block);}
 public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();var old=p.position();p.setGameMode(GameType.CREATIVE);int optIn=0;
  for(var b:net.minecraftforge.registries.ForgeRegistries.BLOCKS)if(b instanceof WaterloggedElement){check(b.defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED),"Waterlogged state registered on "+net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b));check(!b.defaultBlockState().getValue(BlockStateProperties.WATERLOGGED),"Default state is dry on "+net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b));
   var oldNbt=net.minecraft.nbt.NbtUtils.writeBlockState(b.defaultBlockState());oldNbt.getCompound("Properties").remove("waterlogged");
   var loaded=net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),oldNbt);
   check(!loaded.getValue(BlockStateProperties.WATERLOGGED)&&loaded.getFluidState().isEmpty(),"Pre-waterlogging map palette loads dry on "+net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b));
   var wet=b.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED,true);
   var restored=net.minecraft.nbt.NbtUtils.readBlockState(net.minecraft.core.registries.BuiltInRegistries.BLOCK.asLookup(),net.minecraft.nbt.NbtUtils.writeBlockState(wet));
   check(restored.getValue(BlockStateProperties.WATERLOGGED),"Explicit underwater palette stays wet on "+net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(b));optIn++;}check(optIn>20,"Technical and gameplay block families opt into water support");check(!Blocks.STONE.defaultBlockState().hasProperty(BlockStateProperties.WATERLOGGED),"Vanilla full blocks are unchanged");
  var at=new BlockPos(220,65,0);for(int x=218;x<=243;x++)for(int z=-2;z<=2;z++)l.setBlock(new BlockPos(x,64,z),Blocks.STONE.defaultBlockState(),2);
  try{
   l.setBlock(at,Blocks.WATER.defaultBlockState(),2);l.setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);p.teleportTo(l,220.5,65,2,180,0);var door=me.cryo.zombierool.core.registry.ZRBlocks.DEFENSE_DOOR.get();place(p,door,at);
   check(l.getBlockState(at).getValue(BlockStateProperties.WATERLOGGED)&&l.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER),"Defense door preserves water at its lower half");check(!l.getBlockState(at.above()).getValue(BlockStateProperties.WATERLOGGED),"Dry upper door half does not copy lower water state");
   check(((WaterloggedElement)door).placeLiquid(l,at.above(),l.getBlockState(at.above()),Fluids.WATER.getSource(false)),"Upper door half accepts a water bucket");
   ((DefenseDoorSystem.DefenseDoorBlock)door).updateStage(l,at,3);check(l.getFluidState(at).is(net.minecraft.tags.FluidTags.WATER)&&l.getFluidState(at.above()).is(net.minecraft.tags.FluidTags.WATER),"Damage and repair stage updates retain water in both halves");
   var sp=at.east(5);l.setBlock(sp,Blocks.WATER.defaultBlockState(),2);var block=UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get();place(p,block,sp);var be=(UniversalSpawnerSystem.UniversalSpawnerBlockEntity)l.getBlockEntity(sp);be.setConfig(UniversalSpawnerSystem.SpawnerMobType.ZOMBIE,"","1","0",false,1);be.onLoad();
   for(int i=0;i<5;i++){l.getBlockState(sp).updateShape(Direction.UP,Blocks.WATER.defaultBlockState(),l,sp,sp.above());Fluids.WATER.tick(l,sp,l.getFluidState(sp));}
   check(l.getBlockState(sp).is(block)&&l.getFluidState(sp).is(net.minecraft.tags.FluidTags.WATER)&&l.getBlockEntity(sp)==be,"Spawner survives flowing water and neighbor updates without losing its block entity");
   var dryPath=at.east(10);var pathBlock=me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get();l.setBlock(dryPath,Blocks.AIR.defaultBlockState(),2);place(p,pathBlock,dryPath);check(l.getFluidState(dryPath).isEmpty(),"Path Block placed on dry land never creates water");
   for(int i=0;i<5;i++)l.getBlockState(dryPath).updateShape(Direction.UP,Blocks.AIR.defaultBlockState(),l,dryPath,dryPath.above());check(l.getFluidState(dryPath).isEmpty(),"Dry Path Block stays dry through neighbor updates");
   var path=at.east(8);l.setBlock(path,Blocks.WATER.defaultBlockState(),2);l.setBlock(path,me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),2);Fluids.WATER.tick(l,path,l.getFluidState(path));check(l.getBlockState(path).is(me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get())&&l.getFluidState(path).is(net.minecraft.tags.FluidTags.WATER),"Navigation marker survives water instead of being washed away");
   var pad=at.east(20);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)l.setBlock(pad.offset(dx,0,dz),Blocks.WATER.defaultBlockState(),2);p.teleportTo(l,pad.getX()+.5,65,2,180,0);place(p,TeleporterSystem.PLATFORM.get(),pad);for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)check(l.getFluidState(pad.offset(dx,0,dz)).is(net.minecraft.tags.FluidTags.WATER),"Each of the nine teleporter parts preserves its own water cell");
   var env=LunarEnvironment.get(l);var room=new AABB(262,65,-1,266,72,3);var moon=new AABB(258,64,-6,272,82,8);env.put("water-test-room",room,1,false);env.put("water-test-moon",moon,.167,true);check(!env.at(264.5,66.6,.5,new LunarEnvironment.Conditions(1,false)).vacuum(),"Small breathable room wins even when Moon volume registers afterwards");var reload=LunarEnvironment.load(env.describe());check(!reload.at(264.5,66.6,.5,new LunarEnvironment.Conditions(1,false)).vacuum(),"Zone priority survives save and reload");
   p.setGameMode(GameType.SURVIVAL);p.teleportTo(l,264.5,65,.5,0,0);p.baseTick();LunarEquipment.revoke(p);LunarEquipment.data(p).putInt("air",40);LunarEquipment.tick(new net.minecraftforge.event.TickEvent.PlayerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,p));check(!LunarEquipment.data(p).getBoolean("needsOxygen")&&LunarEquipment.data(p).getInt("air")==44,"A real player recovers air inside the overlapping breathable Moon room");env.remove("water-test-room");env.remove("water-test-moon");p.setGameMode(GameType.CREATIVE);
   for(int x=278;x<=380;x++)for(int z:new int[]{0,8})for(int dz=-1;dz<=1;dz++){l.setBlock(new BlockPos(x,64,z+dz),Blocks.STONE.defaultBlockState(),2);for(int y=65;y<=68;y++)l.setBlock(new BlockPos(x,y,z+dz),z==8?Blocks.WATER.defaultBlockState():Blocks.AIR.defaultBlockState(),2);}
   for(double speed:new double[]{.12,.23,.34}){double land=walk(l,0,speed),water=walk(l,8,speed),ratio=water/land;System.out.println("WATER SPEED speed="+speed+" land="+land+" water="+water+" ratio="+ratio);check(ratio>.75&&ratio<.95,"Underwater speed is only slightly slower than land speed at "+speed);}
  }finally{LunarEnvironment.get(l).remove("water-test-room");LunarEnvironment.get(l).remove("water-test-moon");p.setGameMode(GameType.CREATIVE);p.setPos(old);}
  System.out.println("WATER MAP FIXES PASS: "+checks+" checks");return checks;
 }
}
