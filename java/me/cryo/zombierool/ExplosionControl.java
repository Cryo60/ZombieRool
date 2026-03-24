package me.cryo.zombierool;

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

        for (Entity entity : entities) {
            if (entity instanceof Painting || entity instanceof ItemFrame) continue;

            double distSq = entity.distanceToSqr(pos);
            if (distSq <= radius * radius) {
                if (entity instanceof Player p) {
                    if (p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
                        continue;
                    }
                    if (entity == source) {
                        long now = level.getGameTime();
                        long lastSelfDmg = p.getPersistentData().getLong("zr_last_self_exp");
                        
                        if (selfDmgCap >= 100.0f) {
                            if (kbStrength <= 0.0f) p.getPersistentData().putBoolean("zr_prevent_knockback", true);
                            p.hurt(dmgSource, selfDmgCap);
                            p.getPersistentData().remove("zr_prevent_knockback");
                            continue;
                        }

                        if (now - lastSelfDmg < 10) continue;
                        double dist = Math.sqrt(distSq);
                        float distRatio = 1.0f - (float)(dist / radius);
                        if (distRatio > 0.2f) {
                            float selfDmg = Math.min(actualDamage * selfDmgMult * distRatio, selfDmgCap);
                            if (selfDmg >= 0.5f) {
                                if (kbStrength <= 0.0f) p.getPersistentData().putBoolean("zr_prevent_knockback", true);
                                p.hurt(dmgSource, selfDmg);
                                p.getPersistentData().remove("zr_prevent_knockback");
                                p.getPersistentData().putLong("zr_last_self_exp", now);
                            }
                        }
                        continue;
                    }
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
                    if (entity == source) {
                        long now = level.getGameTime();
                        long lastSelfDmg = p.getPersistentData().getLong("zr_last_self_exp");
                        if (now - lastSelfDmg < 10) continue;

                        if (dist <= outerRadius * 0.6) {
                            float selfDmg = 1.0f; 
                            p.hurt(dmgSource, selfDmg);
                            p.getPersistentData().putLong("zr_last_self_exp", now);
                        }
                    }
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
