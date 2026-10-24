package me.cryo.zombierool.entity;

import me.cryo.zombierool.gameplay.*;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.nbt.*;
import net.minecraft.network.syncher.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** One generic engine; appearance, balance and Lua behaviors belong to the save. */
public final class MapActorEntity extends PathfinderMob {
    @Override public boolean isPushable(){return isAlive()&&super.isPushable();}
    @Override public boolean canBeCollidedWith(){return isAlive()&&super.canBeCollidedWith();}
    @Override public boolean canCollideWith(Entity other){return isAlive()&&super.canCollideWith(other);}
    @Override protected void doPush(Entity other){if(isAlive()&&(!(other instanceof LivingEntity living)||living.isAlive()))super.doPush(other);}
    @Override public void push(Entity other){if(isAlive()&&(!(other instanceof LivingEntity living)||living.isAlive()))super.push(other);}
    @Override protected void pushEntities(){if(isAlive())super.pushEntities();}
    private static final EntityDataAccessor<CompoundTag> PROFILE=SynchedEntityData.defineId(MapActorEntity.class,EntityDataSerializers.COMPOUND_TAG);
    private static final EntityDataAccessor<Integer> CAST=SynchedEntityData.defineId(MapActorEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> ANIMATION=SynchedEntityData.defineId(MapActorEntity.class,EntityDataSerializers.STRING);
    private int castAnimationStart;
    private int customAnimationStart;
    private CompoundTag visualProfile;
    private String visualKey="";
    private Vec3 home=Vec3.ZERO;
    private String marker="";
    private final net.minecraft.server.level.ServerBossEvent bar=new ServerBossEvent(net.minecraft.network.chat.Component.empty(),net.minecraft.world.BossEvent.BossBarColor.PURPLE,net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
    private int scriptFailures;
    private int nextMelee;
    public MapActorEntity(EntityType<? extends MapActorEntity> type,Level level) {super(type,level);setPersistenceRequired();setMaxUpStep(1.25f);moveControl=new me.cryo.zombierool.gameplay.DoorwayMoveControl(this);if(getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation nav){nav.setCanOpenDoors(true);nav.setCanFloat(false);nav.setMaxVisitedNodesMultiplier(4);}}
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level){return new me.cryo.zombierool.gameplay.RouteNavigation(this,level);}
    @Override public void knockback(double strength,double x,double z){if(profile().getDouble("knockbackResistance")<1)super.knockback(strength,x,z);}
    public boolean inMeleeRange(net.minecraft.world.entity.LivingEntity target){return MapActorBehavior.inMeleeRange(this,target,profile().getDouble("meleeReach"));}
    private boolean ranged(){return java.util.List.of("archer","mage").contains(profile().getString("combatRole"));}
    @Override public boolean doHurtTarget(net.minecraft.world.entity.Entity target){if(tickCount<nextMelee||!(target instanceof net.minecraft.world.entity.LivingEntity victim)||!MapActorBehavior.eligible(this,victim,128)||!inMeleeRange(victim))return false;boolean hit=victim instanceof ServerPlayer player?me.cryo.zombierool.gameplay.FixedPlayerDamage.mobHit(player,this,(float)getAttributeValue(Attributes.ATTACK_DAMAGE)):victim.hurt(damageSources().mobAttack(this),(float)getAttributeValue(Attributes.ATTACK_DAMAGE));if(hit){nextMelee=tickCount+(profile().contains("meleeCooldown")?profile().getInt("meleeCooldown"):20);me.cryo.zombierool.gameplay.MapActorAudio.play(this,"attack");if(!victim.isAlive()||victim instanceof ServerPlayer player&&me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID()))me.cryo.zombierool.gameplay.MapActorAudio.play(this,"slay");}return hit;}
    public static AttributeSupplier.Builder attributes() {return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,40).add(Attributes.MOVEMENT_SPEED,.23).add(Attributes.ATTACK_DAMAGE,3).add(Attributes.FOLLOW_RANGE,128).add(Attributes.KNOCKBACK_RESISTANCE,.15);}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(PROFILE,new CompoundTag());entityData.define(CAST,0);entityData.define(ANIMATION,"");}
    public CompoundTag profile(){return entityData.get(PROFILE);}
    @Override public MobType getMobType(){return profile().getBoolean("undead")?MobType.UNDEAD:MobType.UNDEFINED;}
    public String visualKey(){if(visualProfile!=profile()){visualProfile=profile();try{visualKey=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(visualProfile.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(java.security.NoSuchAlgorithmException e){throw new IllegalStateException(e);}}return visualKey;}
    public String customAnimation(){return entityData.get(ANIMATION);}
    public float customAnimationAge(float age){return age-customAnimationStart;}
    public boolean playAnimation(String clip){if(!clip.isEmpty()&&!profile().getCompound("animations").contains(clip))return false;entityData.set(ANIMATION,clip);customAnimationStart=tickCount;var n=profile().getCompound("animations").getCompound(clip);getPersistentData().putLong("animationUntil",n.getBoolean("loop")?Long.MAX_VALUE:level().getGameTime()+(int)n.getDouble("duration"));return true;}
    public String marker(){return marker;}
    public Vec3 home(){return home;}
    public void applyBehaviorSettings(CompoundTag settings){var p=profile().copy();for(String key:MapActorBehavior.BEHAVIOR_FIELDS)if(settings.contains(key))p.put(key,settings.get(key).copy());entityData.set(PROFILE,p);if(level() instanceof ServerLevel level)getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(MapActorBehavior.speed(p,level));getNavigation().stop();}
    public boolean casting(){return level().isClientSide?entityData.get(CAST)>0:getPersistentData().getLong("castUntil")>level().getGameTime();}
    public float castAnimationAge(float age){return age-castAnimationStart;}
    public void setCastingTicks(int ticks){getPersistentData().putLong("castUntil",level().getGameTime()+ticks);entityData.set(CAST,ticks);castAnimationStart=tickCount;if(ticks>0)me.cryo.zombierool.gameplay.MapActorAudio.play(this,"cast");}
    public void configure(CompoundTag p,Vec3 home,String marker) {
        entityData.set(PROFILE,p.copy());this.home=home;this.marker=marker;
        getPersistentData().putBoolean("zr_boss",p.getBoolean("boss"));getPersistentData().putBoolean("zombierool:no_gore",true);
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(p.getDouble("health"));getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(level() instanceof ServerLevel server?MapActorBehavior.speed(p,server):p.getDouble("speed"));getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(p.getDouble("damage"));
        getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        setHealth(getMaxHealth());setCustomName(p.getBoolean("boss")?me.cryo.zombierool.gameplay.MapActors.displayName(p):null);bar.setName(getDisplayName());refreshDimensions();
    }
    @Override public EntityDimensions getDimensions(Pose pose) {
        var p=profile();return p.isEmpty()?super.getDimensions(pose):EntityDimensions.scalable((float)(p.getDouble("width")*p.getDouble("scale")),(float)(p.getDouble("height")*p.getDouble("scale")));
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(PROFILE.equals(key))refreshDimensions();if(CAST.equals(key))castAnimationStart=tickCount;if(ANIMATION.equals(key))customAnimationStart=tickCount;}
    @Override protected void registerGoals() {
        goalSelector.addGoal(0,new FloatGoal(this));
        goalSelector.addGoal(2,new MeleeAttackGoal(this,1,true){@Override public boolean canUse(){return !WaveManager.isPausedByPlayer()&&!casting()&&!profile().getString("behavior").equals("script")&&(!ranged()||getTarget()!=null&&inMeleeRange(getTarget()))&&super.canUse();}@Override public boolean canContinueToUse(){return !WaveManager.isPausedByPlayer()&&!casting()&&(!ranged()||getTarget()!=null&&inMeleeRange(getTarget()))&&super.canContinueToUse();}@Override protected void checkAndPerformAttack(net.minecraft.world.entity.LivingEntity target,double distance){if(inMeleeRange(target)&&isTimeToAttack()){resetAttackCooldown();swing(net.minecraft.world.InteractionHand.MAIN_HAND);doHurtTarget(getTarget());}}@Override protected double getAttackReachSqr(net.minecraft.world.entity.LivingEntity target){return Math.pow(getBbWidth()/2+target.getBbWidth()/2+profile().getDouble("meleeReach"),2);}});
        goalSelector.addGoal(7,new RandomLookAroundGoal(this));
    }
    private boolean eligible(ServerPlayer p) {return p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID());}
    @Override protected void customServerAiStep() {
        super.customServerAiStep();if(!isAlive()||profile().isEmpty())return;
        if(!WaveManager.isPausedByPlayer()&&getTarget()!=null&&MapActorBehavior.eligible(this,getTarget(),128)&&inMeleeRange(getTarget())&&tickCount>=nextMelee){getNavigation().stop();setCastingTicks(0);swing(net.minecraft.world.InteractionHand.MAIN_HAND);doHurtTarget(getTarget());}
        if(!casting()&&entityData.get(CAST)>0)entityData.set(CAST,0);
        if(!customAnimation().isEmpty()&&getPersistentData().getLong("animationUntil")<=level().getGameTime())entityData.set(ANIMATION,"");
        if(getPersistentData().getBoolean("matchActor")&&!WaveManager.isGameRunning()){discard();return;}
        if(WaveManager.isPausedByPlayer()){getNavigation().stop();setTarget(null);return;}
        if(tickCount%2!=0)return;
        var p=profile();String behavior=p.getString("behavior");
        if(casting()){getNavigation().stop();if(tickCount%10==0)callback("tick");return;}
        if(!behavior.equals("script")) {
            double radius=p.getDouble("aggroRadius"),leash=MapActorBehavior.boundary(p);
            boolean homebound=leash>0&&position().distanceToSqr(home)>leash*leash;
            if(homebound){setTarget(null);getPersistentData().remove("MapRetaliation");getNavigation().moveTo(home.x,home.y,home.z,1);}
            else {
                var target=MapActorBehavior.target(this,p,home);
                if(target!=null&&getTarget()==null)me.cryo.zombierool.gameplay.MapActorAudio.play(this,"aggro");
                setTarget(target);
                // A spell stops navigation. Refresh pursuit explicitly after it, including
                // paths to targets which have left detection range or broken line of sight.
                if(target!=null){
                    getLookControl().setLookAt(target,30,30);
                    if(ranged()){float facing=(float)(Math.atan2(target.getZ()-getZ(),target.getX()-getX())*180/Math.PI)-90;setYRot(net.minecraft.util.Mth.rotLerp(.45f,getYRot(),facing));setYBodyRot(getYRot());}
                    if(ranged()&&distanceToSqr(target)<=p.getDouble("preferredRange")*p.getDouble("preferredRange")&&MapActorBehavior.visible(this,target))getNavigation().stop();
                    else if(!inMeleeRange(target))getNavigation().moveTo(target,1);
                }
                if(p.getBoolean("smokyEyes")&&tickCount%10==0&&level() instanceof ServerLevel server)server.sendParticles(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(.45f,.95f,.18f),.45f),getX(),getEyeY(),getZ()-.15,2,.08,.05,.08,.005);
                if(Math.floorMod(tickCount/2+getId(),10)==0)MapActorBehavior.requestPatrol(this,p);
            }
        }
        if(p.getBoolean("boss")){bar.setProgress(net.minecraft.util.Mth.clamp(getHealth()/getMaxHealth(),0,1));for(var player:((ServerLevel)level()).players())if(eligible(player)&&distanceToSqr(player)<96*96)bar.addPlayer(player);else bar.removePlayer(player);}
        if(tickCount%10==0)callback("tick");
    }
    public void callback(String event) {
        String prefix=profile().getString("callback");if(prefix.isEmpty()||scriptFailures>=3||!(level() instanceof ServerLevel server)||!LuaScriptManager.ready(server))return;
        if(!LuaScriptManager.actorCallback(prefix+"_"+event,getUUID().toString()))scriptFailures++;
    }
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float damage){boolean hit=super.hurt(source,damage);if(hit&&isAlive()&&me.cryo.zombierool.gameplay.EncounterAudio.allowHurt(this))me.cryo.zombierool.gameplay.MapActorAudio.play(this,"hurt");return hit;}
    @Override public void die(net.minecraft.world.damagesource.DamageSource source){super.die(source);if(!level().isClientSide){getNavigation().stop();setDeltaMovement(Vec3.ZERO);bar.removeAllPlayers();me.cryo.zombierool.gameplay.MapActorAudio.play(this,"death");callback("death");WaveManager.onMobDeath(this,(ServerLevel)level());}}
    @Override protected void tickDeath(){var death=profile().getCompound("animations").getCompound("death");int duration=death.isEmpty()?19:Math.max(1,(int)Math.ceil(death.getDouble("duration")));deathTime=Math.min(deathTime+1,duration);setDeltaMovement(0,getDeltaMovement().y,0);}

    @Override public void remove(RemovalReason reason){bar.removeAllPlayers();super.remove(reason);}
    @Override public void startSeenByPlayer(ServerPlayer player){super.startSeenByPlayer(player);}
    @Override public void stopSeenByPlayer(ServerPlayer player){super.stopSeenByPlayer(player);bar.removePlayer(player);}
    @Override public void addAdditionalSaveData(CompoundTag n){super.addAdditionalSaveData(n);n.putInt("MapDeathTime",deathTime);n.put("MapProfile",profile().copy());n.putDouble("HomeX",home.x);n.putDouble("HomeY",home.y);n.putDouble("HomeZ",home.z);n.putString("MapMarker",marker);}
    @Override public void readAdditionalSaveData(CompoundTag n){super.readAdditionalSaveData(n);deathTime=n.getInt("MapDeathTime");var p=n.getCompound("MapProfile").copy();if(level() instanceof ServerLevel level){var latest=me.cryo.zombierool.gameplay.MapActors.get(level).profile(p.getString("id"));if(!latest.isEmpty()){int choice=p.getString("id").endsWith("_female")&&!latest.getString("id").equals(p.getString("id"))?1:p.getInt("appearance");latest=MapActors.appearance(latest,choice);p.putString("id",latest.getString("id"));p.putInt("appearance",latest.getInt("appearance"));}if(!latest.isEmpty())for(String key:java.util.List.of("behavior","aggroRadius","wanderRadius","leashRadius","bones","boxes","animations","scale","width","height","sounds","callback","meleeReach","knockbackResistance","knifeMode","knifeHits","meleeCooldown","damage","speed","speedMode","stayNearSpawn","healthMode","faction","combatRole","preferredRange","smokyEyes","texture","textureWidth","textureHeight","sourceMotion","eyeR","eyeG","eyeB"))if(latest.contains(key))p.put(key,latest.get(key).copy());}if(level() instanceof ServerLevel level&&!n.getString("MapMarker").isEmpty()){var rule=me.cryo.zombierool.gameplay.MapActors.get(level).marker(net.minecraft.core.BlockPos.of(Long.parseLong(n.getString("MapMarker"))));for(String key:java.util.List.of("health","damage","speed","aggroRadius","wanderRadius","leashRadius","countsForWave","behavior","knifeMode","knifeHits","speedMode","stayNearSpawn","healthMode","faction"))if(rule.contains(key))p.put(key,rule.get(key).copy());p.putDouble("knockbackResistance",1);}entityData.set(PROFILE,p);setCustomName(p.getBoolean("boss")?MapActors.displayName(p):null);getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(p.getDouble("damage"));getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(level() instanceof ServerLevel server?MapActorBehavior.speed(p,server):p.getDouble("speed"));home=new Vec3(n.getDouble("HomeX"),n.getDouble("HomeY"),n.getDouble("HomeZ"));marker=n.getString("MapMarker");bar.setName(getDisplayName());refreshDimensions();}
    @Override public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getAddEntityPacket(){return net.minecraftforge.network.NetworkHooks.getEntitySpawningPacket(this);}
}
