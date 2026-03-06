package me.cryo.zombierool.core.manager;

import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.SyncGorePacket;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

public class GoreManager {

    private static final Random RANDOM = new Random();

    public static final String TAG_NO_HEAD = "zr_gore_no_head";
    public static final String TAG_NO_LEFT_ARM = "zr_gore_no_l_arm";
    public static final String TAG_NO_RIGHT_ARM = "zr_gore_no_r_arm";
    public static final String TAG_NO_LEFT_LEG = "zr_gore_no_l_leg";
    public static final String TAG_NO_RIGHT_LEG = "zr_gore_no_r_leg";

    private static final float MIN_DAMAGE_FOR_DISMEMBERMENT = 6.0f; 
    private static final float CHANCE_TO_DISMEMBER = 0.35f; 

    public enum Limb {
        HEAD, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG
    }

    public static void triggerHeadExplosion(LivingEntity entity) {
        if (entity.level().isClientSide || hasLostLimb(entity, Limb.HEAD)) return;

        setLimbLost(entity, Limb.HEAD, true);

        entity.level().playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(),
                SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.5f, 0.8f);

        if (ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation("zombierool", "death_beurk1"))) {
            entity.level().playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "death_beurk1")),
                    SoundSource.HOSTILE, 1.0f, 1.0f);
        }

        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                    entity.getX(), entity.getEyeY(), entity.getZ(),
                    60, 0.25, 0.25, 0.25, 0.15);
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    entity.getX(), entity.getEyeY(), entity.getZ(),
                    5, 0.1, 0.1, 0.1, 0.05);
        }

        syncGoreToClient(entity);
    }

    public static void tryDismemberLimb(LivingEntity entity, float damageAmount) {
        if (entity.level().isClientSide) return;

        if (damageAmount < MIN_DAMAGE_FOR_DISMEMBERMENT) return;

        if (RANDOM.nextFloat() < CHANCE_TO_DISMEMBER) {
            Limb[] limbs = {Limb.LEFT_ARM, Limb.RIGHT_ARM}; 
            Limb targetLimb = limbs[RANDOM.nextInt(limbs.length)];

            if (!hasLostLimb(entity, targetLimb)) {
                setLimbLost(entity, targetLimb, true);

                entity.level().playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.5f, 1.5f);

                if (entity.level() instanceof ServerLevel serverLevel) {
                    serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()),
                            entity.getX(), entity.getY() + entity.getBbHeight() / 1.5, entity.getZ(),
                            35, 0.2, 0.2, 0.2, 0.1);
                }

                syncGoreToClient(entity);
            }
        }
    }

    private static void setLimbLost(LivingEntity entity, Limb limb, boolean lost) {
        CompoundTag data = entity.getPersistentData();
        switch (limb) {
            case HEAD -> data.putBoolean(TAG_NO_HEAD, lost);
            case LEFT_ARM -> data.putBoolean(TAG_NO_LEFT_ARM, lost);
            case RIGHT_ARM -> data.putBoolean(TAG_NO_RIGHT_ARM, lost);
            case LEFT_LEG -> data.putBoolean(TAG_NO_LEFT_LEG, lost);
            case RIGHT_LEG -> data.putBoolean(TAG_NO_RIGHT_LEG, lost);
        }
    }

    public static boolean hasLostLimb(LivingEntity entity, Limb limb) {
        CompoundTag data = entity.getPersistentData();
        return switch (limb) {
            case HEAD -> data.getBoolean(TAG_NO_HEAD);
            case LEFT_ARM -> data.getBoolean(TAG_NO_LEFT_ARM);
            case RIGHT_ARM -> data.getBoolean(TAG_NO_RIGHT_ARM);
            case LEFT_LEG -> data.getBoolean(TAG_NO_LEFT_LEG);
            case RIGHT_LEG -> data.getBoolean(TAG_NO_RIGHT_LEG);
        };
    }

    private static void syncGoreToClient(LivingEntity entity) {
        if (entity.level().isClientSide) return;

        CompoundTag goreData = new CompoundTag();
        goreData.putBoolean("h", hasLostLimb(entity, Limb.HEAD));
        goreData.putBoolean("la", hasLostLimb(entity, Limb.LEFT_ARM));
        goreData.putBoolean("ra", hasLostLimb(entity, Limb.RIGHT_ARM));

        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                new SyncGorePacket(entity.getId(), goreData));
    }
}