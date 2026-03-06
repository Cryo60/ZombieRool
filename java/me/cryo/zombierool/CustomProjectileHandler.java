package me.cryo.zombierool.handlers;

import me.cryo.zombierool.ExplosionControl;
import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.network.DisplayHitmarkerPacket;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.WeaponVfxPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CustomProjectileHandler {
	@SubscribeEvent
	public static void onLevelTick(TickEvent.LevelTickEvent event) {
	    if (event.phase == TickEvent.Phase.END && !event.level.isClientSide()) {
	        Level level = event.level;
	        for (AbstractArrow arrow : level.getEntitiesOfClass(AbstractArrow.class, new AABB(BlockPos.ZERO).inflate(10000))) {
	            CompoundTag tag = arrow.getPersistentData();
	            if (!tag.getBoolean("zombierool:custom_projectile")) continue;
	
	            String trailVfx = tag.getString("zombierool:trail_vfx");
	            boolean isPap = tag.getBoolean("zombierool:pap");
	
	            if (level instanceof ServerLevel sl) {
	                if ("RPG".equals(trailVfx)) {
	                    sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, arrow.getX(), arrow.getY(), arrow.getZ(), 2, 0.1, 0.1, 0.1, 0.02);
	                } else if ("RAYGUN".equals(trailVfx)) {
	                    Vector3f outerColor = isPap ? new Vector3f(1.0f, 0.0f, 0.0f) : new Vector3f(0.0f, 1.0f, 0.0f);
	                    Vector3f coreColor = isPap ? new Vector3f(1.0f, 0.4f, 0.0f) : new Vector3f(0.5f, 1.0f, 0.5f);
	                    sl.sendParticles(new DustParticleOptions(outerColor, 1.5f), arrow.getX(), arrow.getY(), arrow.getZ(), 3, 0.05, 0.05, 0.05, 0);
	                    sl.sendParticles(new DustParticleOptions(coreColor, 0.8f), arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.02, 0.02, 0.02, 0);
	                } else if ("FIREBALL".equals(trailVfx)) {
	                    sl.sendParticles(ParticleTypes.FLAME, arrow.getX(), arrow.getY(), arrow.getZ(), 3, 0.1, 0.1, 0.1, 0.02);
	                    sl.sendParticles(ParticleTypes.SMOKE, arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0.05, 0.05, 0.05, 0.0);
	                } else if ("LIGHTNING".equals(trailVfx)) {
	                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, arrow.getX(), arrow.getY(), arrow.getZ(), 2, 0.1, 0.1, 0.1, 0.0);
	                    sl.sendParticles(new DustParticleOptions(new Vector3f(0.5f, 0.8f, 1.0f), 1.0f), arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0,0,0,0);
	                }
	
	                if (tag.getBoolean("zombierool:is_needle")) {
	                    Entity target = null;
	                    if (tag.contains("zr_needle_target")) {
	                        target = sl.getEntity(tag.getUUID("zr_needle_target"));
	                    }
	
	                    if (target == null || !target.isAlive() || target.distanceToSqr(arrow) > 9.0) {
	                        AABB searchBox = arrow.getBoundingBox().inflate(3.0);
	                        List<LivingEntity> potentialTargets = sl.getEntitiesOfClass(LivingEntity.class, searchBox, e ->
	                            e.isAlive() && e != arrow.getOwner() &&
	                            (e instanceof me.cryo.zombierool.entity.ZombieEntity || e instanceof me.cryo.zombierool.entity.CrawlerEntity || e instanceof me.cryo.zombierool.entity.HellhoundEntity || e instanceof me.cryo.zombierool.entity.DummyEntity)
	                        );
	                        if (!potentialTargets.isEmpty()) {
	                            potentialTargets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(arrow)));
	                            target = potentialTargets.get(0);
	                            tag.putUUID("zr_needle_target", target.getUUID());
	                        } else {
	                            tag.remove("zr_needle_target");
	                        }
	                    }
	
	                    if (target != null) {
	                        Vec3 targetPos = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.85, target.getZ());
	                        Vec3 dir = targetPos.subtract(arrow.position()).normalize();
	                        double speed = arrow.getDeltaMovement().length();
	                        arrow.setDeltaMovement(dir.scale(speed));
	                        arrow.hasImpulse = true;
	                        arrow.setYRot((float)(Mth.atan2(dir.x, dir.z) * (double)(180F / (float)Math.PI)));
	                        arrow.setXRot((float)(Mth.atan2(dir.y, dir.horizontalDistance()) * (double)(180F / (float)Math.PI)));
	                    }
	
	                    Vector3f color = isPap ? new Vector3f(1.0f, 0.2f, 0.6f) : new Vector3f(0.8f, 0.2f, 0.8f);
	                    sl.sendParticles(new DustParticleOptions(color, 1.0f), arrow.getX(), arrow.getY(), arrow.getZ(), 1, 0, 0, 0, 0);
	                }
	            }
	        }
	    }
	}
	
	@SubscribeEvent
	public static void onImpact(ProjectileImpactEvent event) {
	    if (event.getProjectile() instanceof AbstractArrow arrow) {
	        CompoundTag tag = arrow.getPersistentData();
	        if (tag.getBoolean("zombierool:custom_projectile")) {
	            
	            if (tag.getBoolean("zombierool:is_needle")) {
	                if (event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
	                    EntityHitResult entityHit = (EntityHitResult) event.getRayTraceResult();
	                    Entity target = entityHit.getEntity();
	                    if (target instanceof LivingEntity livingTarget && arrow.getOwner() instanceof ServerPlayer shooter) {
	                        float damage = tag.getFloat("zombierool:damage");
	                        boolean isPap = tag.getBoolean("zombierool:pap");
	                        boolean headshot = arrow.getY() > livingTarget.getY() + livingTarget.getBbHeight() * 0.85;
	
	                        float finalDamage = DamageManager.calculateDamage(shooter, livingTarget, damage, headshot, shooter.getMainHandItem());
	                        livingTarget.getPersistentData().putBoolean(DamageManager.GUN_DAMAGE_TAG, true);
	                        if (headshot) livingTarget.getPersistentData().putBoolean(DamageManager.HEADSHOT_TAG, true);
	                        else livingTarget.getPersistentData().remove(DamageManager.HEADSHOT_TAG);
	
	                        if (DamageManager.applyDamage(livingTarget, shooter.damageSources().playerAttack(shooter), finalDamage)) {
	                            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> shooter), new DisplayHitmarkerPacket());
	
	                            CompoundTag targetData = livingTarget.getPersistentData();
	                            int needles = targetData.getInt("zr_needles_stuck");
	                            long lastNeedleTick = targetData.getLong("zr_last_needle_tick");
	                            long currentTick = livingTarget.level().getGameTime();
	
	                            if (currentTick - lastNeedleTick > 60) {
	                                needles = 0;
	                            }
	                            needles++;
	
	                            int threshold = isPap ? 5 : 7; 
	                            if (needles >= threshold) {
	                                needles = 0;
	                                float radius = isPap ? 4.5f : 3.0f;
	                                float explosionDamage = isPap ? 400.0f : 200.0f;
	                                ExplosionControl.doCustomExplosion(
	                                    livingTarget.level(), shooter, livingTarget.position().add(0, livingTarget.getBbHeight() / 2, 0),
	                                    explosionDamage, radius, 1.0f, 0.0f, 0.0f, 0.2f, "EXPLOSION", "zombierool:needler_supercombine", isPap
	                                );
	                            }
	                            targetData.putInt("zr_needles_stuck", needles);
	                            targetData.putLong("zr_last_needle_tick", currentTick);
	                        }
	                        arrow.discard();
	                        event.setCanceled(true);
	                        return;
	                    }
	                } else if (event.getRayTraceResult().getType() == HitResult.Type.BLOCK) {
	                    arrow.discard();
	                    event.setCanceled(true);
	                    return;
	                }
	            }
	
	            if (tag.getBoolean("zombierool:explosive")) {
	                if (!arrow.level().isClientSide) {
	                    float radius = tag.getFloat("zr_exp_radius");
	                    float damage = tag.getFloat("zombierool:damage");
	                    float dmgMult = tag.getFloat("zr_exp_dmg_mult");
	                    float selfMult = tag.getFloat("zr_exp_self_mult");
	                    float selfCap = tag.getFloat("zr_exp_self_cap");
	                    float kb = tag.getFloat("zr_exp_kb");
	                    String vfx = tag.getString("zr_exp_vfx");
	                    String sound = tag.getString("zr_exp_sound");
	                    boolean isPap = tag.getBoolean("zombierool:pap");
	
	                    Vec3 pos = event.getRayTraceResult().getLocation();
	                    if (event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
	                        pos = new Vec3(pos.x, arrow.getY(), pos.z);
	                    }
	
	                    ExplosionControl.doCustomExplosion(
	                        arrow.level(), arrow.getOwner(), pos, damage, radius,
	                        dmgMult, selfMult, selfCap, kb, vfx, sound, isPap
	                    );
	                }
	                arrow.discard();
	                event.setCanceled(true);
	                return;
	            }
	
	            if (tag.getBoolean("zombierool:plasma_impact")) {
	                if (!arrow.level().isClientSide && arrow.level() instanceof ServerLevel sl) {
	                    Vec3 pos = event.getRayTraceResult().getLocation();
	                    if (event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
	                        pos = new Vec3(pos.x, arrow.getY(0.5), pos.z);
	                    } else {
	                        pos = arrow.getBoundingBox().getCenter();
	                    }
	                    boolean isPap = tag.getBoolean("zombierool:plasma_pap");
	                    boolean overcharged = tag.getBoolean("zombierool:is_overcharged");
	                    String vfxType = overcharged ? "PLASMA_IMPACT_OVERCHARGE" : "PLASMA_IMPACT";
	                    final Vec3 finalPos = pos;
	                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> sl.getChunkAt(BlockPos.containing(finalPos))),
	                        new WeaponVfxPacket(vfxType, finalPos, finalPos, isPap, false));
	                }
	            }
	
	            if (event.getRayTraceResult().getType() == HitResult.Type.ENTITY) {
	                EntityHitResult entityHit = (EntityHitResult) event.getRayTraceResult();
	                Entity target = entityHit.getEntity();
	
	                if (target instanceof LivingEntity livingTarget && arrow.getOwner() instanceof ServerPlayer shooter) {
	                    float damage = tag.getFloat("zombierool:damage");
	                    boolean headshot = false;
	
	                    if (arrow.getY() > livingTarget.getY() + livingTarget.getBbHeight() * 0.85) {
	                        headshot = true;
	                    }
	
	                    float finalDamage = DamageManager.calculateDamage(shooter, livingTarget, damage, headshot, shooter.getMainHandItem());
	                    
	                    livingTarget.getPersistentData().putBoolean(DamageManager.GUN_DAMAGE_TAG, true);
	                    if (headshot) {
	                        livingTarget.getPersistentData().putBoolean(DamageManager.HEADSHOT_TAG, true);
	                    } else {
	                        livingTarget.getPersistentData().remove(DamageManager.HEADSHOT_TAG);
	                    }
	
	                    if (DamageManager.applyDamage(livingTarget, shooter.damageSources().playerAttack(shooter), finalDamage)) {
	                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> shooter), new DisplayHitmarkerPacket());
	                    }
	
	                    if (arrow.getPierceLevel() <= 0) {
	                        arrow.discard();
	                        event.setCanceled(true);
	                    }
	                }
	            } else if (event.getRayTraceResult().getType() == HitResult.Type.BLOCK) {
	                arrow.discard();
	                event.setCanceled(true);
	            }
	        }
	    }
	}
}
