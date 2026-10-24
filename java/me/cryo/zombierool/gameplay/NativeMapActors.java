package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.entity.*;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Map encounters reference the real registered enemies, including their renderer and source motion. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class NativeMapActors {
    public static boolean owns(Entity entity){return entity!=null&&entity.getPersistentData().contains("MapNativeProfile",10);}
    public static CompoundTag profile(Entity entity){return entity.getPersistentData().getCompound("MapNativeProfile");}
    public static EntityType<?> type(String base){return switch(base){case "abomination","george_romero","astronaut"->ZombieroolModEntities.CUSTOM_MINI_BOSS.get();case "geist"->ZombieroolModEntities.GEIST.get();case "crawler"->ZombieroolModEntities.CRAWLER.get();case "hellhound"->ZombieroolModEntities.HELLHOUND.get();case "white_knight"->ZombieroolModEntities.WHITE_KNIGHT.get();case "panzer"->ZombieroolModEntities.PANZER.get();case "brutus"->ZombieroolModEntities.BRUTUS.get();case "pentagon_thief"->ZombieroolModEntities.PENTAGON_THIEF.get();case "napalm_minion"->ZombieroolModEntities.NAPALM_MINION.get();case "skeleton_minion"->ZombieroolModEntities.SKELETON_MINION.get();default->ZombieroolModEntities.ZOMBIE.get();};}
    public static String spawn(ServerLevel level,CompoundTag profile,Vec3 at,String marker){
        if(ActorPopulation.alive(level)>=ActorPopulation.MAX_ALIVE)return "";
        String base=profile.getString("baseEntity");
        var type=type(base);
        PathfinderMob mob=(PathfinderMob)type.create(level);if(mob==null)return "";
        if(java.util.Set.of("abomination","george_romero","astronaut").contains(base))((SpecialEnemyEntity)mob).setBossProfile(me.cryo.zombierool.config.MiniBossDefinitions.get("zombierool:"+base));
        if(mob instanceof ZombieEntity zombie){
            var variants=me.cryo.zombierool.config.EnemySkinPresets.catalog().getAsJsonObject("zombies").getAsJsonObject("scourge").getAsJsonArray("variants");
            for(int i=0;i<variants.size();i++)if(variants.get(i).getAsJsonObject().get("model").getAsString().equals(base)){zombie.setCustomSkin("@preset/zombies/scourge/"+i);break;}
            zombie.mapOutfit(profile);
        }
        var state=mob.getPersistentData();state.put("MapNativeProfile",profile.copy());state.putDouble("MapHomeX",at.x);state.putDouble("MapHomeY",at.y);state.putDouble("MapHomeZ",at.z);state.putString("MapMarker",marker);state.putBoolean("matchActor",WaveManager.isGameRunning());
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(profile.getDouble("health"));mob.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(MapActorBehavior.speed(profile,level));mob.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(profile.getDouble("damage"));mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(profile.getBoolean("boss")?MapActors.displayName(profile):null);mob.setCustomNameVisible(false);mob.moveTo(at.x,at.y,at.z,level.random.nextFloat()*360,0);
        installLocalGoals(mob);if(!level.noCollision(mob)||!level.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(.15,0,.15),e->e.isAlive()).isEmpty()||!level.addFreshEntity(mob))return "";
        if(profile.getBoolean("countsForWave")&&WaveManager.isCombatWave())WaveManager.registerMapActor(mob);
        ActorPopulation.spawned(mob);callback(mob,"spawn");
        return mob.getUUID().toString();
    }
    private static void installLocalGoals(Mob mob){mob.targetSelector.removeAllGoals(goal->true);mob.goalSelector.removeAllGoals(goal->goal instanceof MeleeAttackGoal||goal instanceof RandomStrollGoal);mob.goalSelector.addGoal(2,new MeleeAttackGoal((PathfinderMob)mob,1,true){@Override public boolean canUse(){return !WaveManager.isPausedByPlayer()&&mob.getPersistentData().getLong("MapCastUntil")<=mob.level().getGameTime()&&super.canUse();}@Override public boolean canContinueToUse(){return !WaveManager.isPausedByPlayer()&&mob.getPersistentData().getLong("MapCastUntil")<=mob.level().getGameTime()&&super.canContinueToUse();}@Override protected void checkAndPerformAttack(LivingEntity target,double distance){var p=profile(mob);var state=mob.getPersistentData();if(MapActorBehavior.inMeleeRange(mob,target,p.getDouble("meleeReach"))&&state.getLong("MapNextMelee")<=mob.level().getGameTime()){state.putLong("MapNextMelee",mob.level().getGameTime()+p.getInt("meleeCooldown"));resetAttackCooldown();mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);mob.doHurtTarget(target);}}@Override protected double getAttackReachSqr(LivingEntity target){return Math.pow(mob.getBbWidth()/2+target.getBbWidth()/2+profile(mob).getDouble("meleeReach"),2);}});mob.getPersistentData().putBoolean("mapGoalsInstalled",true);}
    private static void callback(Mob mob,String event){String prefix=profile(mob).getString("callback");var state=mob.getPersistentData();if(!prefix.isEmpty()&&state.getInt("mapScriptErrors")<3&&mob.level() instanceof ServerLevel level&&me.cryo.zombierool.scripting.LuaScriptManager.ready(level)&&!me.cryo.zombierool.scripting.LuaScriptManager.actorCallback(prefix+"_"+event,mob.getUUID().toString()))state.putInt("mapScriptErrors",state.getInt("mapScriptErrors")+1);}
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event){if(event.getEntity() instanceof Mob mob&&owns(mob)&&!mob.level().isClientSide)callback(mob,"death");}
    @SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent event){if(!event.getLevel().isClientSide&&event.getEntity() instanceof Mob mob&&owns(mob)){var p=profile(mob);if(event.getLevel() instanceof ServerLevel level){var latest=MapActors.get(level).profile(p.getString("id"));if(!latest.isEmpty()){for(String key:MapActorBehavior.BEHAVIOR_FIELDS)if(latest.contains(key))p.put(key,latest.get(key).copy());if(latest.contains("armorBoxes"))p.put("armorBoxes",latest.get("armorBoxes").copy());if(latest.contains("knockbackResistance"))p.putDouble("knockbackResistance",latest.getDouble("knockbackResistance"));}String marker=mob.getPersistentData().getString("MapMarker");if(!marker.isEmpty()){var rule=MapActors.get(level).marker(net.minecraft.core.BlockPos.of(Long.parseLong(marker)));for(String key:MapActorBehavior.BEHAVIOR_FIELDS)if(rule.contains(key))p.put(key,rule.get(key).copy());p.putDouble("knockbackResistance",1);}var attr=mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);p.putDouble("knockbackResistance",1);if(attr!=null)attr.setBaseValue(1);mob.setCustomName(p.getBoolean("boss")?MapActors.displayName(p):null);var movement=mob.getAttribute(Attributes.MOVEMENT_SPEED);if(movement!=null)movement.setBaseValue(MapActorBehavior.speed(p,level));if(mob instanceof ZombieEntity z)z.mapOutfit(p);}installLocalGoals(mob);}}
    @SubscribeEvent public static void tick(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event){
        if(!(event.getEntity() instanceof Mob mob)||!(mob.level() instanceof ServerLevel level)||!owns(mob)||!mob.isAlive()||mob.isNoAi()&&!(mob instanceof AbstractZombieRoolEntity emerging&&emerging.getEmergenceTicks()>0))return;
        var state=mob.getPersistentData();var p=profile(mob);
        if(state.getBoolean("matchActor")&&!WaveManager.isGameRunning()){mob.discard();return;}
        if(WaveManager.isPausedByPlayer()){mob.getNavigation().stop();mob.setTarget(null);return;}
        if(mob.tickCount%2!=0)return;
        if(mob.tickCount%10==0)callback(mob,"tick");
        if(p.getString("behavior").equals("script"))return;
        Vec3 home=new Vec3(state.getDouble("MapHomeX"),state.getDouble("MapHomeY"),state.getDouble("MapHomeZ"));double radius=p.getDouble("aggroRadius"),leash=MapActorBehavior.boundary(p);
        if(leash>0&&mob.position().distanceToSqr(home)>leash*leash){mob.setTarget(null);state.remove("MapRetaliation");mob.getNavigation().moveTo(home.x,home.y,home.z,1);return;}
        String behavior=p.getString("behavior");var previous=mob.getTarget();mob.setTarget(MapActorBehavior.target(mob,p,home));if(previous!=mob.getTarget())mob.getNavigation().stop();if(mob.isNoAi())return;
        if(mob.getTarget()!=null&&state.getLong("MapCastUntil")<=level.getGameTime()){if(MapActorBehavior.inMeleeRange(mob,mob.getTarget(),p.getDouble("meleeReach"))){if(state.getLong("MapNextMelee")<=level.getGameTime()){state.putLong("MapNextMelee",level.getGameTime()+p.getInt("meleeCooldown"));mob.swing(net.minecraft.world.InteractionHand.MAIN_HAND);mob.doHurtTarget(mob.getTarget());}}else if(mob.tickCount%10==0||mob.getNavigation().isDone())mob.getNavigation().moveTo(mob.getTarget(),1);}
        if(Math.floorMod(mob.tickCount/2+mob.getId(),10)==0&&state.getLong("MapCastUntil")<=level.getGameTime())MapActorBehavior.requestPatrol(mob,p);
    }
    private NativeMapActors(){}
}
