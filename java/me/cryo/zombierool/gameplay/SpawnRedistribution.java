package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem.*;
import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.Vec3;

/** Relocates living enemies without deaths, rewards, or changing the wave budget. */
public final class SpawnRedistribution {
    private static final String ORIGIN="zr_spawn_origin",TYPE="zr_spawn_type";
    private record Pending(CompoundTag entity,SpawnerMobType type,boolean counted,boolean boss) {}
    private static final Map<ServerLevel,List<Pending>> QUEUES=new WeakHashMap<>();
    private static final Map<ServerLevel,Long> NEXT=new WeakHashMap<>();
    private SpawnRedistribution(){}

    public static void track(Mob mob,ServerLevel level,BlockPos origin){
        mob.getPersistentData().putLong(ORIGIN,origin.asLong());
        if(level.getBlockEntity(origin) instanceof UniversalSpawnerBlockEntity spawner)
            mob.getPersistentData().putString(TYPE,spawner.getMobType().name());
    }
    public static Map<ServerLevel,Set<BlockPos>> activeSpawners(MinecraftServer server){
        Map<ServerLevel,Set<BlockPos>> result=new HashMap<>();
        for(ServerLevel level:server.getAllLevels()){
            // A traveler can leave its origin chunk behind; its spawner still matters.
            Set<BlockPos> origins=new HashSet<>();
            for(Entity entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()){
                var data=mob.getPersistentData();String key=data.contains(ORIGIN)?ORIGIN:data.contains("zr_minion_spawner")?"zr_minion_spawner":null;
                if(key!=null)origins.add(BlockPos.of(data.getLong(key)));
            }
            for(BlockPos origin:origins)level.getChunkAt(origin);
            Set<BlockPos> positions=new HashSet<>();
            for(var spawner:SpawnerRegistry.getSpawners(level))if(spawner.getMobType()!=SpawnerMobType.PLAYER&&spawner.isActive(level))positions.add(spawner.getBlockPos());
            result.put(level,positions);
        }
        return result;
    }
    public static void redistribute(Map<ServerLevel,Set<BlockPos>> before){
        if(!WaveManager.isGameRunning())return;
        for(var entry:before.entrySet()){
            ServerLevel level=entry.getKey();Set<BlockPos> disabled=new HashSet<>(entry.getValue());
            for(var spawner:SpawnerRegistry.getSpawners(level))if(spawner.isActive(level))disabled.remove(spawner.getBlockPos());
            if(disabled.isEmpty())continue;
            List<Mob> victims=new ArrayList<>();
            for(Entity entity:level.getAllEntities())if(entity instanceof Mob mob&&mob.isAlive()){
                var data=mob.getPersistentData();
                String key=data.contains(ORIGIN)?ORIGIN:data.contains("zr_minion_spawner")?"zr_minion_spawner":null;
                if(key!=null&&disabled.contains(BlockPos.of(data.getLong(key))))victims.add(mob);
            }
            for(Mob mob:victims){
                var data=mob.getPersistentData();SpawnerMobType type;
                try{type=SpawnerMobType.valueOf(data.getString(TYPE));}catch(Exception ignored){
                    BlockPos origin=BlockPos.of(data.getLong(data.contains(ORIGIN)?ORIGIN:"zr_minion_spawner"));
                    type=level.getBlockEntity(origin) instanceof UniversalSpawnerBlockEntity spawner?spawner.getMobType():mob instanceof CrawlerEntity?SpawnerMobType.CRAWLER:mob instanceof HellhoundEntity?SpawnerMobType.HELLHOUND:SpawnerMobType.ZOMBIE;
                }
                // A route owns NoAI/noGravity temporarily. Do not serialize that lock into a new mob.
                TraversalMotion.cancel(mob);
                // Clear transient capture/descent before saving a relocated boss.
                if(mob instanceof SpecialEnemyEntity special)special.releaseCapture();
                CompoundTag snapshot=new CompoundTag();if(!mob.save(snapshot))continue;
                snapshot.remove("UUID");snapshot.remove("Passengers");snapshot.putInt("DescentTicks",0);snapshot.putBoolean("NoGravity",false);
                if(data.contains("zr_emerge_no_ai"))snapshot.putBoolean("NoAI",data.getBoolean("zr_emerge_no_ai"));
                boolean counted=WaveManager.detachForRedistribution(mob);
                QUEUES.computeIfAbsent(level,k->new ArrayList<>()).add(new Pending(snapshot,type,counted,mob instanceof SpecialEnemyEntity s&&s.isBoss()));
                me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager.cleanupEntity(mob.getId());
                mob.discard();
            }
            NEXT.put(level,level.getGameTime()+10);
        }
    }
    public static int pendingCount(ServerLevel level){return (int)QUEUES.getOrDefault(level,List.of()).stream().filter(p->p.counted).count();}
    public static boolean pending(ServerLevel level){return QUEUES.getOrDefault(level,List.of()).stream().anyMatch(p->p.counted||p.boss&&!java.util.Set.of("george_romero","astronaut").contains(p.entity.getCompound("MiniBossProfile").getString("template")));}
    public static long pendingRoundBosses(ServerLevel level){return QUEUES.getOrDefault(level,List.of()).stream().filter(p->p.boss&&!java.util.Set.of("george_romero","astronaut").contains(p.entity.getCompound("MiniBossProfile").getString("template"))).count();}
    public static long pendingBosses(ServerLevel level){return QUEUES.getOrDefault(level,List.of()).stream().filter(Pending::boss).count();}
    public static long pendingType(ServerLevel level,SpawnerMobType type){return QUEUES.getOrDefault(level,List.of()).stream().filter(p->p.entity.getString("id").equals("zombierool:"+type.getSerializedName())).count();}
    public static Runnable suspend(ServerLevel level){var queue=QUEUES.remove(level);NEXT.remove(level);return ()->{if(queue!=null&&!queue.isEmpty()){QUEUES.put(level,queue);NEXT.put(level,level.getGameTime()+10);}};}
    public static void reset(){QUEUES.clear();NEXT.clear();}

