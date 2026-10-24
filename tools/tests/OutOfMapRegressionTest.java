package local.hotfixtest;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.init.ZombieroolModBlocks;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal;
@Mod("hotfixtests") public final class OutOfMapRegressionTest {
 public OutOfMapRegressionTest(){MinecraftForge.EVENT_BUS.addListener(this::run);}
 private void check(boolean value,String text){if(!value)throw new AssertionError(text);System.out.println("OUTMAP CHECK: "+text);}
 private void run(ServerStartedEvent event){try{
  var server=event.getServer();var level=server.overworld();server.getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);
  me.cryo.zombierool.config.WorldConfig.get(level).getAllowedMobs().addAll(Set.of("zombierool:zombie","zombierool:crawler","zombierool:hellhound"));
  for(int x=512;x<=553;x++)for(int z=0;z<=10;z++){level.setBlock(new BlockPos(x,78,z),x<=540?ZombieroolModBlocks.PATH.get().defaultBlockState():Blocks.AIR.defaultBlockState(),2);level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),2);for(int y=80;y<=84;y++)level.setBlock(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState(),2);}
  var player=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"OutmapProbe"));level.addNewPlayer(player);player.setGameMode(GameType.SURVIVAL);player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100000);player.setHealth(100000);
  var list=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");list.setAccessible(true);((List<net.minecraft.server.level.ServerPlayer>)list.get(server.getPlayerList())).add(player);
  player.setPos(548.5,80,5.5);player.setOnGround(true);
  var mobs=new ArrayList<me.cryo.zombierool.entity.AbstractZombieRoolEntity>();
  for(int i=0;i<12;i++){var mob=i%3==0?ZombieroolModEntities.CRAWLER.get().create(level):i%3==1?ZombieroolModEntities.HELLHOUND.get().create(level):ZombieroolModEntities.ZOMBIE.get().create(level);mob.setPos(515.5+i%4,80,2.5+i/4*2);level.addFreshEntity(mob);mob.setEmergenceTicks(0);mob.setNoAi(false);mob.setOnGround(true);mobs.add(mob);}
  long start=System.nanoTime();
  for(var mob:mobs){var original=mob.position();for(int i=0;i<10;i++){player.setPos(547.5+i%5,80,5.5);check(mob.getNavigation().createPath(player,0)==null,"Outside marker columns rejected before path search");ReachablePlayerTargetGoal.invalidate(mob);ReachablePlayerTargetGoal.chooseAndSet(mob,player);}check(mob.getTarget()==null&&mob.position().equals(original)&&!mob.isRemoved(),"Unreachable survivor neither attracts nor relocates enemies");}
  long elapsed=(System.nanoTime()-start)/1000000;System.out.println("OUTMAP PERF: 120 moving-target requests, "+elapsed+" ms");check(elapsed<1000,"Outside target checks stay below one second for the whole batch");
  player.setGameMode(GameType.CREATIVE);player.noPhysics=true;for(var mob:mobs){ReachablePlayerTargetGoal.invalidate(mob);ReachablePlayerTargetGoal.chooseAndSet(mob,player);check(mob.getTarget()==null,"Noclip creative player does not attract enemies");}player.noPhysics=false;player.setGameMode(GameType.SURVIVAL);
  player.setPos(536.5,80,5.5);for(var mob:mobs){ReachablePlayerTargetGoal.invalidate(mob);ReachablePlayerTargetGoal.chooseAndSet(mob,player);check(mob.getTarget()==player&&RouteNavigation.reachesPlayer(mob.getNavigation().getPath(),player),"Reentry restores reachable pursuit");}
  var zombie=mobs.get(2);var initial=zombie.position();double biggest=0;for(int tick=0;tick<100;tick++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);var before=zombie.position();zombie.tick();biggest=Math.max(biggest,zombie.position().distanceTo(before));}check(zombie.position().distanceTo(initial)>2&&biggest<1,"Returning zombie walks without teleporting onto the player");
  player.setPos(541.15,80,5.5);player.setShiftKeyDown(true);check(NavigationArea.allowsPursuit(player),"Supported sneaking edge remains targetable");player.setShiftKeyDown(false);
  var point=new BlockPos(535,80,5);check(NavigationArea.allows(level,point),"Marker below admits the column");level.setBlock(point.below(),ZombieroolModBlocks.LIMIT.get().defaultBlockState(),3);check(!NavigationArea.allows(level,point),"Limit blocks invalidate the cached column immediately");
  for(var mob:mobs)mob.discard();player.discard();System.out.println("OUTMAP REGRESSION PASS");
 }catch(Throwable t){t.printStackTrace();}finally{event.getServer().halt(false);}}
}
