package local.hotfixtest;
import java.util.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.block.Blocks;

/** 5 topologies x 2 directions x 5 player behaviours x machine present/absent = 100 real AI pursuits. */
public final class BossPursuitMatrixTest {
 public static void run(ServerLevel level,ServerPlayer player)throws Exception {
  var tickField=net.minecraft.server.MinecraftServer.class.getDeclaredField("tickCount");tickField.setAccessible(true);
  var motion=TraversalMotion.class.getDeclaredMethod("tick",net.minecraft.world.level.Level.class);motion.setAccessible(true);
  int oldTick=tickField.getInt(level.getServer());long oldTime=level.getGameTime();var oldPos=player.position();float oldHealth=player.getHealth();
  var managerField=ServerLevel.class.getDeclaredField("entityManager");managerField.setAccessible(true);var manager=(net.minecraft.world.level.entity.PersistentEntitySectionManager<net.minecraft.world.entity.Entity>)managerField.get(level);
  var results=new ArrayList<String>();int scenario=0,failures=0;
  try {
   for(int cx=102;cx<=105;cx++){level.getChunk(cx,2);manager.updateChunkStatus(new net.minecraft.world.level.ChunkPos(cx,2),FullChunkStatus.ENTITY_TICKING);}
   for(int layout=0;layout<5;layout++) {
    for(int x=1640;x<=1680;x++)for(int z=32;z<=36;z++){
     boolean gap=layout>0&&x>1648&&x<1657||layout>=3&&x>1662&&x<1671;
     for(int y=78;y<=94;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
     int floor=height(layout,x);if(!gap){level.setBlock(new BlockPos(x,floor-2,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);level.setBlock(new BlockPos(x,floor-1,z),Blocks.STONE.defaultBlockState(),3);}
    }
    var roots=new ArrayList<BlockPos>();var ends=new ArrayList<BlockPos>();
    if(layout>0)link(level,1648,1657,height(layout,1648),height(layout,1657),layout==2||layout==4,roots,ends);
    if(layout>=3)link(level,1662,1671,height(layout,1662),height(layout,1671),layout==3,roots,ends);
    for(int direction:new int[]{1,-1})for(int behaviour=0;behaviour<5;behaviour++)for(boolean machine:new boolean[]{false,true}) {
     scenario++;BrutusMachineLocks.reset(level);
     double from=direction==1?1642.5:1677.5,to=direction==1?1677.5:1642.5;
     int fromY=height(layout,(int)from),toY=height(layout,(int)to);
     boolean box=(layout+behaviour+(direction==1?0:1))%2==0;
     var perk=new BlockPos(direction==1?1673:1646,toY+(box?0:1),35);
     if(machine){
      if(box){var state=MysteryBoxSystem.MYSTERY_BOX.get().defaultBlockState().setValue(MysteryBoxSystem.MysteryBoxBlock.ACTIVE,true);level.setBlock(perk,state,3);level.setBlock(perk.west(),state.setValue(MysteryBoxSystem.MysteryBoxBlock.PART,true),3);}
      else{level.setBlock(perk,PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.POWERED,true),3);level.setBlock(perk.below(),PerksSystem.DUMMY_BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaDummyBlock.PART,PerksSystem.DummyPart.LOWER),3);level.setBlock(perk.above(),PerksSystem.DUMMY_BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaDummyBlock.PART,PerksSystem.DummyPart.UPPER),3);((PerksSystem.PerksAColaBlockEntity)level.getBlockEntity(perk)).setPowered(true);}
     }
     player.setPos(to,toY,34.5);
     var boss=ZombieroolModEntities.BRUTUS.get().create(level);boss.moveTo(from,fromY,34.5,0,0);boss.setOnGround(true);boss.getPersistentData().putBoolean("zr_special_spawner",true);level.addFreshEntity(boss);
     var flights=new ArrayList<Integer>();boolean flying=false,turned=false,reached=false;int reachedAt=-1,awaySteps=0;double previousX=from;
     try {
      for(int t=0;t<1000;t++) {
       tickField.setInt(level.getServer(),oldTick+t);level.getServer().getWorldData().overworldData().setGameTime(oldTime+scenario*1100L+t);boss.tickCount=t;player.setHealth(player.getMaxHealth());
       if(behaviour==1)player.setPos(to,toY,34.5+(t%60<30?-.65:.65));
       if(behaviour==2)player.setPos(to+direction*(t>100?1:0),toY,34.5);
       if(behaviour==3&&!turned&&boss.distanceToSqr(player)<25&&!TraversalMotion.active(boss)){player.setPos(boss.getX()+direction*1.8,boss.getY(),34.5);turned=true;}
       if(behaviour==4&&!turned&&boss.distanceToSqr(player)<9&&!TraversalMotion.active(boss)){player.setPos(from,fromY,34.5);turned=true;}
       boss.tick();boolean active=TraversalMotion.active(boss);if(active&&!flying)flights.add(TraversalMotion.flight(boss).style);
       motion.invoke(null,level);flying=TraversalMotion.active(boss);
       if(!active&&!flying&&boss.distanceToSqr(player)>9&&(boss.getX()-previousX)*Math.signum(player.getX()-previousX)<-.08)awaySteps++;
       previousX=boss.getX();
       if(!flying&&boss.distanceToSqr(player)<9&&(behaviour!=4||turned)){reached=true;if(reachedAt<0)reachedAt=t;}
       if(reachedAt>=0&&t-reachedAt>=80)break;
      }
      var expected=new ArrayList<Integer>();if(layout>0)expected.add(layout==2||layout==4?1:0);if(layout>=3)expected.add(layout==3?1:0);if(direction<0)Collections.reverse(expected);if(behaviour==4){var back=new ArrayList<>(expected);Collections.reverse(back);expected.addAll(back);}
      boolean locked=BrutusMachineLocks.locked(level,perk);
      boolean ok=reached&&flights.equals(expected)&&awaySteps<=3&&(!machine||locked);
      String result="BOSS MATRIX "+(ok?"PASS":"FAIL")+" #"+scenario+" layout="+layout+" direction="+direction+" behaviour="+behaviour+" machine="+(machine?(box?"box":"perk"):"none")+" reached="+reached+" flights="+flights+" expected="+expected+" locked="+locked+" awaySteps="+awaySteps+" x="+boss.getX();
      results.add(result);System.out.println(result);if(!ok)failures++;
     }finally{TraversalMotion.cancel(boss);boss.discard();level.setBlock(perk,Blocks.AIR.defaultBlockState(),3);if(machine){if(box)level.setBlock(perk.west(),Blocks.AIR.defaultBlockState(),3);else{level.setBlock(perk.below(),Blocks.AIR.defaultBlockState(),3);level.setBlock(perk.above(),Blocks.AIR.defaultBlockState(),3);}}}
    }
    for(var root:roots)level.destroyBlock(root,false);for(var end:ends)level.setBlock(end,Blocks.AIR.defaultBlockState(),3);
   }
   java.nio.file.Files.write(java.nio.file.Path.of("D:/Dev/ZombieRool/build/dev-26.7/boss-pursuit-100-scenarios.txt"),results);
   if(scenario!=100||failures>0)throw new AssertionError("Boss pursuit matrix: "+failures+" failures / "+scenario+" scenarios");
   System.out.println("BOSS PURSUIT MATRIX PASS: 100 scenarios");
  }finally{BrutusMachineLocks.reset(level);player.setPos(oldPos);player.setHealth(oldHealth);tickField.setInt(level.getServer(),oldTick);level.getServer().getWorldData().overworldData().setGameTime(oldTime);}
 }
 private static int height(int layout,int x){return layout==0?80:layout==1?(x<1657?80:84):layout==2?(x<1657?84:80):(x<1657?80:x<1671?84:88);}
 private static void link(ServerLevel level,int from,int to,int fromY,int toY,boolean zip,List<BlockPos> roots,List<BlockPos> ends){
  var start=new BlockPos(from,fromY,34);var end=new BlockPos(to,toY,34);var block=zip?TraversalSystem.ZIPLINE.get():TraversalSystem.JUMP.get();
  level.setBlock(start,block.defaultBlockState(),3);level.setBlock(end,block.defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
  var anchor=(TraversalSystem.Anchor)level.getBlockEntity(start);anchor.root=start;anchor.exit=end;anchor.duration=zip?24:16;anchor.clearance=3;anchor.bidirectional=true;((TraversalSystem.Anchor)level.getBlockEntity(end)).root=start;anchor.sync();roots.add(start);ends.add(end);
 }
}
