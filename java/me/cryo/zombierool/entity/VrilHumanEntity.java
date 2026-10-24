package me.cryo.zombierool.entity;

import me.cryo.zombierool.gameplay.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** A temporary unarmed human; all paths still respect the map's authorized terrain. */
public final class VrilHumanEntity extends PathfinderMob {
    public int life=200,lazarusHits=1;public UUID owner;
    private BlockPos water;private boolean searched;private int corpseTicks;
    public VrilHumanEntity(EntityType<? extends VrilHumanEntity> type,Level level){super(type,level);setMaxUpStep(1.25f);}
    public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.32).add(Attributes.FOLLOW_RANGE,32);}
    @Override protected PathNavigation createNavigation(Level level){return new RouteNavigation(this,level);}
    @Override protected void registerGoals(){goalSelector.addGoal(2,new RandomStrollGoal(this,1.2,20));goalSelector.addGoal(3,new LookAtPlayerGoal(this,Player.class,8));}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public boolean isPersistenceRequired(){return true;}
    @Override public boolean removeWhenFarAway(double distance){return false;}
    @Override public boolean causeFallDamage(float distance,float multiplier,net.minecraft.world.damagesource.DamageSource source){return false;}
    @Override public boolean hurt(net.minecraft.world.damagesource.DamageSource source,float amount){
        boolean result=super.hurt(source,amount);
        if(result&&isAlive()&&!level().isClientSide)me.cryo.zombierool.core.manager.GoreManager.onHit(this,false);
        if(result&&source.getEntity() instanceof LivingEntity enemy&&EquipmentEffects.enemy(enemy)&&owner!=null&&level() instanceof ServerLevel level){
            var player=level.getServer().getPlayerList().getPlayer(owner);if(player!=null)PointManager.awardEnemyPoints(player,enemy,10);
        }
        return result;
    }
    @Override public void tick(){
        super.tick();if(!(level() instanceof ServerLevel level)||!isAlive())return;
        if(--life<=0||isInWater()){expire();return;}
        if(!searched){searched=true;var candidates=new java.util.ArrayList<BlockPos>();
            for(var p:BlockPos.betweenClosed(blockPosition().offset(-16,-8,-16),blockPosition().offset(16,3,16))){
                if(!level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER)||level.getFluidState(p.below()).is(net.minecraft.tags.FluidTags.WATER))continue;
                candidates.add(p.immutable());
            }
            candidates.sort(java.util.Comparator.comparingDouble(p->p.distSqr(blockPosition())));
            // Bound expensive route searches even in a map with a huge unreachable pool.
            for(var p:candidates.stream().limit(8).toList()){var path=getNavigation().createPath(p,0);if(path!=null&&path.canReach()){water=p;break;}}
        }
        if(tickCount%20==0){
            if(water!=null)getNavigation().moveTo(water.getX()+.5,water.getY(),water.getZ()+.5,1.3);
            else {
                var enemy=level.getEntitiesOfClass(Mob.class,getBoundingBox().inflate(12),EquipmentEffects::enemy).stream().min(java.util.Comparator.comparingDouble(this::distanceToSqr)).orElse(null);
                if(enemy!=null){Vec3 away=position().subtract(enemy.position()).normalize();getNavigation().moveTo(getX()+away.x*6,getY(),getZ()+away.z*6,1.3);}
            }
        }
        if(tickCount%60==0)EquipmentSystem.sound(level,position(),"vr11_human",.5f);
    }
    public void detonate(Player player){
        if(!(level() instanceof ServerLevel level))return;
        EquipmentSystem.sound(level,position(),"vr11_explode",.8f);level.sendParticles(net.minecraft.core.particles.ParticleTypes.EXPLOSION_EMITTER,getX(),getY()+1,getZ(),1,0,0,0,0);
        for(var enemy:level.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(5),EquipmentEffects::enemy))if(distanceToSqr(enemy)<=25)
            me.cryo.zombierool.core.manager.DamageManager.applyDamage(enemy,damageSources().playerAttack(player),750);
        expire();
    }

    private void expire(){
        if(!isAlive())return;
        setHealth(0);die(damageSources().generic());
    }

    @Override public void die(net.minecraft.world.damagesource.DamageSource source){
        if(dead)return;
        super.die(source);
        if(!dead)return;
        getNavigation().stop();setTarget(null);setNoAi(true);
        TraversalMotion.cancel(this);
        if(!level().isClientSide)me.cryo.zombierool.core.manager.GoreManager.onDeath(this,false);
    }

    @Override protected void tickDeath(){
        setNoAi(true);setNoGravity(false);noPhysics=false;
        double vertical=getDeltaMovement().y;
        move(MoverType.SELF,new Vec3(0,vertical-.08*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt(this),0));
        setDeltaMovement(0,onGround()?0:(vertical-.08*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt(this))*.98,0);
        deathTime=Math.min(19,deathTime+1);
        boolean reduced=level() instanceof ServerLevel server&&me.cryo.zombierool.config.WorldConfig.get(server).isReducedGore();
        if(!level().isClientSide&&++corpseTicks>(reduced?20:3600))remove(RemovalReason.KILLED);
    }
}
