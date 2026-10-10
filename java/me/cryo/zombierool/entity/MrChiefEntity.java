package me.cryo.zombierool.entity;

import me.cryo.zombierool.config.MrChiefConfig;
import me.cryo.zombierool.gameplay.PointManager;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.core.particles.*;
import net.minecraft.network.syncher.*;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.*;
import net.minecraft.sounds.*;
import net.minecraft.world.*;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import java.util.Comparator;

/** An untamable, peaceful encounter; never belongs to the wave's enemy budget. */
public final class MrChiefEntity extends Cat {
    private static final EntityDataAccessor<Boolean> PARTY=SynchedEntityData.defineId(MrChiefEntity.class,EntityDataSerializers.BOOLEAN);
    private int escapeTicks=-1, fleeTicks=400, reward=2500, partyTicks=MrChiefConfig.PARTY_SECONDS*20;
    private float fleeRadius=8;
    private long partyStart=-1;
    public long partyStart(){return partyStart;}
    public int partyDuration(){return partyTicks;}
    private boolean forced, rewarded, spawnAuthorized;
    public MrChiefEntity(EntityType<? extends Cat> type,net.minecraft.world.level.Level level){super(type,level);setPersistenceRequired();setSilent(true);xpReward=0;}
    public static AttributeSupplier.Builder attributes(){return Cat.createAttributes().add(Attributes.MAX_HEALTH,12).add(Attributes.MOVEMENT_SPEED,.3).add(Attributes.KNOCKBACK_RESISTANCE,1);}
    public void configure(MrChiefConfig config,boolean forced){spawnAuthorized=true;this.forced=forced;fleeTicks=config.fleeSeconds*20;fleeRadius=config.fleeRadius;reward=config.reward;partyTicks=config.partySeconds*20;}
    @Override protected void defineSynchedData(){super.defineSynchedData();entityData.define(PARTY,false);}
    public boolean isSpawnAuthorized(){return spawnAuthorized;}
    public boolean isPartying(){return entityData.get(PARTY);}
    @Override protected void registerGoals(){goalSelector.addGoal(0,new FloatGoal(this));goalSelector.addGoal(1,new FleeGoal());goalSelector.addGoal(4,new WaterAvoidingRandomStrollGoal(this,.7));}
    @Override public MobType getMobType(){return MobType.UNDEAD;}
    @Override public boolean doHurtTarget(Entity entity){return false;}
    @Override public InteractionResult mobInteract(Player player,InteractionHand hand){return InteractionResult.PASS;}
    @Override public boolean canMate(net.minecraft.world.entity.animal.Animal animal){return false;}
    @Override public boolean isFood(net.minecraft.world.item.ItemStack stack){return false;}
    @Override public boolean isPickable(){return !isPartying() && super.isPickable();}
    @Override public boolean isAttackable(){return !isPartying() && super.isAttackable();}
    @Override public boolean hurt(DamageSource source,float amount){return !isPartying() && super.hurt(source,amount);}
    @Override protected void dropFromLootTable(DamageSource source,boolean recentPlayerHit){}
    @Override protected SoundEvent getDeathSound(){return null;}
    @Override protected SoundEvent getAmbientSound(){return null;}
    @Override protected SoundEvent getHurtSound(DamageSource source){return null;}
    @Override public void knockback(double strength,double x,double z){}

