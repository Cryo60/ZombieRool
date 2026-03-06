package me.cryo.zombierool;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.WeaponVfxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.decoration.Painting;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

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
                    new WeaponVfxPacket(vfxType, pos, pos, isPap, false));
            }
        }

        float actualDamage = baseDamage * dmgMult;
        AABB area = new AABB(pos.x - radius, pos.y - radius, pos.z - radius, pos.x + radius, pos.y + radius, pos.z + radius);
        List<Entity> entities = level.getEntities((Entity) null, area);
        DamageSource dmgSource = source instanceof Player ? level.damageSources().playerAttack((Player)source) : level.damageSources().generic();

        for (Entity entity : entities) {
            if (entity instanceof Painting || entity instanceof ItemFrame) continue;

            double distSq = entity.distanceToSqr(pos);
            if (distSq <= radius * radius) {
                
                if (entity instanceof Player p) {
                    if (p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
                        continue;
                    }
                }

                float finalDamage = actualDamage;

                if (entity == source) {
                    finalDamage = Math.min(actualDamage * selfDmgMult, selfDmgCap);
                    entity.hurt(dmgSource, finalDamage);
                    entity.invulnerableTime = 0; 
                    continue; 
                }

                if (entity instanceof Player) {
                    continue; 
                } else if (entity instanceof LivingEntity le) {
                    le.getPersistentData().putBoolean("zombierool:explosive_damage", true);
                    me.cryo.zombierool.core.manager.DamageManager.applyDamage(le, dmgSource, finalDamage);
                } else {
                    entity.hurt(dmgSource, finalDamage);
                }

                if (kbStrength > 0) {
                    double dx = entity.getX() - pos.x;
                    double dy = entity.getEyeY() - pos.y;
                    double dz = entity.getZ() - pos.z;
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (dist != 0) {
                        entity.setDeltaMovement(entity.getDeltaMovement().add(dx / dist * kbStrength, 0.2, dz / dist * kbStrength));
                    }
                }
                entity.hurtMarked = true;
            }
        }
    }
}