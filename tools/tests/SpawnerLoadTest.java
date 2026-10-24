package local.strathprobe;

import java.util.*;
import com.mojang.authlib.GameProfile;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

/** Measures actual Forge ticks with 500 group spawners, wave enemies and native gore. */
@Mod("strathprobe")
public final class SpawnerLoadTest {
    private ServerLevel level;private FakePlayer player;private int ticks,stage;private long started;private boolean reservesTested;
    private final List<Long> samples=new ArrayList<>();
    private final Map<UUID,net.minecraft.world.phys.Vec3> positions=new HashMap<>();
    private final Set<UUID> moved=new HashSet<>();private final List<UUID> wave=new ArrayList<>();
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("LOAD CHECK: "+message);}
    private static java.lang.reflect.Field field(Class<?> type,String name,String srg)throws Exception{java.lang.reflect.Field f;try{f=type.getDeclaredField(name);}catch(NoSuchFieldException e){f=type.getDeclaredField(srg);}f.setAccessible(true);return f;}
    private static void waveField(String name,Object value)throws Exception{field(WaveManager.class,name,name).set(null,value);}
    public SpawnerLoadTest(){MinecraftForge.EVENT_BUS.addListener(this::start);MinecraftForge.EVENT_BUS.addListener(this::tick);}
    @SuppressWarnings("unchecked") private void start(ServerStartedEvent event){try{
        level=event.getServer().overworld();
        for(int x=-4;x<=152;x++)for(int z=-4;z<=122;z++){
            level.setBlock(new BlockPos(x,198,z),ZombieroolModBlocks.PATH.get().defaultBlockState(),2);
            level.setBlock(new BlockPos(x,199,z),Blocks.STONE.defaultBlockState(),2);
        }
        player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"SpawnerLoad"));player.setPos(74.5,200,60.5);level.addNewPlayer(player);player.setGameMode(GameType.SURVIVAL);player.getAbilities().invulnerable=true;
        var list=field(net.minecraft.server.players.PlayerList.class,"players","f_11196_");((List<ServerPlayer>)list.get(event.getServer().getPlayerList())).add(player);
        var ids=field(net.minecraft.server.players.PlayerList.class,"playersByUUID","f_11197_");((Map<UUID,ServerPlayer>)ids.get(event.getServer().getPlayerList())).put(player.getUUID(),player);
        waveField("gameRunning",true);waveField("currentWave",20);waveField("zombiesToKill",100000);
        var state=field(WaveManager.class,"currentState","currentState");state.set(null,Enum.valueOf((Class)state.getType(),"WAITING_CLEAR"));
        var data=MapActors.get(level);var p=MapActorTemplates.builtin("builtin_scourge_zombie");p.putString("behavior","guard");p.putDouble("aggroRadius",6);p.putDouble("wanderRadius",4);p.putDouble("leashRadius",12);data.put(level,"load_guard",p);var geist=p.copy();geist.putString("baseEntity","geist");data.put(level,"load_guard_geist",geist);
        var wp=p.copy();wp.putString("behavior","melee");wp.putDouble("aggroRadius",128);wp.putBoolean("countsForWave",true);wp.putDouble("wanderRadius",0);data.put(level,"load_wave",wp);
        spawnWave();
        for(int n=0;n<500;n++){
            var at=new BlockPos((n%25)*6,200,(n/25)*6);level.setBlock(at,me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get().defaultBlockState(),2);
            var rule=MapActorTemplates.marker();rule.putString("profile",n%4==0?"load_guard_geist":"load_guard");rule.putInt("batch",8);rule.putInt("maxAlive",8);rule.putDouble("spawnRadius",4);rule.putDouble("wanderRadius",4);rule.putDouble("aggroRadius",6);rule.putString("behavior","guard");data.marker(at,rule);
        }
        for(int n=0;n<300;n++){var corpse=ZombieroolModEntities.ZOMBIE.get().create(level);corpse.moveTo(70.5+(n%10)*.1,200,70.5+(n/10)*.1,0,0);corpse.setHealth(0);corpse.setNoAi(true);level.addFreshEntity(corpse);}
        gore(1100,600);check(count(GorePieceEntity.class)<=1024&&count(BloodDecalEntity.class)<=512,"A mass gore burst respects the allocation limits");System.out.println("LOAD READY: 500 markers, batch=8, patrolRadius=4, wave=24, corpses=300, goreRequests=1100, bloodRequests=600");
    }catch(Throwable e){e.printStackTrace();event.getServer().halt(false);level=null;}}
    @SuppressWarnings("unchecked") private void spawnWave(){try{
        var active=(Set<UUID>)field(WaveManager.class,"activeMobs","activeMobs").get(null);
        for(int n=0;n<24;n++){
            var zombie=ZombieroolModEntities.ZOMBIE.get().create(level);zombie.setCustomSkin("@preset/zombies/scourge/0");
            zombie.moveTo(63.5+(n%8)*3,200,51.5+(n/8)*3,0,0);zombie.setEmergenceTicks(0);zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(MapActorBehavior.waveHealth(level));zombie.setHealth(zombie.getMaxHealth());
            if(level.addFreshEntity(zombie)){active.add(zombie.getUUID());wave.add(zombie.getUUID());zombie.setTarget(player);}
        }
    }catch(Exception e){throw new RuntimeException(e);}}
    private void gore(int pieces,int pools){for(int n=0;n<pieces;n++)GorePieceEntity.spawn(level,player,GorePieceEntity.MEAT,0,"");BloodDecalEntity.pool(level,player.getX(),200,player.getZ(),pools);}
    private long count(Class<?> type){return java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(type::isInstance).count();}
    private long actors(){return java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(e->MapActors.owns(e)&&e.isAlive()).count();}
    private void report(){var sorted=samples.stream().mapToLong(Long::longValue).sorted().toArray();double mean=samples.stream().mapToLong(Long::longValue).average().orElse(0)/1e6;System.out.printf(Locale.ROOT,"LOAD RESULT stage=%d markers=500 actors=%d wave=%d gore=%d blood=%d patrolMoved=%d sampled=%d meanMs=%.3f p95Ms=%.3f p99Ms=%.3f maxMs=%.3f over50ms=%d%n",stage,actors(),wave.stream().filter(id->level.getEntity(id)!=null&&level.getEntity(id).isAlive()).count(),count(GorePieceEntity.class),count(BloodDecalEntity.class),moved.size(),sorted.length,mean,sorted[(int)(sorted.length*.95)]/1e6,sorted[(int)(sorted.length*.99)]/1e6,sorted[sorted.length-1]/1e6,samples.stream().filter(n->n>50_000_000).count());}
    private void tick(TickEvent.ServerTickEvent event){if(level==null)return;if(event.phase==TickEvent.Phase.START){started=System.nanoTime();return;}try{
        ticks++;player.setHealth(player.getMaxHealth());if(ticks%100==0)gore(80,30);
        if(!reservesTested&&ActorPopulation.marked(level)>=ActorPopulation.MARKER_LIMIT){int spawned=0;for(int n=0;n<32;n++){String id=MapActors.get(level).spawn(level,"load_guard",new net.minecraft.world.phys.Vec3(2.5+(n%16)*9,200,119.5+(n/16)*2),"");if(!id.isEmpty())spawned++;}check(spawned==32,"All 32 reserved encounter slots remain available after marker saturation");reservesTested=true;}
        if(ticks>200){for(var e:level.getAllEntities())if(e instanceof net.minecraft.world.entity.Mob mob&&MapActors.owns(mob)&&mob.isAlive()&&MapKnifeRules.profile(mob).getDouble("wanderRadius")==4){var before=positions.putIfAbsent(e.getUUID(),e.position());if(before!=null&&e.position().distanceToSqr(before)>.25)moved.add(e.getUUID());}samples.add(System.nanoTime()-started);}
        if(ticks<600)return;
        report();check(reservesTested,"The marker cap is reached and reserved encounters remain available");check(actors()>=200,"Hundreds of group-spawned actors remain active");check(moved.size()>=100,"At least 100 guards actually patrol under simultaneous wave and gore load");
        long corpses=java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(e->e instanceof net.minecraft.world.entity.LivingEntity living&&living.isDeadOrDying()).count();check(corpses<=256,"Corpse pressure cleanup bounds lingering bodies without deleting live enemies");
        if(stage==0){
            long burst=System.nanoTime();int before=(int)field(WaveManager.class,"zombiesToKill","zombiesToKill").get(null);int killed=0;
            for(var id:wave){var e=level.getEntity(id);if(e instanceof net.minecraft.world.entity.LivingEntity mob&&mob.isAlive()){mob.hurt(mob.damageSources().genericKill(),Float.MAX_VALUE);killed++;}}
            int after=(int)field(WaveManager.class,"zombiesToKill","zombiesToKill").get(null);check(before-after==killed,"Wave quota decrements exactly once per killed counted enemy");
            int removed=0;for(var e:java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).toList())if(MapActors.owns(e)&&e.isAlive()&&removed<128){e.discard();removed++;}
            wave.clear();spawnWave();gore(1100,600);System.out.println("LOAD BURST ms="+(System.nanoTime()-burst)/1e6+" totalTickMs="+(System.nanoTime()-started)/1e6+" freedSlots="+removed);
            stage=1;ticks=0;samples.clear();positions.clear();moved.clear();
        }else{System.out.println("LOAD PASS: sustained spawners, patrol fairness, wave accounting, gore churn and replacements");level.getServer().halt(false);level=null;}
    }catch(Throwable e){e.printStackTrace();level.getServer().halt(false);level=null;}}
}
