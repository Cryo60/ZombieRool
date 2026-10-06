package me.cryo.zombierool.client;

import me.cryo.zombierool.core.manager.GoreManager;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Directional blood. A limb sever sometimes keeps spraying for a short jet
 * instead of a single puff.
 */
public final class BloodJets {
    private static final List<Jet> ACTIVE = new ArrayList<>();

    private BloodJets() {}

    public static void onLimb(LivingEntity entity, GoreManager.Limb limb) {
        if (!(entity.level() instanceof ClientLevel level)) return;
        Vec3 origin = origin(entity, limb);
        Vec3 dir = direction(entity, limb);
        if (level.random.nextFloat() < 0.12F) {
            ACTIVE.add(new Jet(entity.getId(), limb, 16));
            burst(level, origin, dir, 18, 0.55);
        } else {
            burst(level, origin, dir, 8, 0.28);
        }
    }

    public static void clientTick(ClientLevel level) {
        if (level == null) {
            ACTIVE.clear();
            return;
        }
        Iterator<Jet> it = ACTIVE.iterator();
        while (it.hasNext()) {
            if (it.next().tick(level)) it.remove();
        }
    }

    private static void burst(ClientLevel level, Vec3 origin, Vec3 dir, int count, double speed) {
        RandomSource random = level.random;
        Vec3 axis = dir.normalize();
        for (int i = 0; i < count; i++) {
            double power = speed * (0.45 + random.nextDouble());
            Vec3 vel = axis.scale(power).add(
                    (random.nextDouble() - 0.5) * 0.22,
                    (random.nextDouble() - 0.35) * 0.18,
                    (random.nextDouble() - 0.5) * 0.22);
            level.addParticle(ZombieroolModParticleTypes.BLOOD_STAIN.get(),
                    origin.x, origin.y, origin.z, vel.x, vel.y, vel.z);
        }
    }

    private static Vec3 origin(LivingEntity entity, GoreManager.Limb limb) {
        float yaw = entity.getYRot() * ((float) Math.PI / 180.0F);
        Vec3 look = new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
        Vec3 right = new Vec3(look.z, 0.0, -look.x);
        double side = switch (limb) {
            case LEFT_ARM, LEFT_FOREARM, LEFT_HAND, LEFT_LEG -> -0.38;
            case RIGHT_ARM, RIGHT_FOREARM, RIGHT_HAND, RIGHT_LEG -> 0.38;
            default -> 0.0;
        };
        double y = switch (limb) {
            case HEAD, SKULL -> entity.getBbHeight() * 0.85;
            case LEFT_LEG, RIGHT_LEG -> entity.getBbHeight() * 0.28;
            case TORSO -> entity.getBbHeight() * 0.55;
            default -> entity.getBbHeight() * 0.62;
        };
        double forward = 0.12;
        if (entity.deathTime > 0 && entity instanceof Mob mob) {
            ZombieRagdoll.Pose pose = ZombieRagdoll.get(mob);
            float fall = pose == null ? 1.0F : Mth.clamp(pose.fall / 1.38F, 0.0F, 1.0F);
            y = Mth.lerp(fall, (float) y, 0.22F);
            forward += fall * 0.85;
        }
        return new Vec3(entity.getX(), entity.getY() + y, entity.getZ()).add(right.scale(side)).add(look.scale(forward));
    }

    private static Vec3 direction(LivingEntity entity, GoreManager.Limb limb) {
        Vec3 look = Vec3.directionFromRotation(0.0F, entity.getYRot());
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
        right = right.normalize();
        Vec3 up = new Vec3(0, 1, 0);
        return switch (limb) {
            case HEAD, SKULL -> up.scale(0.85).add(look.scale(0.35));
            case LEFT_ARM, LEFT_FOREARM, LEFT_HAND -> right.scale(-0.75).add(up.scale(0.55)).add(look.scale(0.2));
            case RIGHT_ARM, RIGHT_FOREARM, RIGHT_HAND -> right.scale(0.75).add(up.scale(0.55)).add(look.scale(0.2));
            case LEFT_LEG -> right.scale(-0.45).add(up.scale(0.15)).add(look.scale(0.25));
            case RIGHT_LEG -> right.scale(0.45).add(up.scale(0.15)).add(look.scale(0.25));
            case TORSO -> look.scale(0.55).add(up.scale(0.7));
        };
    }

    private static final class Jet {
        private final int entityId;
        private final GoreManager.Limb limb;
        private int left;

        private Jet(int entityId, GoreManager.Limb limb, int ticks) {
            this.entityId = entityId;
            this.limb = limb;
            this.left = ticks;
        }

        private boolean tick(ClientLevel level) {
            RandomSource random = level.random;
            Vec3 origin;
            Vec3 dir;
            if (level.getEntity(this.entityId) instanceof LivingEntity living) {
                origin = BloodJets.origin(living, this.limb);
                dir = BloodJets.direction(living, this.limb).normalize();
            } else {
                return true;
            }
            for (int i = 0; i < 5; i++) {
                double power = 0.42 + random.nextDouble() * 0.7;
                double fade = this.left / 16.0;
                Vec3 vel = dir.scale(power * (0.55 + fade)).add(
                        (random.nextDouble() - 0.5) * 0.16,
                        random.nextDouble() * 0.12,
                        (random.nextDouble() - 0.5) * 0.16);
                level.addParticle(ZombieroolModParticleTypes.BLOOD_STAIN.get(),
                        origin.x + (random.nextDouble() - 0.5) * 0.12,
                        origin.y + (random.nextDouble() - 0.5) * 0.12,
                        origin.z + (random.nextDouble() - 0.5) * 0.12,
                        vel.x, vel.y, vel.z);
            }
            return --this.left <= 0;
        }
    }
}
