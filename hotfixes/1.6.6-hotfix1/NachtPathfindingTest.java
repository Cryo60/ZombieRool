package local.hotfixtest;
import java.util.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import me.cryo.zombierool.init.ZombieroolModEntities;
@Mod("hotfixtests") public class NachtPathfindingTest {
 public NachtPathfindingTest(){MinecraftForge.EVENT_BUS.addListener(this::started);}
 @SuppressWarnings("unchecked") private void started(ServerStartedEvent event){var server=event.getServer();try{
  var level=server.overworld();for(int x=-2;x<=3;x++)for(int z=-2;z<=3;z++)level.getChunk(x,z);
  if(Arrays.stream(Mob.class.getDeclaredMethods()).noneMatch(m->m.getName().endsWith("onAiUpdate")))throw new AssertionError("Missing CustomMobMixin");
  var player=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"NachtTest"));level.addNewPlayer(player);
  var field=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");field.setAccessible(true);((List<ServerPlayer>)field.get(server.getPlayerList())).add(player);
  player.setGameMode(GameType.SURVIVAL);player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(10000);player.setHealth(10000);
  // Reproduce the open southern staircase shown in the video, only in the test copy.
  for(int x=15;x<=16;x++)for(int y=-4;y<=-1;y++)level.setBlock(new net.minecraft.core.BlockPos(x,y,15),y==-4?net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("zombierool","path")).defaultBlockState():net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
  double[][] targets={{4.5,-10,2.5},{6.9,-10,6.5},{6.9,-10,9.8}};
  for(var target:targets)for(double x:new double[]{4.5,5.5,6.5}){
   player.setHealth(10000);player.setPos(target[0],target[1],target[2]);var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setPos(x,x<6?-5:-4,2.5);zombie.setOnGround(true);level.addFreshEntity(zombie);
   var path=zombie.getNavigation().createPath(player,0);
   System.out.println("NACHT INITIAL start="+zombie.position()+" target="+player.position()+" path="+path+" reachable="+(path!=null&&path.canReach())+" end="+(path==null?null:path.getEndNode()));
   if(path==null||!path.canReach())throw new AssertionError("No complete stair route from "+zombie.position());
   boolean stairs=false;for(int n=0;n<path.getNodeCount();n++){
    var node=path.getNode(n);String block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(node.asBlockPos()).getBlock()).toString();
    if(block.equals("zombierool:sandbags"))throw new AssertionError("Route enters occupied sandbag volume");
    if(node.x>=14&&node.z>=17&&node.y<=-5)stairs=true;
   }
   if(!stairs)throw new AssertionError("Route bypasses the staircase");
   var ai=Mob.class.getDeclaredMethod("customServerAiStep");ai.setAccessible(true);ai.invoke(zombie);
   System.out.println("NACHT FLAGS noAi="+zombie.isNoAi()+" alive="+zombie.isAlive()+" frozen="+zombie.getPersistentData()+" target="+zombie.getTarget()+" canAttack="+zombie.canAttack(player));
   for(int i=0;i<600&&!zombie.isRemoved();i++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);ai.invoke(zombie);zombie.tick();}
   System.out.println("NACHT RESULT pos="+zombie.position()+" target="+zombie.getTarget()+" remaining="+zombie.getNavigation().getPath()+" removed="+zombie.isRemoved());
   if(zombie.isRemoved()||Math.abs(zombie.getY()+10)>.1||zombie.distanceToSqr(player)>4)throw new AssertionError("Zombie did not arrive downstairs");
   zombie.discard();
  }
  System.out.println("NACHT MAP PASS: 9 starts/targets reach downstairs via staircase");
 }catch(Throwable e){e.printStackTrace();}finally{server.halt(false);}}
}
