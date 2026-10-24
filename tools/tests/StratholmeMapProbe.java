package local.strathprobe;

import com.mojang.authlib.GameProfile;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Runs against an isolated copy of the actual map, never the user's save. */
@Mod("strathprobe")
public final class StratholmeMapProbe {
    private static java.lang.reflect.Field playerField(String named,String srg)throws Exception{java.lang.reflect.Field f;try{f=net.minecraft.server.players.PlayerList.class.getDeclaredField(named);}catch(NoSuchFieldException e){f=net.minecraft.server.players.PlayerList.class.getDeclaredField(srg);}f.setAccessible(true);return f;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("STRATH CHECK: "+message);}
    private static long count(ServerLevel level,String prefix){return java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(e->e.isAlive()&&MapActors.owns(e)&&MapKnifeRules.profile((net.minecraft.world.entity.LivingEntity)e).getString("id").startsWith(prefix)).count();}
    public StratholmeMapProbe() { MinecraftForge.EVENT_BUS.addListener(this::start); }
    @SuppressWarnings("unchecked") private void start(ServerStartedEvent event) {
        var server=event.getServer();var level=server.overworld();
        try {
            var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.set(null,true);
            var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"StrathProbe"));
            level.addNewPlayer(player);player.setGameMode(GameType.SURVIVAL);
            var list=playerField("players","f_11196_");((List<ServerPlayer>)list.get(server.getPlayerList())).add(player);
            var ids=playerField("playersByUUID","f_11197_");((Map<UUID,ServerPlayer>)ids.get(server.getPlayerList())).put(player.getUUID(),player);
            LuaScriptManager.loadScripts(level);
            var api=new ZombieroolAPI(level);
            System.out.println("STRATH PROBE volumes="+api.getMusicVolumes()+" profiles="+api.getMapActorProfiles().length());
            for(double[] point:new double[][]{{28.5,100,.5},{-2.5,98,.5},{-100.5,96,118.5},{74.5,82,-46.5}}) {
                player.setPos(point[0],point[1],point[2]);player.setOnGround(true);
                for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)level.getChunk(player.chunkPosition().x+x,player.chunkPosition().z+z);
                System.out.println("STRATH PROBE position="+player.position()+" allowed="+NavigationArea.allowsPursuit(player)+" floor="+level.getBlockState(player.blockPosition().below()));
                for(String profile:List.of("scourge_ambush_zombie","scarlet_zombie")) {
                    int success=0;long started=System.nanoTime();
                    for(int i=0;i<8;i++) {
                        server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
                        String id=api.spawnMapActorNearPlayer(profile,player.getUUID().toString(),profile.equals("scarlet_zombie")?4:6,profile.equals("scarlet_zombie")?8:14);
                        if(!id.isEmpty()){success++;api.despawnMapActor(id);}
                    }
                    System.out.println("STRATH PROBE profile="+profile+" spawned="+success+" ms="+(System.nanoTime()-started)/1000000);
                }
            }
            player.setPos(28.5,100,.5);
            String probeId=MapActors.get(level).spawn(level,"scourge_ambush_zombie",new net.minecraft.world.phys.Vec3(28.5,100,3.5),"");
            var mob=(net.minecraft.world.entity.PathfinderMob)level.getEntity(UUID.fromString(probeId));
            int successes=0;
            for(int tick=0;tick<12;tick++){
                server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
                for(int n=0;n<6;n++)mob.getNavigation().createPath(player,0);
                String id=api.spawnMapActorNearPlayer("scourge_ambush_zombie",player.getUUID().toString(),6,14);
                if(!id.isEmpty()){successes++;api.despawnMapActor(id);}
            }
            check(successes>0,"Hidden spawns succeed after ordinary AI exhausts the shared path budget ("+successes+"/12)");
            api.despawnMapActor(probeId);
            LuaScriptManager.callEvent("OnGameStart");
            var pulse=new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,server);
            long before=count(level,"scourge_ambush_");
            for(int tick=0;tick<260;tick++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);LuaScriptManager.onServerTick(pulse);}
            check(count(level,"scourge_ambush_")>before,"Actual map scripts and real Lua timers spawn Scourge");
            String zone=me.cryo.zombierool.block.system.MusicVolume.Data.get(level).list().getAllKeys().iterator().next();
            check(api.toggleMusicVolume(player.getUUID().toString(),zone),"The relocated map music zone toggles on");
            System.out.println("STRATH PROBE zone="+zone+" preset="+LuaScriptManager.executeString("return ZombieroolAPI:getMusicVolume('"+zone+"').preset"));
            before=count(level,"scourge_ambush_");long risenBefore=count(level,"scarlet_zombie");
            for(int tick=0;tick<1200;tick++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);LuaScriptManager.onServerTick(pulse);}
            check(count(level,"scarlet_zombie")>risenBefore,"Actual Risen script spawns from the relocated zone");
            check(count(level,"scourge_ambush_")==before,"Actual Scourge scheduler pauses during Risen music");
            check(api.toggleMusicVolume(player.getUUID().toString(),zone),"Risen music toggles off");
            check(!api.isMusicVolumeActive(player.getUUID().toString(),zone),"The zone is inactive after toggling off");
            // The scripted simulation does not tick combat AI: clear stationary test mobs
            // so their hitboxes do not occupy every hidden placement in this street.
            for(var e:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(MapActors::owns).toList())api.despawnMapActor(e.getUUID().toString());
            before=count(level,"scourge_ambush_");
            for(int tick=0;tick<1000;tick++){server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);LuaScriptManager.onServerTick(pulse);}
            check(count(level,"scourge_ambush_")>before,"Actual Scourge scheduler resumes after Risen");
            for(int x=-8;x<=8;x++)for(int z=-8;z<=8;z++){
                level.setBlock(new BlockPos(x,198,z),me.cryo.zombierool.init.ZombieroolModBlocks.PATH.get().defaultBlockState(),2);
                level.setBlock(new BlockPos(x,199,z),net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(),2);
            }
            player.setGameMode(GameType.CREATIVE);
            for(boolean nativeMob:new boolean[]{true,false}){
                var guard=MapActorTemplates.profile(false);guard.putString("behavior","guard");guard.putDouble("wanderRadius",4);guard.putDouble("leashRadius",12);
                if(nativeMob)guard.putString("baseEntity","geist");
                String id=nativeMob?"probe_native_guard":"probe_custom_guard";
                MapActors.get(level).put(level,id,guard);
                var markerPos=new BlockPos(0,200,0);
                level.setBlock(markerPos,me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get().defaultBlockState(),2);
                var rule=MapActorTemplates.marker();rule.putString("profile",id);rule.putString("behavior","guard");rule.putDouble("wanderRadius",0);
                MapActors.get(level).marker(markerPos,rule);
                String uuid=MapActors.get(level).spawn(level,id,new net.minecraft.world.phys.Vec3(.5,200,.5),Long.toString(markerPos.asLong()));
                var sentry=(net.minecraft.world.entity.Mob)level.getEntity(UUID.fromString(uuid));
                sentry.setOnGround(true);
                rule.putDouble("wanderRadius",4);MapActors.get(level).marker(markerPos,rule);
                check(MapKnifeRules.profile(sentry).getDouble("wanderRadius")==4,"Editing a placed spawner updates its already-living guard immediately");
                boolean patrolled=false;
                for(int attempt=0;attempt<30&&!patrolled;attempt++){
                    server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
                    patrolled=MapActorBehavior.patrol(sentry,MapKnifeRules.profile(sentry),sentry.position());
                }
                check(patrolled,(nativeMob?"Native Geist":"Custom actor")+" in guard mode finds a real patrol path inside radius");
                var origin=sentry.position();
                double traveled=0;
                for(int tick=0;tick<160;tick++){
                    server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);sentry.tick();
                    if(tick%20==0)MapActorBehavior.requestPatrol(sentry,MapKnifeRules.profile(sentry));
                    MapActorBehavior.patrolTick(new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER,net.minecraftforge.event.TickEvent.Phase.END,level,()->true));
                    traveled=Math.max(traveled,sentry.position().distanceToSqr(origin));
                }
                System.out.println("STRATH PROBE patrol="+nativeMob+" movedSquared="+traveled+" position="+sentry.position());
                check(traveled>.1,(nativeMob?"Native Geist":"Custom actor")+" physically moves along its patrol");
                sentry.getNavigation().stop();guard=MapKnifeRules.profile(sentry).copy();guard.putDouble("wanderRadius",0);
                check(!MapActorBehavior.patrol(sentry,guard,origin),"Zero patrol radius keeps the sentry stationary");
                api.despawnMapActor(uuid);
            }
            System.out.println("STRATH PROBE COMPLETE");
        } catch(Throwable e) {e.printStackTrace();System.out.println("STRATH PROBE console="+LuaScriptManager.console());}
        finally {server.halt(false);}
    }
}
