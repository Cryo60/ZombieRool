package local.hotfixtest;
import java.util.*;
import me.cryo.zombierool.block.system.TraversalSystem;
import me.cryo.zombierool.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
@Mod("hotfixtests") public final class NavigationBenchmark {
 public NavigationBenchmark(){MinecraftForge.EVENT_BUS.addListener(this::started);}
 private void started(ServerStartedEvent event){try{
  var level=event.getServer().overworld();
  for(int cx=30;cx<=38;cx++)for(int cz=0;cz<=2;cz++)level.getChunk(cx,cz);
  for(int x=500;x<=600;x++)for(int z=0;z<=30;z++){
   level.setBlock(new BlockPos(x,-60,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),3);
   level.setBlock(new BlockPos(x,-50,z),Blocks.STONE.defaultBlockState(),3);
   for(int y=-49;y<=-46;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),3);
  }
  for(int i=0;i<12;i++){
   var p=new BlockPos(505+i*7,-49,10);var q=p.offset(6,0,8);var block=TraversalSystem.ZIPLINE.get();
   level.setBlock(p,block.defaultBlockState(),3);level.setBlock(q,block.defaultBlockState().setValue(TraversalSystem.TraversalBlock.PHASE,2),3);
   var a=(TraversalSystem.Anchor)level.getBlockEntity(p);a.exit=q;a.bidirectional=true;a.onLoad();((TraversalSystem.Anchor)level.getBlockEntity(q)).root=p;
  }
  for(int i=0;i<4;i++){
   var p=new BlockPos(510+i*20,-15,25);level.setBlock(p,TraversalSystem.WALL.get().defaultBlockState(),3);
   for(int y=-16;y>=-49;y--)level.setBlock(new BlockPos(p.getX()+1,y,25),Blocks.STONE.defaultBlockState(),3);
   ((TraversalSystem.Anchor)level.getBlockEntity(p)).onLoad();
  }
  var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setPos(501.5,-49,15.5);level.addFreshEntity(zombie);zombie.setEmergenceTicks(0);zombie.setOnGround(true);
  for(int i=0;i<60;i++)zombie.getNavigation().createPath(new BlockPos(590,-49,12+i%8),0);
  long[] batches=new long[5];int reachable=0,nodes=0;
  for(int batch=0;batch<5;batch++){
   long start=System.nanoTime();
   for(int i=0;i<40;i++){
    zombie.setPos(501.5+i%5,-49,10.5+i%13);
    var path=zombie.getNavigation().createPath(new BlockPos(590+i%3,-49,10+i%13),0);
    if(path!=null){nodes+=path.getNodeCount();if(path.canReach())reachable++;}
   }
   batches[batch]=System.nanoTime()-start;
  }
  System.out.println("NAV BENCH routes="+TraversalSystem.loaded(level).size()+" searches=200 reachable="+reachable+" pathNodes="+nodes+" batchesMs="+Arrays.toString(Arrays.stream(batches).map(n->n/1000000).toArray()));
  if(reachable!=200)throw new AssertionError("Benchmark paths must remain reachable");
  System.out.println("NAV BENCH PASS");
 }catch(Throwable t){t.printStackTrace();}finally{event.getServer().halt(false);}}
}
