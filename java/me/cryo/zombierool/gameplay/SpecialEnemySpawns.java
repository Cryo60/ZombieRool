package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

public final class SpecialEnemySpawns {
    private static final java.util.Map<ServerLevel,BossBudget> BUDGETS=new java.util.WeakHashMap<>();
    private static final class BossBudget {int wave=-1,spawned,rounds,quota;}
    public static int bossCount(int encounters,int players){return players<=1?1:encounters<2?1:encounters<5?2:3;}
    private static long session=java.util.concurrent.ThreadLocalRandom.current().nextLong();
    private SpecialEnemySpawns(){}
    public static boolean due(int wave,int first,int interval){return wave>=first&&(wave-first)%interval==0;}
    public static boolean livingBoss(ServerLevel level){if(SpawnRedistribution.pendingRoundBosses(level)>0)return true;for(var e:level.getAllEntities())if(e instanceof SpecialEnemyEntity s&&s.isBoss()&&!EncounterRules.roundIndependent(s)&&s.isAlive())return true;return false;}
    public static void reset(ServerLevel level){
        BUDGETS.remove(level);MiniBossLoot.reset(level);EncounterRules.reset(level);
        session=java.util.concurrent.ThreadLocalRandom.current().nextLong();
        NapalmSpawnRules.reset(level);
        for(var spawner:me.cryo.zombierool.spawner.SpawnerRegistry.getSpawners(level))spawner.resetSpecialRuntime();
        var victims=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        for(var e:level.getAllEntities())if(e instanceof SpecialEnemyEntity||e instanceof me.cryo.zombierool.entity.NapalmFireEntity||e instanceof me.cryo.zombierool.entity.PanzerClawEntity)victims.add(e);
        victims.forEach(net.minecraft.world.entity.Entity::discard);
    }
    public static void clearMinions(ServerLevel level){
        var expired=new java.util.ArrayList<net.minecraft.world.entity.Entity>();
        for(var e:level.getAllEntities())if(e instanceof SpecialEnemyEntity s&&!s.isBoss()||e instanceof me.cryo.zombierool.entity.NapalmFireEntity)expired.add(e);
        expired.forEach(net.minecraft.world.entity.Entity::discard);
        for(var be:me.cryo.zombierool.spawner.SpawnerRegistry.getSpawners(level))if(be.getMobType().special()&&!be.getMobType().boss()){be.nextMinionTick=0;be.setChanged();}
    }
    public static void tick(UniversalSpawnerBlockEntity be){
        if(!(be.getLevel() instanceof ServerLevel level)||!be.getMobType().special()||!WaveManager.isGameRunning()||WaveManager.isPausedByPlayer()||(!me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getString("type").equals("zombierool:george_romero")&&(WaveManager.isIntroPhase()||!WaveManager.isCombatWave()))||!be.isActive(level))return;
        MiniBossTransitions.evaluate(level);
        var config=be.specialConfig();
        if(be.specialSession!=session){be.resetSpecialRuntime();be.specialSession=session;}
        int wave=WaveManager.getCurrentWave();
        if(be.getMobType().boss()){
            spawnBosses(level,wave);
        }else{
            if(wave<config.getInt("FirstWave"))return;
            if(be.getMobType()==SpawnerMobType.NAPALM_MINION&&!NapalmSpawnRules.available(level))return;
            if(level.getGameTime()<be.nextMinionTick)return;
            double radius=config.getInt("Radius");
            Vec3 center=Vec3.atCenterOf(be.getBlockPos());
            boolean nearby=level.players().stream().anyMatch(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())&&p.distanceToSqr(center)<=radius*radius);
            if(config.getBoolean("UseRadius")&&!nearby)return;
            int alive=(int)SpawnRedistribution.pendingType(level,be.getMobType());
            for(var e:level.getAllEntities())if(e instanceof SpecialEnemyEntity s&&s.isAlive()&&s.getPersistentData().contains("zr_minion_spawner")&&s.getPersistentData().getLong("zr_minion_spawner")==be.getBlockPos().asLong())alive++;
            int count=Math.min(config.getInt("Batch"),config.getInt("MaxAlive")-alive);
            if(be.getMobType()==SpawnerMobType.NAPALM_MINION)count=Math.min(1,count);
            for(int i=0;i<count;i++)if(!spawn(level,be,false))break;
            be.nextMinionTick=level.getGameTime()+config.getInt("Cooldown")*20L;
            be.setChanged();
        }
    }
    private static void spawnBosses(ServerLevel level,int wave){
        var budget=BUDGETS.computeIfAbsent(level,k->new BossBudget());
        var pool=new java.util.ArrayList<UniversalSpawnerBlockEntity>();
        for(var candidate:me.cryo.zombierool.spawner.SpawnerRegistry.getSpawners(level)){
            var c=candidate.specialConfig();
            if(candidate.getMobType().boss()&&candidate.isActive(level)&&(me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getString("type").equals("zombierool:george_romero")?EncounterRules.romeroDue(level,wave):due(wave,c.getInt("FirstWave"),c.getInt("WaveInterval"))))pool.add(candidate);
        }
        if(pool.isEmpty())return;
        int players=(int)level.players().stream().filter(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()).count();if(players==0)return;
        if(budget.wave!=wave){
            // Persisted spawner markers prevent a loaded map from replaying the same encounter.
            if(pool.stream().anyMatch(p->p.specialSession==session&&p.lastBossWave==wave))return;
            budget.wave=wave;budget.spawned=0;int encounter=budget.rounds++;
            for(var p:pool){var c=p.specialConfig();encounter=Math.max(encounter,(wave-c.getInt("FirstWave"))/c.getInt("WaveInterval"));}
            budget.quota=me.cryo.zombierool.config.SpecialEnemyConfig.get(level).getString("type").equals("zombierool:george_romero")?1:bossCount(encounter,players);
        }
        if(budget.spawned>=budget.quota)return;
        long alive=SpawnRedistribution.pendingBosses(level)+java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).filter(e->e instanceof SpecialEnemyEntity boss&&boss.isBoss()&&boss.isAlive()&&!EncounterRules.roundIndependent(boss)).count();
        if(alive>=budget.quota)return;
        while(!pool.isEmpty()&&budget.spawned<budget.quota&&alive<budget.quota){
            long total=pool.stream().mapToLong(p->p.getSpawnWeight()).sum(),pick=(long)(level.random.nextDouble()*total);
            var chosen=pool.get(0);for(var candidate:pool){pick-=candidate.getSpawnWeight();if(pick<0){chosen=candidate;break;}}
            if(spawn(level,chosen,true)){budget.spawned++;alive++;for(var p:pool){p.specialSession=session;p.lastBossWave=wave;p.setChanged();}}
            else pool.remove(chosen);
        }
    }
    private static EntityType<SpecialEnemyEntity> type(SpawnerMobType type){return switch(type){case PANZER->ZombieroolModEntities.PANZER.get();case BRUTUS->ZombieroolModEntities.BRUTUS.get();case NAPALM_MINION->ZombieroolModEntities.NAPALM_MINION.get();default->ZombieroolModEntities.SKELETON_MINION.get();};}
    public static boolean spawn(ServerLevel level,UniversalSpawnerBlockEntity be,boolean boss){
        if(boss)MiniBossTransitions.evaluate(level);
        var settings=me.cryo.zombierool.config.SpecialEnemyConfig.get(level);
        String bossId=settings.getString("type");var profile=me.cryo.zombierool.config.MiniBossDefinitions.get(bossId);
        var mob=(!boss||profile.isEmpty()?type(boss?me.cryo.zombierool.config.SpecialEnemyConfig.type(level):be.getMobType()):ZombieroolModEntities.CUSTOM_MINI_BOSS.get()).create(level);if(mob==null)return false;
        if(boss&&!profile.isEmpty()){profile.merge(settings.getCompound("bossOverrides").getCompound(bossId));mob.setBossProfile(profile);mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(profile.getDouble("damage"));}
        BlockPos origin=be.getBlockPos();
        // The technical spawner is non-solid. Retry nearby feet positions if its volume is obstructed.
        for(int attempt=0;attempt<12;attempt++){
            int dx=attempt==0?0:level.random.nextInt(5)-2,dz=attempt==0?0:level.random.nextInt(5)-2;
            BlockPos feet=origin.offset(dx,0,dz);
            if(!level.hasChunkAt(feet))continue;
            // Technical Path markers have no collision; resolve the real floor (also slabs).
            double ground=Double.NaN;
            for(int down=1;down<=8;down++){
                BlockPos floor=feet.below(down);
                var shape=level.getBlockState(floor).getCollisionShape(level,floor);
                if(!shape.isEmpty()){ground=floor.getY()+shape.max(net.minecraft.core.Direction.Axis.Y);break;}
            }
            if(Double.isNaN(ground))continue;
            mob.moveTo(feet.getX()+.5,ground,feet.getZ()+.5,level.random.nextFloat()*360,0);
            // Reserve the landing volume too: another Panzer may still be high above this floor.
            if(boss){
                boolean occupied=false;
                for(var entity:level.getAllEntities())if(entity instanceof SpecialEnemyEntity other&&other.isBoss()&&other.isAlive()){
                    double separation=(mob.getBbWidth()+other.getBbWidth())*.5+.1;
                    if(other.spawnLanding().distanceToSqr(mob.position())<separation*separation){occupied=true;break;}
                }
                if(occupied)continue;
            }
            if(!level.noCollision(mob))continue;
            if(boss){
                double players=Math.max(1,level.players().stream().filter(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()).count());
                double health=(profile.isEmpty()?settings.getDouble(mob.isPanzer()?"panzerHealth":"brutusHealth"):profile.getDouble("health"))*(1+Math.max(0,WaveManager.getCurrentWave()-settings.getInt("firstWave"))*settings.getDouble("waveHealthGrowth"))*(1+settings.getDouble("extraPlayerHealth")*(players-1));
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);mob.setHealth((float)health);
            }else{
                mob.getPersistentData().putLong("zr_minion_spawner",origin.asLong());
                if(mob.isNapalm()){
                    var config=me.cryo.zombierool.config.WorldConfig.get(level);int wave=WaveManager.getCurrentWave();
                    double normal=config.getZombieBaseHealth();if(wave>=2){normal+=Math.min(9,wave)*4;if(wave>9)normal*=Math.pow(1.15,wave-9);normal=Math.min(normal,config.getZombieMaxHealth());}
                    double health=normal*settings.getDouble("napalmHealth")*Math.max(1,level.players().stream().filter(p->!p.isCreative()&&!p.isSpectator()).count());
                    mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);mob.setHealth((float)health);mob.markNapalmSpawnWave(wave);
                }
            }
            mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(boss&&!profile.isEmpty()?profile.getDouble("speed"):settings.getDouble(mob.isBoss()?(mob.isPanzer()?"panzerSpeed":"brutusSpeed"):mob.isNapalm()?"napalmSpeed":"skeletonSpeed"));
            if(!boss&&!mob.isNapalm()){mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(settings.getDouble("skeletonHealth"));mob.setHealth((float)settings.getDouble("skeletonHealth"));}
            mob.getPersistentData().putBoolean("zr_special_spawner",true);
            if(mob.isPanzer())mob.beginDescent();
            else if(mob.isBoss()){mob.playBossAnimation("spawn",50);mob.setEmergenceTicks(12);}
            SpawnRedistribution.track(mob,level,origin);
            boolean added=level.addFreshEntity(mob);
            if(added&&!boss)for(var p:level.players())if(p.distanceToSqr(mob)<144)me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new me.cryo.zombierool.network.packet.S2CScreenShakePacket(10,.55f,me.cryo.zombierool.client.ScreenShakeHandler.ShakeType.IMPACT));
            if(added&&mob.legacyBoss()&&!mob.isPanzer()){level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION,mob.getX(),mob.getY()+.2,mob.getZ(),8,.8,.2,.8,0);for(var p:level.players())if(p.distanceToSqr(mob)<400)me.cryo.zombierool.network.NetworkHandler.INSTANCE.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(()->p),new me.cryo.zombierool.network.packet.S2CScreenShakePacket(24,2.5f,me.cryo.zombierool.client.ScreenShakeHandler.ShakeType.IMPACT));}
            if(added&&mob.isNapalm()&&!mob.isInWater()){var pool=ZombieroolModEntities.NAPALM_FIRE.get().create(level);if(pool!=null){pool.setPos(mob.position());pool.setDecorative(24);level.addFreshEntity(pool);}level.sendParticles(net.minecraft.core.particles.ParticleTypes.FLAME,mob.getX(),mob.getY()+.2,mob.getZ(),70,.7,.15,.7,.06);}
            if(added&&mob.isNapalm())level.playSound(null,mob.blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_SPAWN.get(),net.minecraft.sounds.SoundSource.HOSTILE,1,1);
            if(added&&boss&&!mob.isEncounter()){var sound=mob.isPanzer()?me.cryo.zombierool.init.ZombieroolModSounds.PANZER_SPAWN.get():me.cryo.zombierool.init.ZombieroolModSounds.BRUTUS_SPAWN.get();for(var player:level.players())player.connection.send(new net.minecraft.network.protocol.game.ClientboundSoundPacket(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.wrapAsHolder(sound),net.minecraft.sounds.SoundSource.HOSTILE,player.getX(),player.getY(),player.getZ(),1,1,level.random.nextLong()));}
            return added;
        }
        return false;
    }
}
