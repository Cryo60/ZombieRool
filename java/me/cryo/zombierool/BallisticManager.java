package me.cryo.zombierool.core.manager;

import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.DisplayHitmarkerPacket;
import me.cryo.zombierool.network.packet.WeaponVfxPacket;
import me.cryo.zombierool.block.system.DefenseDoorSystem;
import me.cryo.zombierool.entity.WhiteKnightEntity;
import me.cryo.zombierool.PointManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import me.cryo.zombierool.network.VisualBlockCrackPacket;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;

public class BallisticManager {

    private static final Random RANDOM = new Random();

    private static class Impact {
        Vec3 pos;
        double distance;
        @Nullable Entity entity;
        @Nullable BlockPos blockPos;
        boolean isEntity;
        boolean isBounce;
        Vec3 bounceNormal;

        Impact(Vec3 pos, double distance, Entity entity) {
            this.pos = pos;
            this.distance = distance;
            this.entity = entity;
            this.isEntity = true;
            this.isBounce = false;
        }

        Impact(Vec3 pos, double distance, BlockPos blockPos) {
            this.pos = pos;
            this.distance = distance;
            this.blockPos = blockPos;
            this.isEntity = false;
            this.isBounce = false;
        }
    }

    public static void fireBullet(ServerPlayer shooter, float range, float damage, float spread, int basePenetration, ItemStack weaponStack, float yawOffset) {
        ServerLevel level = shooter.serverLevel();
        Vec3 eyePos = shooter.getEyePosition(1.0F);

        float actualYaw = shooter.getYRot() + yawOffset;
        float actualPitch = shooter.getXRot();
        float f = actualPitch * ((float)Math.PI / 180F);
        float f1 = -actualYaw * ((float)Math.PI / 180F);
        float f2 = net.minecraft.util.Mth.cos(f1);
        float f3 = net.minecraft.util.Mth.sin(f1);
        float f4 = net.minecraft.util.Mth.cos(f);
        float f5 = net.minecraft.util.Mth.sin(f);
        Vec3 lookVec = new Vec3((f3 * f4), -f5, (f2 * f4));

        if (spread > 0) {
            lookVec = applySpread(lookVec, spread);
        }

        String weaponId = "";
        boolean isWhisperLastShot = false;
        boolean isPap = false;
        WeaponSystem.Definition def = null;
        
        if (weaponStack != null && weaponStack.getItem() instanceof WeaponSystem.BaseGunItem gunItem) {
            def = gunItem.getDefinition();
            weaponId = def.id.replace("zombierool:", "").toLowerCase();
            isPap = gunItem.isPackAPunched(weaponStack);
            if (weaponId.equals("whisper") && gunItem.getAmmo(weaponStack) == 1) {
                isWhisperLastShot = true;
            }
        }

        int finalPenetration = def != null ? def.stats.penetration + (isPap ? def.pap.penetration_bonus : 0) : basePenetration;
        int maxHits = 1 + finalPenetration;
        boolean reduceDamage = def != null && def.stats.damage_reduction_on_pierce;

        List<Impact> allImpacts = new ArrayList<>();
        List<Vec3> trailPoints = new ArrayList<>();
        Vec3 visualStart = weaponStack != null && weaponStack.getItem() instanceof WeaponSystem.BaseGunItem gun ? gun.getVisualMuzzlePos(shooter) : eyePos;
        trailPoints.add(visualStart);

        Vec3 currentPos = eyePos;
        Vec3 currentDir = lookVec;
        double remainingRange = range;
        double accumulatedDist = 0;
        int maxBounces = isPap && def != null ? def.pap.ricochet_count : 0;
        int bounces = 0;
        int safetyLoop = 0;
        BlockPos lastHitBlock = null;

        while (remainingRange > 0 && safetyLoop < 50) {
            safetyLoop++;
            Vec3 segmentEnd = currentPos.add(currentDir.scale(remainingRange));
            
            BlockHitResult blockHit = level.clip(new ClipContext(currentPos, segmentEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
            Vec3 actualEnd = blockHit.getType() == HitResult.Type.MISS ? segmentEnd : blockHit.getLocation();
            double segmentDist = currentPos.distanceTo(actualEnd);

            List<EntityHitResult> entityHits = findAllEntitiesOnPath(level, shooter, currentPos, actualEnd, new AABB(currentPos, actualEnd).inflate(1.0));
            for (EntityHitResult result : entityHits) {
                allImpacts.add(new Impact(result.getLocation(), accumulatedDist + currentPos.distanceTo(result.getLocation()), result.getEntity()));
            }

            if (blockHit.getType() == HitResult.Type.BLOCK) {
                BlockPos hitPos = blockHit.getBlockPos();
                if (hitPos.equals(lastHitBlock)) {
                    currentPos = actualEnd.add(currentDir.scale(0.1));
                    accumulatedDist += segmentDist;
                    remainingRange -= segmentDist;
                    continue;
                }

                BlockState hitState = level.getBlockState(hitPos);
                
                if (hitState.getBlock() instanceof me.cryo.zombierool.block.AbstractTechnicalBlock || hitState.getBlock() instanceof DefenseDoorSystem.DefenseDoorBlock) {
                    currentPos = actualEnd.add(currentDir.scale(0.1));
                    accumulatedDist += segmentDist;
                    remainingRange -= segmentDist;
                    continue;
                }

                if (isPenetrable(hitState)) {
                    allImpacts.add(new Impact(actualEnd, accumulatedDist + segmentDist, hitPos));
                    currentPos = actualEnd.add(currentDir.scale(0.1));
                    accumulatedDist += segmentDist;
                    remainingRange -= segmentDist;
                    lastHitBlock = hitPos;
                } else {
                    Impact impact = new Impact(actualEnd, accumulatedDist + segmentDist, hitPos);
                    if (bounces < maxBounces) {
                        impact.isBounce = true;
                        impact.bounceNormal = new Vec3(blockHit.getDirection().step());
                    }
                    allImpacts.add(impact);

                    if (bounces < maxBounces) {
                        bounces++;
                        Vec3 normal = impact.bounceNormal;
                        currentDir = currentDir.subtract(normal.scale(2 * currentDir.dot(normal))).normalize();
                        currentPos = actualEnd.add(currentDir.scale(0.1));
                        accumulatedDist += segmentDist;
                        remainingRange -= segmentDist;
                        lastHitBlock = hitPos;
                    } else {
                        break;
                    }
                }
            } else {
                break;
            }
        }

        Collections.sort(allImpacts, Comparator.comparingDouble(i -> i.distance));

        int currentHitCount = 0;
        boolean hitHeadshotDuringPath = false;
        Vec3 visualTrailEnd = eyePos.add(lookVec.scale(range));

        for (Impact impact : allImpacts) {
            if (currentHitCount >= maxHits && !impact.isBounce) {
                visualTrailEnd = impact.pos;
                break;
            }

            if (impact.isEntity) {
                if (impact.entity instanceof WhiteKnightEntity) continue;
                if (!impact.entity.isAlive()) continue;

                if (impact.entity instanceof LivingEntity livingTarget) {
                    float headshotThreshold = (def != null && isPap) ? def.pap.headshot_threshold : 0.85f;
                    boolean isHeadshot = isHeadshot(livingTarget, impact.pos, headshotThreshold);
                    if (isHeadshot) hitHeadshotDuringPath = true;

                    float baseGunDamage = damage;
                    float specialBonusDamage = 0f;

                    if (weaponId.equals("percepteur") && livingTarget.getHealth() / livingTarget.getMaxHealth() <= 0.05f) {
                        specialBonusDamage = livingTarget.getHealth() + 10;
                        int pts = isPap ? 75 : 50; 
                        PointManager.modifyScore(shooter, pts); 
                        level.sendParticles(ParticleTypes.SOUL, livingTarget.getX(), livingTarget.getY() + 1, livingTarget.getZ(), 10, 0.2, 0.5, 0.2, 0.1);
                        level.playSound(null, livingTarget.blockPosition(), SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1f, 1f);
                    } else if (isWhisperLastShot) {
                        float baseRandomDamage = 44.0f + RANDOM.nextFloat() * (84.0f - 44.0f);
                        baseGunDamage = (float) (Math.round(baseRandomDamage / 4.0f) * 4.0f);
                        specialBonusDamage = livingTarget.getMaxHealth() * 0.44f;
                    }

                    float headshotMultipliedDamage = DamageManager.calculateDamage(shooter, livingTarget, baseGunDamage, isHeadshot, weaponStack);
                    float finalDamage = headshotMultipliedDamage + specialBonusDamage;

                    boolean willDie = livingTarget.getHealth() - finalDamage <= 0;

                    livingTarget.getPersistentData().putBoolean(DamageManager.GUN_DAMAGE_TAG, true);
                    if (isHeadshot) {
                        livingTarget.getPersistentData().putBoolean(DamageManager.HEADSHOT_TAG, true);
                    } else {
                        livingTarget.getPersistentData().remove(DamageManager.HEADSHOT_TAG);
                    }

                    if (DamageManager.applyDamage(livingTarget, shooter.damageSources().playerAttack(shooter), finalDamage)) {
                        level.playSound(null, livingTarget.getX(), livingTarget.getY(), livingTarget.getZ(),
                                ZombieroolModSounds.IMPACT_FLESH.get(),
                                SoundSource.PLAYERS, 0.5f, 1.0f + (RANDOM.nextFloat() * 0.2f));

                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> shooter), new DisplayHitmarkerPacket());

                        float kb = (def != null && isPap) ? def.pap.knockback_bonus : 0.0f;
                        if (kb > 0.0f) {
                            long now = level.getGameTime();
                            long lastKb = livingTarget.getPersistentData().getLong("zr_kb_cooldown");
                            if (now - lastKb > 20) {
                                Vec3 kbDir = livingTarget.position().subtract(shooter.position()).normalize();
                                livingTarget.setDeltaMovement(livingTarget.getDeltaMovement().add(kbDir.x * kb, 0.2, kbDir.z * kb));
                                livingTarget.getPersistentData().putLong("zr_kb_cooldown", now);
                            }
                        }

                        if (def != null && def.pap.incendiary && isPap) {
                            livingTarget.setSecondsOnFire(5);
                        }

                        if (willDie) {
                            if (isHeadshot) {
                                boolean canExplode = def == null || def.headshot.can_explode_head;
                                float headshotChance = def == null ? 0.3f : def.headshot.head_explosion_chance;
                                if (canExplode && RANDOM.nextFloat() <= headshotChance) {
                                    me.cryo.zombierool.core.manager.GoreManager.triggerHeadExplosion(livingTarget);
                                    if (weaponId.equals("whisper")) {
                                        double hX = livingTarget.getX();
                                        double hY = livingTarget.getY() + livingTarget.getBbHeight() * 0.85;
                                        double hZ = livingTarget.getZ();
                                        spawnCrowFlockEffect(level, hX, hY, hZ);
                                    }
                                }

                                if (weaponId.equals("vandal") && isPap) {
                                    long now = level.getGameTime();
                                    long lastExp = shooter.getPersistentData().getLong("zr_vandal_exp_cd");
                                    if (now - lastExp >= 60) {
                                        shooter.getPersistentData().putLong("zr_vandal_exp_cd", now);
                                        me.cryo.zombierool.ExplosionControl.doCustomExplosion(
                                            level, shooter, livingTarget.position().add(0, livingTarget.getBbHeight() * 0.85, 0),
                                            damage * 3.0f, 3.5f, 1.0f, 0.0f, 0.0f, 0.0f, "EXPLOSION", "zombierool:explosion_old", true
                                        );
                                        level.sendParticles(ParticleTypes.FLAME, livingTarget.getX(), livingTarget.getY() + livingTarget.getBbHeight() * 0.85, livingTarget.getZ(), 40, 0.4D, 0.4D, 0.4D, 0.1D);
                                        level.sendParticles(ParticleTypes.LAVA, livingTarget.getX(), livingTarget.getY() + livingTarget.getBbHeight() * 0.85, livingTarget.getZ(), 10, 0.2D, 0.2D, 0.2D, 0.05D);
                                    }
                                }
                            }
                            if (isWhisperLastShot && !isHeadshot) { 
                                double bX = livingTarget.getX();
                                double bY = livingTarget.getY() + livingTarget.getBbHeight() / 2.0D;
                                double bZ = livingTarget.getZ();
                                spawnCrowFlockEffect(level, bX, bY, bZ);
                            }
                        } else {
                            if (!isHeadshot) {
                                me.cryo.zombierool.core.manager.GoreManager.tryDismemberLimb(livingTarget, finalDamage);
                            }
                        }
                    }

                    currentHitCount++;

                    if (def != null && def.explosion != null && (!def.explosion.pap_only || isPap)) {
                        float rad = def.explosion.radius + (isPap ? def.pap.explosion_radius_bonus : 0);
                        if (rad > 0) {
                            me.cryo.zombierool.ExplosionControl.doCustomExplosion(
                                level, shooter, impact.pos, damage, rad,
                                def.explosion.damage_multiplier, def.explosion.self_damage_multiplier, def.explosion.self_damage_cap,
                                def.explosion.knockback, def.explosion.vfx_type, def.explosion.sound, isPap
                            );
                        }
                    }

                    if (reduceDamage) {
                        damage *= 0.5f; 
                    }

                    if (currentHitCount >= maxHits) {
                        visualTrailEnd = impact.pos; 
                    }
                }
            } else {
                BlockState state = level.getBlockState(impact.blockPos);
                int crackLevel = RANDOM.nextInt(3) + 3;
                NetworkHandler.INSTANCE.send(
                    PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(impact.blockPos)),
                    new VisualBlockCrackPacket(impact.blockPos, crackLevel)
                );

                if (impact.isBounce) {
                    level.playSound(null, impact.pos.x, impact.pos.y, impact.pos.z, SoundEvents.METAL_HIT, SoundSource.PLAYERS, 0.5f, 1.5f);
                    level.sendParticles(ParticleTypes.CRIT, impact.pos.x, impact.pos.y, impact.pos.z, 3, 0.1, 0.1, 0.1, 0.05);
                    trailPoints.add(impact.pos);
                } else if (isPenetrable(state)) {
                    level.sendParticles(ParticleTypes.POOF, impact.pos.x, impact.pos.y, impact.pos.z, 1, 0, 0, 0, 0);
                    level.playSound(null, impact.pos.x, impact.pos.y, impact.pos.z, SoundEvents.WOOD_BREAK, SoundSource.PLAYERS, 0.3f, 1.5f);
                    currentHitCount++;
                    if (reduceDamage) {
                        damage *= 0.5f;
                    }
                } else {
                    if (def != null && def.explosion != null && (!def.explosion.pap_only || isPap)) {
                        float rad = def.explosion.radius + (isPap ? def.pap.explosion_radius_bonus : 0);
                        if (rad > 0) {
                            me.cryo.zombierool.ExplosionControl.doCustomExplosion(
                                level, shooter, impact.pos, damage, rad,
                                def.explosion.damage_multiplier, def.explosion.self_damage_multiplier, def.explosion.self_damage_cap,
                                def.explosion.knockback, def.explosion.vfx_type, def.explosion.sound, isPap
                            );
                        }
                    } else {
                        level.playSound(null, impact.pos.x, impact.pos.y, impact.pos.z, SoundEvents.STONE_HIT, SoundSource.PLAYERS, 0.5f, 1.5f);
                        level.sendParticles(ParticleTypes.POOF, impact.pos.x, impact.pos.y, impact.pos.z, 1, 0, 0, 0, 0);
                    }
                    visualTrailEnd = impact.pos;
                    break;
                }
            }
        }