    private Player nearbyPlayer(){return level().getEntitiesOfClass(Player.class,getBoundingBox().inflate(fleeRadius),p->p.isAlive()&&!p.isSpectator()&&p.distanceToSqr(this)<fleeRadius*fleeRadius).stream().min(Comparator.comparingDouble(this::distanceToSqr)).orElse(null);}
    private void beginEscape(){if(escapeTicks<0){escapeTicks=fleeTicks;LuaScriptManager.callEvent("OnMrChiefFled",getUUID().toString(),fleeTicks/20);}}
    @Override public void tick(){
        if(!level().isClientSide){
            if(!forced && !WaveManager.isGameRunning()){discard();return;}
            if(WaveManager.isPausedByPlayer())return;
            if(!isPartying() && isAlive()){
                if(escapeTicks<0 && nearbyPlayer()!=null)beginEscape();
                if(escapeTicks>=0 && --escapeTicks<=0){LuaScriptManager.callEvent("OnMrChiefEscaped",getUUID().toString());discard();return;}
            }
        }
        super.tick();
    }
    @Override public void die(DamageSource source){
        if(dead || rewarded || isPartying())return;
        var credit=source.getEntity() instanceof ServerPlayer p?p:getKillCredit();
        if(!(level() instanceof ServerLevel server)){super.die(source);return;}
        ServerPlayer winner=credit instanceof ServerPlayer player?player:null;
        super.die(source);
        if(!dead)return;
        rewarded=true;partyStart=server.getGameTime();clearFire();hurtTime=0;entityData.set(PARTY,true);setNoAi(true);setNoGravity(true);getNavigation().stop();setDeltaMovement(Vec3.ZERO);
        me.cryo.zombierool.gameplay.MatchAtmosphereSync.begin(this);
        if(winner!=null){
            PointManager.modifyScore(winner,reward,false);
            winner.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.zombierool.mr_chief.reward",reward));
        }
        server.playSound(null,getX(),getY(),getZ(),ZombieroolModSounds.MR_CHIEF_PARTY.get(),SoundSource.RECORDS,1,1);
        server.sendParticles(ParticleTypes.FIREWORK,getX(),getY()+.6,getZ(),20,.6,.4,.6,.08);
        LuaScriptManager.callEvent("OnMrChiefKilled",getUUID().toString(),winner==null?"":winner.getUUID().toString(),winner==null?0:reward,getX(),getY(),getZ());
    }
    @Override protected void tickDeath(){
        if(!isPartying()){super.tickDeath();return;}
        deathTime++;
        if(level() instanceof ServerLevel server){
            if(deathTime>=partyTicks){stopPartySound();discard();return;}
            if(deathTime%4==0)for(int i=0;i<8;i++){
                double angle=(deathTime*.08+i*Math.PI/4),r=2.5;
                Vector3f color=new Vector3f((float)(.5+.5*Math.sin(angle)),(float)(.5+.5*Math.sin(angle+2.1)),(float)(.5+.5*Math.sin(angle+4.2)));
                server.sendParticles(new DustParticleOptions(color,1.1f),getX()+Math.cos(angle)*r,getY()+.3+(i%3)*.4,getZ()+Math.sin(angle)*r,1,.03,.05,.03,0);
            }
            if(deathTime%20==0)server.sendParticles(ParticleTypes.NOTE,getX(),getY()+1.4,getZ(),3,.8,.1,.8,0);
        }
    }
    public void stopPartySound(){if(level() instanceof ServerLevel server && isPartying())for(var p:server.players())p.connection.send(new ClientboundStopSoundPacket(ZombieroolModSounds.MR_CHIEF_PARTY.getId(),SoundSource.RECORDS));}
    private final class FleeGoal extends Goal {
        private net.minecraft.world.level.pathfinder.Path path;
        FleeGoal(){setFlags(java.util.EnumSet.of(Flag.MOVE));}
        @Override public boolean canUse(){
            if(isPartying())return false;Player player=nearbyPlayer();if(player==null)return false;
            Vec3 away=DefaultRandomPos.getPosAway(MrChiefEntity.this,16,7,player.position());if(away==null)return false;
            if(away.distanceToSqr(player.position())<=distanceToSqr(player))return false;
            path=getNavigation().createPath(away.x,away.y,away.z,0);return path!=null;
        }
        @Override public void start(){beginEscape();getNavigation().moveTo(path,1.8);}
        @Override public boolean canContinueToUse(){return !isPartying()&&!getNavigation().isDone();}
        @Override public void stop(){getNavigation().stop();}
    }
}
