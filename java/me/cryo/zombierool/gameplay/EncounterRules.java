package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.config.SpecialEnemyConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import java.util.*;

@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class EncounterRules {
    private static final Map<ServerLevel,Integer> ROMERO_RETURN=new WeakHashMap<>();
    public static boolean thief(ServerLevel level){return SpecialEnemyConfig.get(level).getString("specialWaveEnemy").equals("pentagon_thief");}
    public static boolean geist(ServerLevel level){return SpecialEnemyConfig.get(level).getString("crawlerVariant").equals("geist");}
    public static boolean special(net.minecraft.world.entity.Entity e){return e instanceof HellhoundEntity||e instanceof SpecialEnemyEntity s&&EncounterAbilities.kind(s).equals("pentagon_thief");}
    public static boolean roundIndependent(net.minecraft.world.entity.Entity e){if(MapActors.owns(e))return !MapActors.countsForWave(e);return e instanceof SpecialEnemyEntity s&&java.util.Set.of("astronaut","george_romero").contains(EncounterAbilities.kind(s));}
    public static boolean persistent(SpecialEnemyEntity e){return EncounterAbilities.kind(e).equals("george_romero");}
    public static boolean romeroDue(ServerLevel level,int wave){return SpawnRedistribution.pendingBosses(level)==0&&wave>=ROMERO_RETURN.getOrDefault(level,0)&&java.util.stream.StreamSupport.stream(level.getAllEntities().spliterator(),false).noneMatch(e->e instanceof SpecialEnemyEntity s&&persistent(s)&&s.isAlive());}
    public static void reset(ServerLevel level){ROMERO_RETURN.remove(level);}
    public static void enforce(ServerLevel level){var victims=new ArrayList<net.minecraft.world.entity.Mob>();for(var e:level.getAllEntities())if(!NativeMapActors.owns(e)&&(e instanceof HellhoundEntity&&thief(level)||e instanceof SpecialEnemyEntity s&&EncounterAbilities.kind(s).equals("pentagon_thief")&&!thief(level)))victims.add((net.minecraft.world.entity.Mob)e);for(var e:victims){WaveManager.onMobDeath(e,level);e.discard();}}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.LevelTickEvent e){if(e.phase==net.minecraftforge.event.TickEvent.Phase.END&&e.level instanceof ServerLevel level&&level.getGameTime()%20==0)enforce(level);}
    @SubscribeEvent public static void join(net.minecraftforge.event.entity.EntityJoinLevelEvent e){
        if(NativeMapActors.owns(e.getEntity()))return;
        if(e.getLevel() instanceof ServerLevel level&&e.getEntity() instanceof HellhoundEntity&&thief(level))e.setCanceled(true);
        if(e.getLevel() instanceof ServerLevel level&&e.getEntity() instanceof SpecialEnemyEntity s&&EncounterAbilities.kind(s).equals("pentagon_thief")&&!thief(level))e.setCanceled(true);
        if(!e.isCanceled()&&e.getLevel() instanceof ServerLevel level&&(e.getEntity() instanceof HellhoundEntity||e.getEntity() instanceof SpecialEnemyEntity s&&EncounterAbilities.kind(s).equals("pentagon_thief")))enforce(level);
    }
    @SubscribeEvent public static void died(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(e.getEntity() instanceof SpecialEnemyEntity s&&persistent(s)&&s.level() instanceof ServerLevel level)ROMERO_RETURN.put(level,WaveManager.getCurrentWave()+SpecialEnemyConfig.get(level).getInt("romeroReturnRounds"));}
    @SubscribeEvent public static void contact(net.minecraftforge.event.entity.living.LivingHurtEvent e){if(e.getEntity() instanceof net.minecraft.server.level.ServerPlayer p&&e.getSource().getEntity() instanceof ZombieEntity z&&z.getPersistentData().getLong("zr_electrified_until")>p.level().getGameTime()){
        EncounterAbilities.shockPlayer(p,24);p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,30,1));
        ((ServerLevel)p.level()).sendParticles(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,p.getX(),p.getY()+1,p.getZ(),6,.3,.4,.3,.02);
    }}
    private EncounterRules(){}
}