        trailPoints.add(visualTrailEnd);

        if (def != null) {
            String hitscanVfx = def.ballistics.hitscan_vfx;
            if (isPap && def.pap.incendiary) {
                hitscanVfx = "INCENDIARY";
            }
            if (hitscanVfx != null && !hitscanVfx.equals("NONE")) {
                for (int i = 0; i < trailPoints.size() - 1; i++) {
                    Vec3 pStart = trailPoints.get(i);
                    Vec3 pEnd = trailPoints.get(i + 1);
                    WeaponVfxPacket packet = new WeaponVfxPacket(hitscanVfx, pStart, pEnd, isPap, hitHeadshotDuringPath);
                    NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunkAt(shooter.blockPosition())), packet);
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> shooter), packet);
                }
            }
        }
    }

    private static void spawnCrowFlockEffect(ServerLevel level, double x, double y, double z) {
        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 100, 0.3, 0.3, 0.3, 0.1);
        level.sendParticles(new DustParticleOptions(new Vector3f(0f, 0f, 0f), 3.0f), x, y, z, 100, 0.4, 0.4, 0.4, 0.1);
        for (int i = 0; i < 40; i++) {
            double vx = (RANDOM.nextFloat() - 0.5) * 2.0;
            double vy = RANDOM.nextFloat() * 1.5 + 0.5; 
            double vz = (RANDOM.nextFloat() - 0.5) * 2.0;
            level.sendParticles(ZombieroolModParticleTypes.BLACK_CROW.get(), x, y, z, 0, vx, vy, vz, 1.0);
            level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, x, y, z, 0, vx * 0.5, vy * 0.5, vz * 0.5, 1.0);
        }
        level.playSound(null, x, y, z, ZombieroolModSounds.CROW_WAVE.get(), SoundSource.HOSTILE, 1.5f, 0.8f + RANDOM.nextFloat() * 0.4f);
    }

    private static boolean isPenetrable(BlockState state) {
        if (state.getBlock() instanceof me.cryo.zombierool.block.AbstractTechnicalBlock) return true;
        return state.is(BlockTags.LOGS) ||
               state.is(BlockTags.PLANKS) ||
               state.is(BlockTags.WOODEN_DOORS) ||
               state.is(BlockTags.WOODEN_TRAPDOORS) ||
               state.is(BlockTags.WOODEN_FENCES) ||
               state.is(BlockTags.LEAVES) ||
               state.is(BlockTags.WOOL) ||
               state.is(BlockTags.IMPERMEABLE) ||
               state.is(Blocks.GLASS) ||
               state.is(Blocks.GLASS_PANE) ||
               state.is(Blocks.TINTED_GLASS) ||
               state.is(Blocks.ICE) ||
               state.is(Blocks.PACKED_ICE);
    }

    public static Entity getRayTraceTarget(ServerPlayer shooter, double range) {
        Vec3 eyePos = shooter.getEyePosition(1.0F);
        Vec3 lookVec = shooter.getViewVector(1.0F);
        Vec3 endPos = eyePos.add(lookVec.scale(range));
        EntityHitResult hit = findEntityOnPath(shooter.level(), shooter, eyePos, endPos, new AABB(eyePos, endPos).inflate(1.0));
        return hit != null ? hit.getEntity() : null;
    }

    private static Vec3 applySpread(Vec3 vec, float spread) {
        double factor = 0.045;
        return vec.add(
            RANDOM.nextGaussian() * factor * spread,
            RANDOM.nextGaussian() * factor * spread,
            RANDOM.nextGaussian() * factor * spread
        ).normalize();
    }

    private static List<EntityHitResult> findAllEntitiesOnPath(Level level, Entity shooter, Vec3 start, Vec3 end, AABB searchBox) {
        List<Entity> entities = level.getEntities(shooter, searchBox, e -> e instanceof LivingEntity && !e.isSpectator() && e.isAlive());
        List<EntityHitResult> results = new ArrayList<>();

        for (Entity entity : entities) {
            AABB box = entity.getBoundingBox().inflate(0.3);
            Optional<Vec3> hit = box.clip(start, end);
            if (box.contains(start)) {
                results.add(new EntityHitResult(entity, start));
            } else if (hit.isPresent()) {
                results.add(new EntityHitResult(entity, hit.get()));
            }
        }
        return results;
    }

    @Nullable
    private static EntityHitResult findEntityOnPath(Level level, Entity shooter, Vec3 start, Vec3 end, AABB searchBox) {
        List<EntityHitResult> all = findAllEntitiesOnPath(level, shooter, start, end, searchBox);
        if (all.isEmpty()) return null;
        all.sort(Comparator.comparingDouble(h -> start.distanceToSqr(h.getLocation())));
        return all.get(0);
    }

    private static boolean isHeadshot(Entity target, Vec3 hitPos, float threshold) {
        double yHit = hitPos.y;
        double yBase = target.getY();
        double height = target.getBbHeight();
        return (yHit - yBase) > (height * threshold);
    }
}