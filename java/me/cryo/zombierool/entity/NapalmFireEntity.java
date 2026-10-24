package me.cryo.zombierool.entity;

import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraftforge.network.NetworkHooks;

/** A temporary fire carpet, independent of the collisionless Path blocks underneath. */
public class NapalmFireEntity extends Entity {
    private int remaining=160;
    private boolean decorative;
    public void setLifetime(int ticks){remaining=Math.max(1,Math.min(2400,ticks));}
    public boolean isDecorative(){return decorative;}
    public void setDecorative(int ticks){decorative=true;remaining=ticks;}
    public NapalmFireEntity(EntityType<?> type,Level level){super(type,level);if(level instanceof ServerLevel server)remaining=me.cryo.zombierool.config.SpecialEnemyConfig.get(server).getInt("fireDuration")*20;}
    @Override protected void defineSynchedData(){}
    @Override public void tick(){
        super.tick();
        if(!(level() instanceof ServerLevel server)||me.cryo.zombierool.gameplay.WaveManager.isPausedByPlayer())return;
        if(!me.cryo.zombierool.gameplay.WaveManager.isGameRunning()){discard();return;}
        if(--remaining<=0){discard();return;}
        if(!decorative&&tickCount%10==0)for(var victim:server.getEntitiesOfClass(LivingEntity.class,getBoundingBox().inflate(0,.7,0),e->e.isAlive()&&!e.fireImmune())){
            if(victim instanceof net.minecraft.world.entity.player.Player p&&(p.isCreative()||p.isSpectator()||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(p.getUUID())))continue;
            if(victim.position().subtract(position()).horizontalDistanceSqr()>16||victim.getY()<getY()-.4||victim.getY()>getY()+1.2)continue;
            if(victim instanceof me.cryo.zombierool.entity.ZombieEntity zombie){me.cryo.zombierool.gameplay.InfernoZombie.ignite(zombie);zombie.hurt(damageSources().generic(),1);}
            else victim.hurt(damageSources().hotFloor(),1);
            if(!(victim instanceof net.minecraft.world.entity.player.Player))victim.setSecondsOnFire(2);
        }
        if(tickCount%40==0)server.playSound(null,blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.NAPALM_BURN.get(),net.minecraft.sounds.SoundSource.HOSTILE,.65f,1);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag){remaining=tag.getInt("Remaining");decorative=tag.getBoolean("Decorative");}
    @Override protected void addAdditionalSaveData(CompoundTag tag){tag.putInt("Remaining",remaining);tag.putBoolean("Decorative",decorative);}
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket(){return NetworkHooks.getEntitySpawningPacket(this);}
}
