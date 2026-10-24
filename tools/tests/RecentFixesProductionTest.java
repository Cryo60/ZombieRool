package local.actorproduction;

import me.cryo.zombierool.gameplay.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.fml.common.Mod;

/** Exercises the distributed SRG JAR in normal Forge, rather than a mapped dev client. */
@Mod("actorproduction")
public final class RecentFixesProductionTest {
    public RecentFixesProductionTest(){MinecraftForge.EVENT_BUS.addListener(this::start);}
    private void start(ServerStartedEvent event){var server=event.getServer();try{
        var level=server.overworld();
        var batchSettings=new CompoundTag();batchSettings.putInt("batch",3);batchSettings.putInt("maxAlive",1);
        MapActors.normalizeSpawnBatch(batchSettings);
        if(batchSettings.getInt("maxAlive")!=3)throw new AssertionError("Batch 3 did not raise the population limit");
        batchSettings.putInt("batch",7);MapActors.normalizeSpawnBatch(batchSettings);
        if(batchSettings.getInt("maxAlive")!=7)throw new AssertionError("Batch 7 did not raise the population limit");
        batchSettings.putInt("maxAlive",20);MapActors.normalizeSpawnBatch(batchSettings);
        if(batchSettings.getInt("maxAlive")!=20)throw new AssertionError("Larger manual population limit was reduced");
        batchSettings.putInt("batch",3);MapActors.normalizeSpawnBatch(batchSettings);
        if(batchSettings.getInt("maxAlive")!=20)throw new AssertionError("Lowering the batch reduced the population limit");
        var markerSettings=MapActorTemplates.marker();markerSettings.putString("profile","builtin_ghoul");markerSettings.putInt("batch",7);markerSettings.putInt("maxAlive",1);
        var markerPos=new BlockPos(130,65,130);MapActors.get(level).marker(markerPos,markerSettings);
        if(MapActors.get(level).marker(markerPos).getInt("maxAlive")!=7)throw new AssertionError("Saved marker population limit was not raised");
        System.out.println("RECENT PRODUCTION batch auto-limit PASS: 3/7, larger limits preserved, marker save normalized");
        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL,true);
        WaveManager.startGame(level);
        if(level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)throw new AssertionError("Starting after defeat leaves native enemies in Peaceful");
        WaveManager.endGame(level,null);
        WaveManager.startGame(level);
        if(level.getDifficulty()==net.minecraft.world.Difficulty.PEACEFUL)throw new AssertionError("Second match start leaves native enemies in Peaceful");
        WaveManager.endGame(level,null);
        System.out.println("RECENT PRODUCTION difficulty restart PASS: Peaceful restored for both first and post-defeat match starts");
        var path=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation("zombierool:path"));
        for(int x=128;x<160;x++)for(int z=128;z<152;z++){
            level.setBlock(new BlockPos(x,53,z),path.defaultBlockState(),2);
            for(int y=54;y<74;y++)level.setBlock(new BlockPos(x,y,z),(y==54||y==64?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        }
        var config=me.cryo.zombierool.config.WorldConfig.get(level);
        for(var pos:new java.util.ArrayList<>(config.getPathPositions()))if(!pos.equals(new BlockPos(146,53,140)))config.removePathPosition(pos,level);
        config.addPathPosition(new BlockPos(146,53,140),level);
        var player=net.minecraftforge.common.util.FakePlayerFactory.getMinecraft(level);
        player.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);player.setPos(140.5,65,140.5);
        level.players().add(player);
        var restartMarker=new BlockPos(132,65,148);
        level.setBlock(restartMarker,me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get().defaultBlockState(),3);
        var restartRule=MapActorTemplates.marker();restartRule.putString("profile","builtin_abomination");restartRule.putDouble("spawnRadius",0);
        MapActors.get(level).marker(restartMarker,restartRule);
        var pulse=new net.minecraftforge.event.TickEvent.LevelTickEvent(net.minecraftforge.fml.LogicalSide.SERVER,net.minecraftforge.event.TickEvent.Phase.END,level,()->true);
        var active=WaveManager.class.getDeclaredField("gameRunning");active.setAccessible(true);
        for(int round=0;round<3;round++){
            active.setBoolean(null,false);MapActors.begin(level);active.setBoolean(null,true);
            server.getWorldData().overworldData().setGameTime(100+round*10);MapActors.tick(pulse);
            var spawned=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
            for(var entity:level.getAllEntities())if(NativeMapActors.owns(entity)&&entity.getPersistentData().getString("MapMarker").equals(Long.toString(restartMarker.asLong())))spawned.add(entity);
            if(spawned.size()!=1)throw new AssertionError("Abomination restart cycle "+round+" spawned "+spawned.size());
            if(round==0)((net.minecraft.world.entity.LivingEntity)spawned.get(0)).setHealth(0);
            active.setBoolean(null,false);MapActors.end(level);
        }
        MapActors.get(level).removeMarker(restartMarker);
        var batchMarker=new BlockPos(108,65,140);
        for(int x=106;x<=110;x++)for(int z=138;z<=142;z++)for(int y=64;y<=70;y++)level.setBlock(new BlockPos(x,y,z),(y==64?Blocks.STONE:Blocks.AIR).defaultBlockState(),2);
        level.setBlock(batchMarker,me.cryo.zombierool.block.system.UniversalSpawnerSystem.ACTOR_MARKER_BLOCK.get().defaultBlockState(),3);
        var batchRule=MapActorTemplates.marker();batchRule.putString("profile","builtin_ghoul");batchRule.putInt("batch",7);batchRule.putInt("maxAlive",7);batchRule.putDouble("spawnRadius",8);batchRule.putBoolean("countsForWave",false);
        MapActors.get(level).marker(batchMarker,batchRule);MapActors.begin(level);active.setBoolean(null,true);
        server.getWorldData().overworldData().setGameTime(200);MapActors.tick(pulse);
        var batchMobs=new java.util.ArrayList<net.minecraft.world.entity.Mob>();
        for(var entity:level.getAllEntities())if(NativeMapActors.owns(entity)&&entity.getPersistentData().getString("MapMarker").equals(Long.toString(batchMarker.asLong())))batchMobs.add((net.minecraft.world.entity.Mob)entity);
        if(batchMobs.size()!=7)throw new AssertionError("Batch 7 on a small platform spawned "+batchMobs.size());
        for(int i=0;i<batchMobs.size();i++)for(int j=0;j<i;j++)if(batchMobs.get(i).getBoundingBox().intersects(batchMobs.get(j).getBoundingBox()))throw new AssertionError("Batch enemies overlap");
        MapActors.tick(pulse);
        if(java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(MapActors::owns).count()!=7)throw new AssertionError("Completed batch spawned duplicates");
        active.setBoolean(null,false);MapActors.end(level);MapActors.get(level).removeMarker(batchMarker);
        // Force a partially blocked batch, then free room for the outstanding spawn.
        for(int x=106;x<=110;x++)for(int z=138;z<=142;z++)level.setBlock(new BlockPos(x,64,z),Blocks.AIR.defaultBlockState(),2);
        level.setBlock(batchMarker.below(),Blocks.STONE.defaultBlockState(),2);batchRule.putInt("batch",3);batchRule.putInt("maxAlive",3);batchRule.putDouble("spawnRadius",0);MapActors.get(level).marker(batchMarker,batchRule);
        MapActors.begin(level);active.setBoolean(null,true);
        for(int i=0;i<3;i++){
            server.getWorldData().overworldData().setGameTime(210+i*10);MapActors.tick(pulse);
            var group=new java.util.ArrayList<net.minecraft.world.entity.Mob>();for(var entity:level.getAllEntities())if(MapActors.owns(entity))group.add((net.minecraft.world.entity.Mob)entity);
            if(group.size()!=i+1)throw new AssertionError("Partial batch did not resume: "+group.size()+" at "+i);
            group.get(i).setPos(100+i*2,65,140);
        }
        MapActors.tick(pulse);
        if(java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(MapActors::owns).count()!=3)throw new AssertionError("Partial batch refilled beyond requested total");
        active.setBoolean(null,false);MapActors.end(level);MapActors.get(level).removeMarker(batchMarker);
        level.players().remove(player);
        System.out.println("RECENT PRODUCTION batch placement PASS: 7 distinct positions, partial batch resumed, no duplicate or extra spawns");
        System.out.println("RECENT PRODUCTION restart PASS: Abomination respawned in three consecutive sessions");
        var boss=me.cryo.zombierool.init.ZombieroolModEntities.CUSTOM_MINI_BOSS.get().create(level);
        boss.setBossProfile(me.cryo.zombierool.config.MiniBossDefinitions.get("zombierool:abomination"));
        boss.setPos(146.5,65,140.5);boss.setOnGround(true);boss.setNoAi(false);
        var profile=MapActorTemplates.builtin("builtin_abomination");profile.putDouble("aggroRadius",4);boss.getPersistentData().put("MapNativeProfile",profile);
        new BossJumpLinkGoal(boss).canUse();
        if(boss.getTarget()!=null)throw new AssertionError("Idle map boss acquired a distant player");
        boss.setTarget(player);new BossJumpLinkGoal(boss).canUse();
        if(boss.getTarget()!=player)throw new AssertionError("Existing boss target was lost");
        level.addFreshEntity(boss);
        active.setBoolean(null,true);
        if(!EncounterRules.roundIndependent(boss)||SpecialEnemySpawns.livingBoss(level))throw new AssertionError("Counts-for-wave Off still blocks the round");
        var enabled=NativeMapActors.profile(boss);enabled.putBoolean("countsForWave",true);
        if(EncounterRules.roundIndependent(boss)||!SpecialEnemySpawns.livingBoss(level))throw new AssertionError("Counts-for-wave On stopped counting a boss");
        enabled.putBoolean("countsForWave",false);active.setBoolean(null,false);
        int score=PointManager.getScore(player);
        me.cryo.zombierool.core.manager.DamageManager.onLivingDamage(new net.minecraftforge.event.entity.living.LivingDamageEvent(boss,level.damageSources().playerAttack(player),1));
        me.cryo.zombierool.core.manager.DamageManager.onLivingDeath(new net.minecraftforge.event.entity.living.LivingDeathEvent(boss,level.damageSources().playerAttack(player)));
        PointManager.awardEnemyPoints(player,boss,75);
        if(PointManager.getScore(player)!=score)throw new AssertionError("Summoned enemy awarded damage, kill or Collector points");
        boss.discard();
        System.out.println("RECENT PRODUCTION wave and points PASS: Off ignores living boss, On counts boss, summoned damage/kill/extra points suppressed");
        System.out.println("RECENT PRODUCTION boss target calls PASS");
        var data=MapActors.get(level);var actor=new CompoundTag();actor.putString("name","Production Risen");actor.putString("behavior","melee");actor.putDouble("width",.6);actor.putDouble("height",1.8);actor.putDouble("scale",1);actor.putDouble("health",40);actor.putString("faction","risen");
        data.put(level,"production_risen",actor);
        var running=WaveManager.class.getDeclaredField("gameRunning");running.setAccessible(true);running.setBoolean(null,true);
        // Fake player is not in the observer list, so use a solid obstruction at the upper candidate.
        for(int y=65;y<69;y++)level.setBlock(new BlockPos(146,y,140),Blocks.STONE.defaultBlockState(),2);
        server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
        String blocked=HiddenActorSpawn.spawn(player,"production_risen","",4,8);
        if(!blocked.isEmpty())throw new AssertionError("Sealed basement accepted in production");
        for(int y=65;y<69;y++)level.setBlock(new BlockPos(146,y,140),Blocks.AIR.defaultBlockState(),2);
        server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
        String id=HiddenActorSpawn.spawn(player,"production_risen","",4,8);
        if(id.isEmpty())throw new AssertionError("Reachable custom actor spawn failed in production");
        level.getEntity(java.util.UUID.fromString(id)).discard();
        server.getWorldData().overworldData().setGameTime(level.getGameTime()+1);
        id=HiddenActorSpawn.spawn(player,"builtin_zombie","",4,8);
        if(id.isEmpty())throw new AssertionError("Reachable native actor spawn failed in production");
        level.getEntity(java.util.UUID.fromString(id)).discard();
        running.setBoolean(null,false);
        System.out.println("RECENT PRODUCTION PASS: actual distributed JAR, boss target calls, sealed basement rejection, custom and native reachable spawns");
    }catch(Throwable t){t.printStackTrace();System.out.println("RECENT PRODUCTION FAIL: "+t);}finally{server.halt(false);}}
}
