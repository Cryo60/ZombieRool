package me.cryo.zombierool.entity;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.*;
import net.minecraft.world.phys.*;
import net.minecraft.server.level.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.network.syncher.*;

/** Straight skillshot: no homing, swept entity/block collision, then a visible tether. */
public class PanzerClawEntity extends Entity {
    private static final EntityDataAccessor<Integer> OWNER=SynchedEntityData.defineId(PanzerClawEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CAUGHT=SynchedEntityData.defineId(PanzerClawEntity.class,EntityDataSerializers.INT);
    public PanzerClawEntity(EntityType<?> type,Level level){super(type,level);}
    @Override protected void defineSynchedData(){entityData.define(OWNER,-1);entityData.define(CAUGHT,-1);}
    public void launch(SpecialEnemyEntity owner,Vec3 direction){direction=direction.normalize();entityData.set(OWNER,owner.getId());setPos(owner.grappleOrigin().add(direction.scale(.3)));setDeltaMovement(direction.scale(level() instanceof net.minecraft.server.level.ServerLevel server?me.cryo.zombierool.config.SpecialEnemyConfig.get(server).getDouble("clawSpeed"):1.65));}
    public Entity owner(){return level().getEntity(entityData.get(OWNER));}
    @Override public void tick(){
        super.tick();
        if(level().isClientSide)return;
        if(!(owner() instanceof SpecialEnemyEntity panzer)||!panzer.isAlive()){discard();return;}
        if(me.cryo.zombierool.gameplay.WaveManager.isPausedByPlayer())return;
        if(entityData.get(CAUGHT)>=0){var caught=level().getEntity(entityData.get(CAUGHT));if(caught==null||!panzer.hasCapturedPlayer()){discard();return;}setPos(caught.getEyePosition());return;}
        if(tickCount>18){discard();return;}
        Vec3 start=position(),end=start.add(getDeltaMovement());
        var block=level().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,this));
        Vec3 limit=block.getType()==HitResult.Type.MISS?end:block.getLocation();
        EntityHitResult hit=null;double nearest=start.distanceToSqr(limit);
        for(var p:level().getEntitiesOfClass(ServerPlayer.class,getBoundingBox().expandTowards(getDeltaMovement()).inflate(me.cryo.zombierool.config.SpecialEnemyConfig.get((ServerLevel)level()).getDouble("clawRadius")),p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()&&!me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID()))){
            var box=p.getBoundingBox().inflate(me.cryo.zombierool.config.SpecialEnemyConfig.get((ServerLevel)level()).getDouble("clawRadius"));var point=box.contains(start)?java.util.Optional.of(start):box.clip(start,limit);
            if(point.isPresent()&&start.distanceToSqr(point.get())<=nearest){nearest=start.distanceToSqr(point.get());hit=new EntityHitResult(p,point.get());}
        }
        if(hit!=null&&hit.getEntity() instanceof ServerPlayer player){if(panzer.capture(player)){entityData.set(CAUGHT,player.getId());setDeltaMovement(Vec3.ZERO);setPos(player.getEyePosition());}else discard();return;}
        if(block.getType()!=HitResult.Type.MISS){discard();return;}
        setPos(end);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){}
    @Override protected void addAdditionalSaveData(CompoundTag tag){}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
