package local.skingalleryclient;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.core.registry.ZRBlocks;
import me.cryo.zombierool.handlers.ServerInteractionHandler;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.*;

public final class BarricadeRegressionTest {
 private static int checks;
 private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("BARRICADE CHECK: "+label);}
 private static int stage(ServerLevel level,BlockPos p){return level.getBlockState(p).getValue(DefenseDoorSystem.DefenseDoorBlock.STAGE);}
 private static void tick(ServerLevel l,BlockPos p){DefenseDoorSystem.DefenseDoorBlockEntity.tick(l,p,l.getBlockState(p),(DefenseDoorSystem.DefenseDoorBlockEntity)l.getBlockEntity(p));}
 public static int run(ServerPlayer p)throws Exception{
  var l=p.serverLevel();var position=p.position();int originalTicks=p.tickCount;long time=l.getGameTime();var left=new BlockPos(140,65,0);var right=left.east();var door=(DefenseDoorSystem.DefenseDoorBlock)ZRBlocks.DEFENSE_DOOR.get();
  var repair=ServerInteractionHandler.class.getDeclaredMethod("handleRepairBarricade",ServerPlayer.class,ServerLevel.class,BlockPos.class);repair.setAccessible(true);
  try{
   for(var at:java.util.List.of(left,right)){l.setBlock(at.below(),Blocks.STONE.defaultBlockState(),2);var s=door.defaultBlockState().setValue(DoorBlock.FACING,Direction.NORTH).setValue(DoorBlock.HINGE,at.equals(left)?DoorHingeSide.LEFT:DoorHingeSide.RIGHT).setValue(DefenseDoorSystem.DefenseDoorBlock.STAGE,0);l.setBlock(at,s,2);l.setBlock(at.above(),s.setValue(DoorBlock.HALF,DoubleBlockHalf.UPPER),2);}
   p.setPos(140.5,65,1.5);p.tickCount=1000;ServerInteractionHandler.resetAll();repair.invoke(null,p,l,left);check(stage(l,left)==1,"First repair starts immediately");
   p.tickCount=1024;repair.invoke(null,p,l,left);check(stage(l,left)==1,"Early request cannot bypass repair cooldown");
   p.tickCount=1025;repair.invoke(null,p,l,left);check(stage(l,left)==2,"Early rejected request does not delay next repair by a full interval");
   repair.invoke(null,p,l,right.above());check(stage(l,left)==2,"Duplicate request across linked leaves cannot repair twice in one tick");
   p.tickCount=1050;repair.invoke(null,p,l,right.above());check(stage(l,left)==3&&stage(l,right)==3,"Upper half and opposite leaf repair at normal cadence");
   ServerInteractionHandler.resetAll();door.updateStage(l,left,5);p.tickCount=1100;repair.invoke(null,p,l,left);door.updateStage(l,left,4);p.tickCount=1101;repair.invoke(null,p,l,left);check(stage(l,left)==5,"Request on a full barricade never consumes a repair cooldown");
   ServerInteractionHandler.resetAll();door.updateStage(l,left,0);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get(),200));p.tickCount=1200;repair.invoke(null,p,l,left);p.tickCount=1212;repair.invoke(null,p,l,left);check(stage(l,left)==1,"Speed Cola still rejects repairs before its interval");p.tickCount=1213;repair.invoke(null,p,l,left);check(stage(l,left)==2,"Speed Cola repairs at 13 ticks without extra interval");p.removeEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_SPEED_COLA.get());
   check(DefenseDoorSystem.attackRoot(l,left).equals(DefenseDoorSystem.attackRoot(l,right)),"Double-door leaves share the same attack root");
   var zombie=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);zombie.setPos(141,65,.5);l.addFreshEntity(zombie);zombie.setEmergenceTicks(0);zombie.setNoAi(true);door.updateStage(l,left,5);
   try{
    for(int dt=0;dt<=120;dt++){l.getServer().getWorldData().overworldData().setGameTime(time+dt);tick(l,dt%2==0?left:right);tick(l,dt%2==0?right:left);int expected=5-dt/60;check(stage(l,left)==expected&&stage(l,right)==expected,"One stage per zombie hit on both leaves at tick "+dt);}
    check(stage(l,left.above())==3&&stage(l,right.above())==3,"All four door parts stay synchronized after attacks");
    var other=me.cryo.zombierool.init.ZombieroolModEntities.ZOMBIE.get().create(l);other.setPos(141,65,.5);l.addFreshEntity(other);other.setEmergenceTicks(0);other.setNoAi(true);
    try{door.updateStage(l,left,5);zombie.getPersistentData().remove("zombie_attack_time_"+DefenseDoorSystem.attackRoot(l,left).asLong());tick(l,left);tick(l,right);l.getServer().getWorldData().overworldData().setGameTime(time+180);tick(l,left);tick(l,right);check(stage(l,left)==3,"Two separate zombies may each land one legitimate hit");}finally{other.discard();}
   }finally{zombie.discard();}
  }finally{p.tickCount=originalTicks;l.getServer().getWorldData().overworldData().setGameTime(time);p.setPos(position);ServerInteractionHandler.resetAll();for(var at:java.util.List.of(left,right)){l.setBlock(at.above(),Blocks.AIR.defaultBlockState(),2);l.setBlock(at,Blocks.AIR.defaultBlockState(),2);}}
  System.out.println("BARRICADE REGRESSION PASS: "+checks+" checks");return checks;
 }
}
