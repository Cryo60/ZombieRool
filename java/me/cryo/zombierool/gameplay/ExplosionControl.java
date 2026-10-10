package me.cryo.zombierool.gameplay;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CWeaponVfxPacket;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.scripting.LuaScriptManager;

import java.util.List;

public class ExplosionControl {

    public static void doCustomExplosion(Level level, Entity source, Vec3 pos, float baseDamage, float radius, float dmgMult, float selfDmgMult, float selfDmgCap, float kbStrength, String vfxType, String soundId, boolean isPap) {
        doCustomExplosion(level,source,pos,baseDamage,radius,dmgMult,selfDmgMult,selfDmgCap,kbStrength,vfxType,soundId,isPap,false);
    }

    public static void doCustomExplosion(Level level, Entity source, Vec3 pos, float baseDamage, float radius, float dmgMult, float selfDmgMult, float selfDmgCap, float kbStrength, String vfxType, String soundId, boolean isPap, boolean heldInHand) {
        if (level.isClientSide) return;

        if (soundId != null && !soundId.isEmpty() && !soundId.equals("NONE")) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation(soundId));
            if (sound != null) {
                level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, 4.0f, (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
            }
        }

        if (vfxType != null && !vfxType.isEmpty() && !vfxType.equals("NONE")) {
            if (level instanceof ServerLevel sl) {
                NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> sl.getChunkAt(BlockPos.containing(pos))),
                        new S2CWeaponVfxPacket(vfxType, pos, pos, isPap, false));
            }
        }

        float actualDamage = baseDamage * dmgMult;
        AABB area = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
        List<Entity> entities = level.getEntities((Entity) null, area);
        DamageSource dmgSource = source instanceof Player ? level.damageSources().playerAttack((Player)source) : level.damageSources().generic();

        String ownerUuid = source instanceof Player p ? p.getUUID().toString() : "";
        LuaScriptManager.callEvent("OnExplosion", ownerUuid, pos.x, pos.y, pos.z, (double)radius);

        if (source instanceof Player owner && owner.level() == level && owner.distanceToSqr(pos) <= radius * radius) {
            if (heldInHand) FixedPlayerDamage.heldGrenadeExplosion(owner);
            else FixedPlayerDamage.selfExplosion(owner);
        }

        for (Entity entity : entities) {
            if (entity instanceof Painting || entity instanceof ItemFrame) continue;

            double distSq = entity.distanceToSqr(pos);
            if (distSq <= radius * radius) {
                if (entity instanceof Player p) {
                    if (p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
                        continue;
                    }
                    // Owner was handled once before the entity loop.
                    continue;
                }

                float finalDamage = actualDamage;
                if (entity instanceof LivingEntity le) {
                    finalDamage = actualDamage * 5.0f;
                    le.getPersistentData().putBoolean("zombierool:explosive_damage", true);
                    
                    if (kbStrength <= 0.0f) le.getPersistentData().putBoolean("zr_prevent_knockback", true);
                    me.cryo.zombierool.core.manager.DamageManager.applyDamage(le, dmgSource, finalDamage);
                    le.getPersistentData().remove("zr_prevent_knockback");
                } else {
                    entity.hurt(dmgSource, finalDamage);
                }
                
                entity.hurtMarked = true;
            }
        }
    }

    public static void doGrenadeExplosion(Level level, Entity source, Vec3 pos, float baseDamage, float innerRadius, float outerRadius) {
        if (level.isClientSide) return;

        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:explode"));
        if (sound != null) {
            level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, 4.0f, (1.0F + (level.random.nextFloat() - level.random.nextFloat()) * 0.2F) * 0.7F);
        }

        if (level instanceof ServerLevel sl) {
            NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> sl.getChunkAt(BlockPos.containing(pos))),
                    new S2CWeaponVfxPacket("EXPLOSION", pos, pos, false, false));
        }

        String ownerUuid = source instanceof Player p ? p.getUUID().toString() : "";
        LuaScriptManager.callEvent("OnExplosion", ownerUuid, pos.x, pos.y, pos.z, (double)outerRadius);

        AABB area = new AABB(pos.x - outerRadius, pos.y - outerRadius, pos.z - outerRadius, pos.x + outerRadius, pos.y + outerRadius, pos.z + outerRadius);
        List<Entity> entities = level.getEntities((Entity) null, area);
        DamageSource dmgSource = source instanceof Player ? level.damageSources().playerAttack((Player)source) : level.damageSources().generic();

        if (source instanceof Player owner && owner.level() == level && owner.distanceToSqr(pos) <= outerRadius * outerRadius) FixedPlayerDamage.selfExplosion(owner);

        for (Entity entity : entities) {
            if (entity instanceof Painting || entity instanceof ItemFrame) continue;

            double distSq = entity.distanceToSqr(pos);
            if (distSq <= outerRadius * outerRadius) {
                double dist = Math.sqrt(distSq);
                boolean inInner = dist <= innerRadius;
                
                if (entity instanceof Player p) {
                    if (p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
                        continue;
                    }
                    // Owner was handled once before the entity loop.
                    continue;
                }

                float finalDamage = inInner ? baseDamage : baseDamage * 0.45f;
                if (entity instanceof LivingEntity le) {
                    le.getPersistentData().putBoolean("zombierool:explosive_damage", true);
                    me.cryo.zombierool.core.manager.DamageManager.applyDamage(le, dmgSource, finalDamage);
                    
                    if (!inInner && le.isAlive() && le instanceof ZombieEntity zombie && !zombie.isCrawler()) {
                        if (level.random.nextFloat() < 0.85f) {
                            zombie.makeCrawler();
                        }
                    }
                } else {
                    entity.hurt(dmgSource, finalDamage);
                }
                entity.hurtMarked = true;
            }
        }
    }
}
