package me.cryo.zombierool.client;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;

import java.util.HashMap;
import java.util.Map;

/**
 * Floppy collapse. The fall direction and the resting pose are picked per zombie.
 */
public final class ZombieRagdoll {
    private static final Map<Integer, Pose> POSES = new HashMap<>();

    private ZombieRagdoll() {}

    public static Pose get(LivingEntity entity) {
        if (entity.isAlive() && entity.deathTime <= 0) {
            POSES.remove(entity.getId());
            return null;
        }
        if (POSES.size() > 256) POSES.clear();
        Pose pose = POSES.computeIfAbsent(entity.getId(), Pose::new);
        float linear = net.minecraft.util.Mth.clamp(entity.deathTime / 10.0F, 0.0F, 1.0F);
        pose.settle = linear * linear * (3.0F - 2.0F * linear);
        return pose;
    }

    public static final class Pose {
        public final int style;
        public final float side;
        public float fall;
        public float headX;
        public float headY;
        public float headZ;
        public float rArmX = -1.57F;
        public float rArmY;
        public float rArmZ;
        public float lArmX = -1.57F;
        public float lArmY;
        public float lArmZ;
        public float rFore;
        public float lFore;
        public float rLeg;
        public float lLeg;
        public float bodyBend;

        public final float headXRest;
        public final float headZRest;
        public final float rArmRest;
        public final float lArmRest;
        public final float rArmZRest;
        public final float lArmZRest;
        public final float rLegRest;
        public final float lLegRest;
        public final float foreRest;
        public float settle;
        private float fallVel;
        private float headVel;
        private float rArmVel;
        private float lArmVel;
        private float rLegVel;
        private float lLegVel;
        private int simulated;

        private Pose(int id) {
            RandomSource random = RandomSource.create(id * 341873128712L + 13L);
            this.style = random.nextInt(6);
            this.side = random.nextBoolean() ? 1.0F : -1.0F;
            float j = (random.nextFloat() - 0.5F) * 0.25F;
            switch (this.style) {
                case 1 -> {
                    this.headXRest = -0.7F + j;
                    this.headZRest = this.side * 0.35F;
                    this.rArmRest = -0.4F;
                    this.lArmRest = -0.3F;
                    this.rArmZRest = 1.35F * this.side;
                    this.lArmZRest = -1.35F * this.side;
                    this.rLegRest = 0.9F;
                    this.lLegRest = 1.15F;
                    this.foreRest = 0.35F;
                }
                case 2 -> {
                    this.headXRest = 0.5F + j;
                    this.headZRest = this.side * 1.15F;
                    this.rArmRest = -2.1F;
                    this.lArmRest = -0.2F;
                    this.rArmZRest = 0.2F * this.side;
                    this.lArmZRest = -1.1F * this.side;
                    this.rLegRest = 1.25F;
                    this.lLegRest = 1.45F;
                    this.foreRest = 1.35F;
                }
                case 3 -> {
                    this.headXRest = 0.2F + j;
                    this.headZRest = -this.side * 0.9F;
                    this.rArmRest = -0.15F;
                    this.lArmRest = -2.3F;
                    this.rArmZRest = 1.5F * this.side;
                    this.lArmZRest = 0.15F * this.side;
                    this.rLegRest = -0.85F;
                    this.lLegRest = 0.7F;
                    this.foreRest = 0.9F;
                }
                case 4 -> {
                    this.headXRest = 1.25F + j;
                    this.headZRest = this.side * 0.25F;
                    this.rArmRest = -2.0F;
                    this.lArmRest = -1.85F;
                    this.rArmZRest = 0.45F * this.side;
                    this.lArmZRest = -0.45F * this.side;
                    this.rLegRest = 1.5F;
                    this.lLegRest = 1.35F;
                    this.foreRest = 1.55F;
                }
                case 5 -> {
                    this.headXRest = 0.15F + j;
                    this.headZRest = this.side * 0.55F;
                    this.rArmRest = -0.55F;
                    this.lArmRest = -0.7F;
                    this.rArmZRest = 1.55F;
                    this.lArmZRest = -1.55F;
                    this.rLegRest = -0.55F;
                    this.lLegRest = 0.85F;
                    this.foreRest = 0.25F;
                }
                default -> {
                    this.headXRest = 0.95F + j;
                    this.headZRest = this.side * 0.15F;
                    this.rArmRest = -0.15F;
                    this.lArmRest = -0.35F;
                    this.rArmZRest = 0.25F * this.side;
                    this.lArmZRest = -0.2F * this.side;
                    this.rLegRest = 0.15F;
                    this.lLegRest = -0.1F;
                    this.foreRest = 0.45F;
                }
            }
            this.fallVel = 0.08F + random.nextFloat() * 0.2F;
            this.headVel = (random.nextFloat() - 0.5F) * 0.2F;
            this.rArmVel = (random.nextFloat() - 0.5F) * 0.7F;
            this.lArmVel = (random.nextFloat() - 0.5F) * 0.7F;
            this.rLegVel = (random.nextFloat() - 0.4F) * 0.4F;
            this.lLegVel = (random.nextFloat() - 0.4F) * 0.4F;
        }

        private void advance(int age) {
            int target = Math.min(Math.max(age, 0), 28);
            while (this.simulated < target) {
                this.simulated++;
                this.step();
            }
        }

        private void step() {
            float ground = 1.38F;
            this.fallVel += (ground - this.fall) * 0.14F + 0.02F;
            this.fallVel *= 0.72F;
            this.fall += this.fallVel;
            if (this.fall > ground) {
                this.fall = ground;
                this.fallVel *= -0.38F;
            }

            this.headVel += (this.headXRest - this.headX) * 0.18F;
            this.headVel *= 0.66F;
            this.headX += this.headVel;
            this.headY += (this.side * 0.45F - this.headY) * 0.1F;
            this.headZ += (this.headZRest - this.headZ) * 0.16F;

            this.rArmVel += (this.rArmRest - this.rArmX) * 0.14F;
            this.rArmVel *= 0.72F;
            this.rArmX += this.rArmVel;
            this.rArmY += (this.side * 0.3F - this.rArmY) * 0.1F;
            this.rArmZ += (this.rArmZRest - this.rArmZ) * 0.14F;

            this.lArmVel += (this.lArmRest - this.lArmX) * 0.14F;
            this.lArmVel *= 0.72F;
            this.lArmX += this.lArmVel;
            this.lArmY += (-this.side * 0.25F - this.lArmY) * 0.1F;
            this.lArmZ += (this.lArmZRest - this.lArmZ) * 0.14F;

            this.rFore += ((this.foreRest) - this.rFore) * 0.2F;
            this.lFore += ((this.foreRest * 0.75F) - this.lFore) * 0.2F;

            this.rLegVel += (this.rLegRest - this.rLeg) * 0.16F;
            this.rLegVel *= 0.74F;
            this.rLeg += this.rLegVel;
            this.lLegVel += (this.lLegRest - this.lLeg) * 0.16F;
            this.lLegVel *= 0.74F;
            this.lLeg += this.lLegVel;

            this.bodyBend += (0.0F - this.bodyBend) * 0.15F;
        }

        public float flipDegrees() {
            return 90.0F * this.side;
        }

        public float yawTwist() {
            return switch (this.style) {
                case 1 -> 12.0F * this.side;
                case 2 -> -18.0F * this.side;
                case 3 -> 20.0F * this.side;
                case 4 -> -10.0F * this.side;
                case 5 -> 16.0F * this.side;
                default -> 0.0F;
            };
        }
    }
}
