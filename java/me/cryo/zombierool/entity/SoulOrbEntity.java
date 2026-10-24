package me.cryo.zombierool.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.*;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;

/** A tracked visual entity: visible independently of particle settings and render-stage timing. */
public final class SoulOrbEntity extends Entity implements IEntityAdditionalSpawnData {
    public static final int DURATION=32;
    private static final EntityDataAccessor<Integer> AGE=SynchedEntityData.defineId(SoulOrbEntity.class,EntityDataSerializers.INT);
    private Vec3 start=Vec3.ZERO,end=Vec3.ZERO;
    private int age;
    public SoulOrbEntity(EntityType<?> type,Level level){super(type,level);noPhysics=true;setNoGravity(true);}
    public SoulOrbEntity(PlayMessages.SpawnEntity packet,Level level){this(me.cryo.zombierool.init.ZombieroolModEntities.SOUL_ORB.get(),level);}
    @Override protected void defineSynchedData(){entityData.define(AGE,0);}
    public void route(Vec3 from,Vec3 to){start=from;end=to;setPos(from);}
    public Vec3 sample(double tick){double t=Math.max(0,Math.min(1,tick/DURATION));return start.lerp(end,t).add(0,Math.sin(t*Math.PI)*.6,0);}
    public Vec3 renderPosition(float partial){return sample(Math.max(0,age-1+partial));}
    public void advance(int ticks){age=ticks;entityData.set(AGE,ticks);setPos(sample(ticks));}
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){super.onSyncedDataUpdated(key);if(key.equals(AGE))age=entityData.get(AGE);}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide){age=Math.min(DURATION,age+1);setPos(sample(age));
            level().addParticle(new net.minecraft.core.particles.DustParticleOptions(new org.joml.Vector3f(1,.015f,.015f),1.25f),getX(),getY(),getZ(),0,.01,0);
        }else if(tickCount>DURATION+40)discard(); // A removed/unloaded chest cannot leave immortal visuals.
    }
    @Override public boolean isPickable(){return false;}
    @Override protected void readAdditionalSaveData(CompoundTag n){}
    @Override protected void addAdditionalSaveData(CompoundTag n){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
    @Override public void writeSpawnData(FriendlyByteBuf b){for(Vec3 p:new Vec3[]{start,end}){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}b.writeVarInt(age);}
    @Override public void readSpawnData(FriendlyByteBuf b){start=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());end=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());age=b.readVarInt();setPos(sample(age));}
}
