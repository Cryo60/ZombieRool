package local.hotfixtest;

import me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal;
import me.cryo.zombierool.player.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

@Mod("hotfixtests")
public class HotfixRuntimeTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);System.out.println("HOTFIX CHECK: "+message);}
    public HotfixRuntimeTest(){MinecraftForge.EVENT_BUS.addListener(this::started);}
    @SuppressWarnings("unchecked") private void started(ServerStartedEvent event){
        var server=event.getServer();try{
            ServerLevel level=server.overworld();
            check(!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation("zombierool","teleporter")),"New teleporter registry is absent");
            check(!ForgeRegistries.BLOCKS.containsKey(new ResourceLocation("zombierool","traversal_node")),"New traversal registry is absent");
            var path=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","path"));
            for(int x=-3;x<=17;x++)for(int z=-4;z<=4;z++){
                level.setBlock(new BlockPos(x,78,z),path.defaultBlockState(),3);
                level.setBlock(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState(),3);
                for(int y=80;y<=88;y++)level.setBlock(new BlockPos(x,y,z),y==84?Blocks.STONE.defaultBlockState():Blocks.AIR.defaultBlockState(),3);
            }
            var a=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"HotfixA"));
            var b=new FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"HotfixB"));
            level.addNewPlayer(a);level.addNewPlayer(b);var listField=net.minecraft.server.players.PlayerList.class.getDeclaredField("players");listField.setAccessible(true);var online=(List<ServerPlayer>)listField.get(server.getPlayerList());online.add(a);online.add(b);
            var mapField=net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");mapField.setAccessible(true);var playerMap=(Map<UUID,ServerPlayer>)mapField.get(server.getPlayerList());playerMap.put(a.getUUID(),a);playerMap.put(b.getUUID(),b);
            a.setGameMode(GameType.SURVIVAL);b.setGameMode(GameType.SURVIVAL);a.setPos(2.5,85,.5);b.setPos(7.5,80,.5);
            var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setPos(2.5,80,.5);zombie.setOnGround(true);zombie.setNoAi(true);level.addFreshEntity(zombie);
            check(zombie.targetSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof ReachablePlayerTargetGoal),"Released zombie uses the reachable-target goal");
            var goal=new ReachablePlayerTargetGoal(zombie,net.minecraft.world.entity.player.Player.class,false,false);
            boolean selected=false;for(int i=0;i<100&&!selected;i++)selected=goal.canUse();check(selected,"A reachable player can be selected");goal.start();
            check(zombie.getTarget()==b,"Zombie prefers reachable lower player over nearer player through ceiling");
            check(Arrays.stream(net.minecraft.world.entity.Mob.class.getDeclaredMethods()).anyMatch(m->m.getName().endsWith("onAiUpdate")),"CustomMobMixin is actually applied to Mob");
            var ai = net.minecraft.world.entity.Mob.class.getDeclaredMethod("customServerAiStep");ai.setAccessible(true);
            zombie.setNoAi(false);
            for(int i=0;i<60;i++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);zombie.tickCount++;ai.invoke(zombie);if(zombie.getTarget()!=b)throw new AssertionError("Complete AI lost reachable player at tick "+i);}
            check(zombie.getTarget()==b,"Complete AI retains reachable lower player for 60 ticks");
            for(int x=7;x<=11;x++)level.setBlock(new BlockPos(x,84,0),Blocks.AIR.defaultBlockState(),3);
            for(int x=8;x<=12;x++)level.setBlock(new BlockPos(x,80+x-8,0),Blocks.STONE.defaultBlockState(),3);
            b.setGameMode(GameType.CREATIVE);zombie.setTarget(null);goal.stop();
            selected=false;for(int i=0;i<100&&!selected;i++)selected=goal.canUse();check(selected,"Upper player is reachable through a staircase");goal.start();check(zombie.getTarget()==a,"Zombie selects the upstairs player when stairs exist");
            var route=zombie.getNavigation().createPath(a,0);check(route!=null&&route.canReach(),"Actual staircase path reaches the upper floor");
            for(int i=0;i<40;i++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);zombie.tickCount++;ai.invoke(zombie);}
            check(zombie.getTarget()==a,"Complete AI retains upstairs target through the staircase");
            route=zombie.getNavigation().getPath();check(route!=null&&route.canReach()&&route.getEndNode().y>=84,"Active melee navigation follows stairs to the upper floor");
            // Remove the staircase after acquisition: AI must drop that target and choose downstairs.
            for(int x=8;x<=12;x++)level.setBlock(new BlockPos(x,80+x-8,0),Blocks.AIR.defaultBlockState(),3);
            for(int x=7;x<=12;x++)level.setBlock(new BlockPos(x,84,0),Blocks.STONE.defaultBlockState(),3);
            b.setGameMode(GameType.SURVIVAL);zombie.getNavigation().stop();
            for(int i=0;i<60;i++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);zombie.tickCount++;ai.invoke(zombie);}
            check(zombie.getTarget()==b,"Complete AI switches downstairs when the stair route becomes blocked");
            var sandbags=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("zombierool","sandbags"));
            for(int z=-1;z<=1;z++)for(int y=80;y<=81;y++)level.setBlock(new BlockPos(5,y,z),sandbags.defaultBlockState(),3);
            zombie.getNavigation().stop();var detour=zombie.getNavigation().createPath(b,0);
            check(detour!=null&&detour.canReach(),"A route around sandbags remains reachable");
            for(int n=0;n<detour.getNodeCount();n++)check(!level.getBlockState(detour.getNode(n).asBlockPos()).is(sandbags),"Route never enters the occupied sandbag volume");
            var downField=PlayerDownManager.class.getDeclaredField("playersDown");downField.setAccessible(true);var down=(Map<UUID,Long>)downField.get(null);
            for(var player:List.of(a,b)){down.put(player.getUUID(),level.getGameTime());PlayerCrawlManager.setCrawling(player.getUUID(),true);player.setPose(Pose.SWIMMING);player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,1200,3));player.addEffect(new MobEffectInstance(MobEffects.GLOWING,1200));}
            PlayerDownManager.resetAll();
            for(var player:List.of(a,b))check(!PlayerDownManager.isPlayerDown(player.getUUID())&&!PlayerCrawlManager.isCrawling(player.getUUID())&&player.getPose()==Pose.STANDING&&player.getEyeHeight()>1.5&&!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),"Match reset stands up each player and restores camera and movement");
            down.put(a.getUUID(),level.getGameTime());PlayerCrawlManager.setCrawling(a.getUUID(),true);a.setPose(Pose.SWIMMING);
            PlayerDownManager.onPlayerRespawn(new net.minecraftforge.event.entity.player.PlayerEvent.PlayerRespawnEvent(a,false));
            check(!PlayerDownManager.isPlayerDown(a.getUUID())&&!PlayerCrawlManager.isCrawling(a.getUUID())&&a.getPose()==Pose.STANDING,"Respawn clears stale down and crawl states");
            var tick=Arrays.stream(net.minecraft.world.entity.player.Player.class.getDeclaredMethods()).filter(m->m.getName().endsWith("zombierool_onPlayerTickMixin")).findFirst().orElseThrow();tick.setAccessible(true);
            a.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(6);a.setHealth(2);a.getPersistentData().putLong("zr_last_hurt_game_time",level.getGameTime()-101);tick.invoke(a,new CallbackInfo("tick",false));
            check(Math.abs(a.getHealth()-2.015f)<.0001,"Regeneration heals 0.015 per tick after the original delay");
            a.setHealth(2);a.getPersistentData().putLong("zr_last_hurt_game_time",level.getGameTime());tick.invoke(a,new CallbackInfo("tick",false));check(a.getHealth()==2,"Damage still delays regeneration");
            HotfixExtendedTests.run(server,level,a,b);
            MatchConfigRestartTest.run(level);
            TextureSpawnerRuntimeTest.run(level,a);
            System.out.println("HOTFIX RUNTIME PASS");
        }catch(Throwable failure){failure.printStackTrace();}finally{server.halt(false);}
    }
}
