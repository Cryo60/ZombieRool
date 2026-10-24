package local.hotfixtest;

import java.util.*;
import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.*;

public final class TeleporterRedistributionTest {
    private static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);System.out.println("TRANSFER CHECK: "+text);}
    private static Object field(String name)throws Exception{var f=WaveManager.class.getDeclaredField(name);f.setAccessible(true);return f.get(null);}
    private static void set(String name,Object value)throws Exception{var f=WaveManager.class.getDeclaredField(name);f.setAccessible(true);f.set(null,value);}
    private static UniversalSpawnerBlockEntity spawner(ServerLevel level,BlockPos pos,String channels){
        level.setBlock(pos,UniversalSpawnerSystem.UNIVERSAL_SPAWNER_BLOCK.get().defaultBlockState(),3);
        var be=(UniversalSpawnerBlockEntity)level.getBlockEntity(pos);be.setConfig(SpawnerMobType.ZOMBIE,"",channels,"0",false,1);be.onLoad();return be;
    }
    private static List<Mob> transferred(ServerLevel level,BlockPos origin){
        List<Mob> result=new ArrayList<>();for(var e:level.getAllEntities())if(e instanceof Mob m&&m.isAlive()&&m.getPersistentData().contains("zr_spawn_origin")&&m.getPersistentData().getLong("zr_spawn_origin")==origin.asLong())result.add(m);return result;
    }
    private static BlockPathTypes nodeType(ServerLevel level,BlockPos pos,Mob mob){
        var region=new net.minecraft.world.level.PathNavigationRegion(level,pos.offset(-4,-2,-4),pos.offset(4,5,4));
        var evaluator=new WalkNodeEvaluator();evaluator.prepare(region,mob);
        try{return evaluator.getBlockPathType(region,pos.getX(),pos.getY(),pos.getZ(),mob);}finally{evaluator.done();}
    }
    @SuppressWarnings({"unchecked","rawtypes"}) public static void run(ServerLevel level)throws Exception{
        level.getServer().getWorldData().setDifficulty(net.minecraft.world.Difficulty.NORMAL);
        var defaults=TeleporterConfig.defaults(TeleporterConfig.Mode.DIRECT);check(defaults.getInt("chargeTime")==3,"New departures and returns use three-second charge");
        var old=defaults.copy();old.remove("chargeVersion");old.putInt("chargeTime",6);check(TeleporterConfig.sanitize(old).getInt("chargeTime")==3,"Existing six-second defaults migrate to three seconds");
        old.putInt("chargeTime",4);check(TeleporterConfig.sanitize(old).getInt("chargeTime")==4,"Custom four-second charge survives migration");
        defaults.putInt("chargeTime",6);check(TeleporterConfig.sanitize(defaults).getInt("chargeTime")==6,"New explicit custom duration remains configurable");
        defaults.putBoolean("redistributeMobs",true);check(TeleporterConfig.sanitize(defaults).getBoolean("redistributeMobs"),"Redistribution option persists through configuration sanitization");
        BlockPos source=new BlockPos(1500,80,0),target=source.east(12),other=source.east(24),pad=source.north(10);
        for(int x=-3;x<=28;x++)for(int z=-13;z<=3;z++){level.setBlock(source.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);for(int y=0;y<5;y++)level.setBlock(source.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);}
        level.setBlock(pad,TeleporterSystem.PLATFORM.get().defaultBlockState(),3);
        var padState=level.getBlockState(pad);
        check(padState.is(net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,new net.minecraft.resources.ResourceLocation("zombierool","allowed_blocks"))),"Teleporter is in the actual navigation whitelist");
        check(padState.isPathfindable(level,pad,PathComputationType.LAND),"Visible teleporter is land navigable");
        var probe=ZombieroolModEntities.ZOMBIE.get().create(level);
        check(nodeType(level,pad,probe)!=BlockPathTypes.BLOCKED,"Applied zombie navigation mixin accepts a visible pad without path markers");
        level.setBlock(pad,padState.setValue(TeleporterSystem.Platform.INVISIBLE,true),3);
        check(nodeType(level,pad,probe)!=BlockPathTypes.BLOCKED,"Invisible pad also authorizes zombie navigation");
        var src=spawner(level,source,"0,100");var dst=spawner(level,target,"1");var untouched=spawner(level,other,"2");
        WaveManager.UNLOCKED_CHANNELS.clear();WaveManager.LOCKED_CHANNELS.clear();WaveManager.UNLOCKED_CHANNELS.add(0);WaveManager.UNLOCKED_CHANNELS.add(100);WaveManager.UNLOCKED_CHANNELS.add(2);
        set("gameRunning",true);set("isSpecialWave",false);set("currentWave",1);set("zombiesToKill",2);
        var stateField=WaveManager.class.getDeclaredField("currentState");stateField.setAccessible(true);stateField.set(null,Enum.valueOf((Class)stateField.getType(),"WAITING_CLEAR"));
        set("currentWaveSpawnOrder",new ArrayList<>(List.of(false)));set("mobsToSpawnInThisWave",1);
        var spawn=WaveManager.class.getDeclaredMethod("spawnOneRegularMob",ServerLevel.class);spawn.setAccessible(true);
        // Leave only channel 0 eligible for the ordinary spawning path.
        WaveManager.LOCKED_CHANNELS.add(2);spawn.invoke(null,level);WaveManager.LOCKED_CHANNELS.remove(2);
        var original=transferred(level,source).get(0);original.setHealth(original.getMaxHealth()/2);float originalHealth=original.getHealth();
        var dog=ZombieroolModEntities.HELLHOUND.get().create(level);dog.setPos(source.getX()+.5,80,2);SpawnRedistribution.track(dog,level,source);level.addFreshEntity(dog);WaveManager.attachAfterRedistribution(dog);
        var minion=ZombieroolModEntities.SKELETON_MINION.get().create(level);minion.setPos(source.getX()+.5,80,-2);SpawnRedistribution.track(minion,level,source);minion.getPersistentData().putBoolean("zr_special_spawner",true);check(level.addFreshEntity(minion),"minion fixture joins the world");
        var boss=ZombieroolModEntities.BRUTUS.get().create(level);boss.setPos(source.getX()+2.5,80,2);SpawnRedistribution.track(boss,level,source);boss.getPersistentData().putBoolean("zr_special_spawner",true);check(level.addFreshEntity(boss),"boss fixture joins the world");
        var napalm=ZombieroolModEntities.NAPALM_MINION.get().create(level);napalm.setPos(source.getX()+3.5,80,-2);SpawnRedistribution.track(napalm,level,source);napalm.getPersistentData().putBoolean("zr_special_spawner",true);check(level.addFreshEntity(napalm),"napalm fixture joins the world");
        var untracked=ZombieroolModEntities.ZOMBIE.get().create(level);untracked.setPos(other.getX(),80,0);level.addFreshEntity(untracked);
        var safe=ZombieroolModEntities.ZOMBIE.get().create(level);safe.setPos(other.getX(),80,2);SpawnRedistribution.track(safe,level,other);level.addFreshEntity(safe);
        untouched.setConfig(SpawnerMobType.PLAYER,"","2","0",false,1);
        long time=level.getGameTime();var settings=new CompoundTag();settings.putString("enableChannels","1");settings.putString("disableChannels","0,100");settings.putInt("channelDuration",10);settings.putBoolean("redistributeMobs",true);
        SpawnChannelActions.apply(level,settings);
        check(original.isRemoved()&&dog.isRemoved()&&minion.isRemoved()&&boss.isRemoved()&&napalm.isRemoved(),"Only living enemies from newly disabled spawners disappear");
        check(!untracked.isRemoved()&&!safe.isRemoved(),"Unrelated active spawners and untracked enemies are untouched");
        check(WaveManager.remainingEnemies()==2&&((Set<?>)field("activeMobs")).isEmpty(),"Redistribution preserves the wave kill budget and detaches active IDs");
        check(SpawnRedistribution.pendingBosses(level)==1&&SpecialEnemySpawns.livingBoss(level),"Queued boss prevents premature wave completion");
        check(!NapalmSpawnRules.available(level),"Queued napalm blocks a duplicate napalm spawn");
        SpawnRedistribution.tick(level);check(transferred(level,target).isEmpty(),"No simultaneous respawn burst");
        for(int i=1;i<=5;i++){level.getServer().getWorldData().overworldData().setGameTime(time+10L*i);SpawnRedistribution.tick(level);check(transferred(level,target).size()==i,"Exactly one enemy returns per half second: "+i);for(var m:transferred(level,target))m.setPos(target.getX()+2,80,2);}
        var arrived=transferred(level,target);
        check(arrived.stream().anyMatch(m->m instanceof ZombieEntity&&m.getHealth()==originalHealth)&&arrived.stream().anyMatch(m->m instanceof HellhoundEntity)&&arrived.stream().anyMatch(m->m instanceof SpecialEnemyEntity s&&!s.isBoss())&&arrived.stream().anyMatch(m->m instanceof SpecialEnemyEntity s&&s.isBoss()),"Zombie health, normal-wave dog, minion and boss types survive relocation");
        check(!SpawnRedistribution.pending(level)&&((Set<?>)field("activeMobs")).size()==2&&WaveManager.remainingEnemies()==2,"All replacements rejoin accounting without creating extra kills");
        level.getServer().getWorldData().overworldData().setGameTime(time+200);SpawnChannelActions.tick(new net.minecraftforge.event.TickEvent.ServerTickEvent(net.minecraftforge.event.TickEvent.Phase.END,()->true,level.getServer()));
        check(src.isActive(level)&&!dst.isActive(level)&&SpawnRedistribution.pending(level),"Timed restoration automatically redistributes channel 1 enemies back");
        for(int i=1;i<=5;i++){level.getServer().getWorldData().overworldData().setGameTime(time+200+10L*i);SpawnRedistribution.tick(level);for(var m:transferred(level,source))m.setPos(source.getX()+2,80,2);}
        check(transferred(level,source).size()==5&&!SpawnRedistribution.pending(level),"Reverse transfer returns every enemy to reactivated source spawners");
        // With no destination, keep all queued enemies and the remaining wave budget.
        settings.putString("enableChannels","");settings.putInt("channelDuration",0);SpawnChannelActions.apply(level,settings);
        WaveManager.LOCKED_CHANNELS.add(2);level.getServer().getWorldData().overworldData().setGameTime(time+260);SpawnRedistribution.tick(level);
        check(SpawnRedistribution.pending(level)&&WaveManager.remainingEnemies()==2,"Missing active destinations retain the pending enemies and wave budget");
        SpawnChannelActions.reset();check(!SpawnRedistribution.pending(level),"Match reset clears redistribution state");
        WaveManager.LOCKED_CHANNELS.clear();WaveManager.UNLOCKED_CHANNELS.add(0);WaveManager.UNLOCKED_CHANNELS.add(100);WaveManager.UNLOCKED_CHANNELS.remove(1);
        set("isSpecialWave",true);((java.util.concurrent.atomic.AtomicInteger)field("specialSpawnedCount")).set(1);((java.util.concurrent.atomic.AtomicInteger)field("specialWaveTotal")).set(1);((java.util.concurrent.atomic.AtomicInteger)field("specialWaveKilled")).set(0);((java.util.concurrent.atomic.AtomicInteger)field("hellhoundsCurrentlyActive")).set(1);
        var specialDog=ZombieroolModEntities.HELLHOUND.get().create(level);specialDog.setPos(source.getX(),80,0);SpawnRedistribution.track(specialDog,level,source);level.addFreshEntity(specialDog);((Set<UUID>)field("activeMobs")).add(specialDog.getUUID());
        settings.putString("enableChannels","1");SpawnChannelActions.apply(level,settings);
        check(specialDog.isRemoved()&&((java.util.concurrent.atomic.AtomicInteger)field("hellhoundsCurrentlyActive")).get()==0&&WaveManager.remainingEnemies()==1,"Special-wave dog is removed without advancing the kill counter");
        level.getServer().getWorldData().overworldData().setGameTime(level.getGameTime()+10);SpawnRedistribution.tick(level);
        check(!SpawnRedistribution.pending(level)&&((java.util.concurrent.atomic.AtomicInteger)field("hellhoundsCurrentlyActive")).get()==1&&((java.util.concurrent.atomic.AtomicInteger)field("specialSpawnedCount")).get()==1&&WaveManager.remainingEnemies()==1,"Special-wave dog returns without duplicate spawn budget");
        set("isSpecialWave",false);SpawnChannelActions.reset();
        set("gameRunning",false);((Set<?>)field("activeMobs")).clear();
        for(var e:List.copyOf(transferred(level,other)))e.discard();untracked.discard();
        System.out.println("TRANSFER TEST PASS");
    }
}