    /** Do not let the first enemy's emergence block the rest of a transfer indefinitely. */
    private static boolean placeNearSpawner(ServerLevel level,BlockPos origin,Mob mob){
        for(int radius=0;radius<=2;radius++)for(int dx=-radius;dx<=radius;dx++)for(int dz=-radius;dz<=radius;dz++){
            if(Math.max(Math.abs(dx),Math.abs(dz))!=radius)continue;
            BlockPos pos=origin.offset(dx,0,dz);Vec3 at=Vec3.atBottomCenterOf(pos);mob.moveTo(at.x,at.y,at.z,level.random.nextFloat()*360,0);
            if(!level.getWorldBorder().isWithinBounds(mob.getBoundingBox())||!level.noCollision(mob))continue;
            if(!level.getEntitiesOfClass(Mob.class,mob.getBoundingBox().inflate(.05),e->e.isAlive()).isEmpty())continue;
            if(level.getBlockState(pos.below()).getCollisionShape(level,pos.below()).isEmpty()&&!level.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER))continue;
            var evaluator=new RouteNodeEvaluator();var region=new net.minecraft.world.level.PathNavigationRegion(level,pos.offset(-3,-2,-3),pos.offset(3,4,3));evaluator.prepare(region,mob);
            net.minecraft.world.level.pathfinder.BlockPathTypes type;
            try{type=evaluator.getBlockPathType(region,pos.getX(),pos.getY(),pos.getZ(),mob);}finally{evaluator.done();}
            if(type==net.minecraft.world.level.pathfinder.BlockPathTypes.BLOCKED||mob.getPathfindingMalus(type)<0)continue;
            return true;
        }
        return false;
    }
    public static void tick(ServerLevel level){
        if(QUEUES.getOrDefault(level,List.of()).isEmpty()||!WaveManager.isCombatWave()||level.getGameTime()<NEXT.getOrDefault(level,0L))return;
        NEXT.put(level,level.getGameTime()+10); // One replacement every half second.
        var queue=QUEUES.get(level);
        for(Pending next:List.copyOf(queue)){
            if(next.counted&&!WaveManager.hasRedistributionCapacity(level))continue;
            var active=SpawnerRegistry.getSpawners(level).stream().filter(s->s.getMobType()!=SpawnerMobType.PLAYER&&s.isActive(level)).toList();
            var pool=new ArrayList<>(active.stream().filter(s->s.getMobType()==next.type).toList());
            if(pool.isEmpty())pool.addAll(active.stream().filter(s->s.getMobType()==SpawnerMobType.ZOMBIE).toList());
            Collections.shuffle(pool);
            for(var spawner:pool){
                Entity entity=EntityType.loadEntityRecursive(next.entity.copy(),level,e->e);
                if(!(entity instanceof Mob mob))break;
                if(!placeNearSpawner(level,spawner.getBlockPos(),mob))continue;
                mob.setDeltaMovement(Vec3.ZERO);mob.fallDistance=0;
                track(mob,level,spawner.getBlockPos());
                // Preserve the enemy type even when a zombie spawner receives it.
                mob.getPersistentData().putString(TYPE,next.type.name());
                if(mob instanceof SpecialEnemyEntity special&&!special.isBoss())mob.getPersistentData().putLong("zr_minion_spawner",spawner.getBlockPos().asLong());
                if(!level.addFreshEntity(mob))continue;
                if(next.counted)WaveManager.attachAfterRedistribution(mob);
                queue.remove(next);if(queue.isEmpty()){QUEUES.remove(level);NEXT.remove(level);}return;
            }
        }
    }
}
