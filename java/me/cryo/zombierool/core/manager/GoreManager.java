package me.cryo.zombierool.core.manager;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.entity.BloodDecalEntity;
import me.cryo.zombierool.entity.GorePieceEntity;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncGorePacket;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
public class GoreManager {
    private static final Random RANDOM = new Random();
    public static final String TAG_NO_HEAD = "zr_gore_no_head";
    public static final String TAG_NO_LEFT_ARM = "zr_gore_no_l_arm";
    public static final String TAG_NO_RIGHT_ARM = "zr_gore_no_r_arm";
    public static final String TAG_NO_LEFT_LEG = "zr_gore_no_l_leg";
    public static final String TAG_NO_RIGHT_LEG = "zr_gore_no_r_leg";
    public static final String TAG_NO_LEFT_FOREARM = "zr_gore_no_l_fore";
    public static final String TAG_NO_RIGHT_FOREARM = "zr_gore_no_r_fore";
    public static final String TAG_NO_LEFT_HAND = "zr_gore_no_l_hand";
    public static final String TAG_NO_RIGHT_HAND = "zr_gore_no_r_hand";
    public static final String TAG_TORSO = "zr_gore_torso";
    public static final String TAG_SKULL = "zr_gore_skull";
    public static final String TAG_BIT = "zr_gore";
    private static final float MIN_DAMAGE_FOR_DISMEMBERMENT = 6.0f;
    private static final float CHANCE_TO_DISMEMBER = 0.35f;
    public enum Limb {
        HEAD, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG,
        LEFT_FOREARM, RIGHT_FOREARM, LEFT_HAND, RIGHT_HAND, TORSO, SKULL
    }
    public static void onHit(LivingEntity entity, boolean dismember) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (entity.level().isClientSide) return;
        if (entity instanceof me.cryo.zombierool.entity.HellhoundEntity) {
            ((ServerLevel)entity.level()).sendParticles(ZombieroolModParticleTypes.BLOOD_STAIN.get(),entity.getX(),entity.getY()+entity.getBbHeight()*.5,entity.getZ(),3,.1,.1,.1,.1);
            return;
        }
        spray(entity, 8, entity.getY() + entity.getBbHeight() * 0.6);
        if (dismember && RANDOM.nextFloat() < 0.45f) {
            severRandomPiece(entity, false);
        }
    }
    public static void onChar(LivingEntity entity) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (!(entity.level() instanceof ServerLevel server)) return;
        server.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                12, 0.25, 0.2, 0.25, 0.01);
        server.sendParticles(ParticleTypes.SMOKE, entity.getX(), entity.getY() + entity.getBbHeight() * 0.4, entity.getZ(),
                8, 0.2, 0.15, 0.2, 0.01);
    }
    public static void onDeath(LivingEntity entity, boolean dismember) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (entity.level().isClientSide) return;
        if (dismember && entity.level() instanceof ServerLevel server) {
            int skin = Math.floorMod(entity.getId(), 5);
            String custom = entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zombie ? zombie.getCustomSkin() : "";
            for (int i = 0; i < 8; i++) {
                GorePieceEntity.spawn(server, entity, GorePieceEntity.MEAT, skin, custom);
            }
        }
        spray(entity, 16, entity.getY() + entity.getBbHeight() * 0.55);
        boolean headshot = entity.getPersistentData().getBoolean(DamageManager.HEADSHOT_TAG);
        if (dismember && !headshot && !hasLostLimb(entity, Limb.HEAD)) {
            severRandomPiece(entity, false);
            severRandomPiece(entity, false);
        }
    }
    public static void triggerHeadExplosion(LivingEntity entity) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (entity instanceof me.cryo.zombierool.entity.HellhoundEntity || entity.level().isClientSide || hasLostLimb(entity, Limb.HEAD)) return;
        setLimbLost(entity, Limb.HEAD, true);
        entity.level().playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(),
                SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.5f, 0.8f);
        if (ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation("zombierool", "death_beurk1"))) {
            entity.level().playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "death_beurk1")),
                    SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    entity.getX(), entity.getEyeY(), entity.getZ(),
                    5, 0.1, 0.1, 0.1, 0.05);
        }
        launch(entity, Limb.HEAD);
        syncGoreToClient(entity);
    }
    public static void triggerArmExplosion(LivingEntity entity, Limb arm) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (entity.level().isClientSide || hasLostLimb(entity, arm)) return;
        setLimbLost(entity, arm, true);
        entity.level().playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.0f, 0.8f);
        if (ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation("zombierool", "death_beurk1"))) {
            entity.level().playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() * 0.6, entity.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "death_beurk1")),
                    SoundSource.HOSTILE, 0.8f, 1.0f);
        }
        launch(entity, arm);
        syncGoreToClient(entity);
    }
    public static void triggerLegsExplosion(LivingEntity entity) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (entity instanceof me.cryo.zombierool.entity.HellhoundEntity || entity.level().isClientSide || (hasLostLimb(entity, Limb.LEFT_LEG) && hasLostLimb(entity, Limb.RIGHT_LEG))) return;
        setLimbLost(entity, Limb.LEFT_LEG, true);
        setLimbLost(entity, Limb.RIGHT_LEG, true);
        entity.level().playSound(null, entity.getX(), entity.getY() + 0.2, entity.getZ(),
                SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 1.5f, 0.8f);
        if (ForgeRegistries.SOUND_EVENTS.containsKey(new ResourceLocation("zombierool", "death_beurk1"))) {
            entity.level().playSound(null, entity.getX(), entity.getY() + 0.2, entity.getZ(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "death_beurk1")),
                    SoundSource.HOSTILE, 1.0f, 1.0f);
        }
        if (entity.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()),
                    entity.getX(), entity.getY() + 0.2, entity.getZ(),
                    6, 0.2, 0.1, 0.2, 0.05);
        }
        launch(entity, Limb.LEFT_LEG);
        launch(entity, Limb.RIGHT_LEG);
        syncGoreToClient(entity);
    }
    public static void tryDismemberLimb(LivingEntity entity, float damageAmount) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (entity.level().isClientSide) return;
        if (entity instanceof me.cryo.zombierool.entity.HellhoundEntity || damageAmount < MIN_DAMAGE_FOR_DISMEMBERMENT) return;
        if (RANDOM.nextFloat() < CHANCE_TO_DISMEMBER) {
            Limb[] limbs = {Limb.LEFT_ARM, Limb.RIGHT_ARM}; 
            Limb targetLimb = limbs[RANDOM.nextInt(limbs.length)];
            if (!hasLostLimb(entity, targetLimb)) {
                setLimbLost(entity, targetLimb, true);
                entity.level().playSound(null, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 0.5f, 1.5f);
                launch(entity, targetLimb);
                syncGoreToClient(entity);
            }
        }
    }
    private static int bit(Limb limb) {
        return switch (limb) {
            case HEAD -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_HEAD;
            case LEFT_ARM -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_LEFT_ARM;
            case RIGHT_ARM -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_RIGHT_ARM;
            case LEFT_LEG -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_LEFT_LEG;
            case RIGHT_LEG -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_RIGHT_LEG;
            case LEFT_FOREARM -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_LEFT_FOREARM;
            case RIGHT_FOREARM -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_RIGHT_FOREARM;
            case LEFT_HAND -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_LEFT_HAND;
            case RIGHT_HAND -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_RIGHT_HAND;
            case TORSO -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_TORSO;
            case SKULL -> me.cryo.zombierool.entity.AbstractZombieRoolEntity.GORE_SKULL;
        };
    }
    private static void setLimbLost(LivingEntity entity, Limb limb, boolean lost) {
        CompoundTag data = entity.getPersistentData();
        switch (limb) {
            case HEAD -> data.putBoolean(TAG_NO_HEAD, lost);
            case LEFT_ARM -> data.putBoolean(TAG_NO_LEFT_ARM, lost);
            case RIGHT_ARM -> data.putBoolean(TAG_NO_RIGHT_ARM, lost);
            case LEFT_LEG -> data.putBoolean(TAG_NO_LEFT_LEG, lost);
            case RIGHT_LEG -> data.putBoolean(TAG_NO_RIGHT_LEG, lost);
            case LEFT_FOREARM -> data.putBoolean(TAG_NO_LEFT_FOREARM, lost);
            case RIGHT_FOREARM -> data.putBoolean(TAG_NO_RIGHT_FOREARM, lost);
            case LEFT_HAND -> data.putBoolean(TAG_NO_LEFT_HAND, lost);
            case RIGHT_HAND -> data.putBoolean(TAG_NO_RIGHT_HAND, lost);
            case TORSO -> data.putBoolean(TAG_TORSO, lost);
            case SKULL -> data.putBoolean(TAG_SKULL, lost);
        }
        if (entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zombie) {
            zombie.setGoreBit(bit(limb), lost);
        }
    }
    public static boolean hasLostLimb(LivingEntity entity, Limb limb) {
        CompoundTag data = entity.getPersistentData();
        boolean tagged = switch (limb) {
            case HEAD -> data.getBoolean(TAG_NO_HEAD);
            case LEFT_ARM -> data.getBoolean(TAG_NO_LEFT_ARM);
            case RIGHT_ARM -> data.getBoolean(TAG_NO_RIGHT_ARM);
            case LEFT_LEG -> data.getBoolean(TAG_NO_LEFT_LEG);
            case RIGHT_LEG -> data.getBoolean(TAG_NO_RIGHT_LEG);
            case LEFT_FOREARM -> data.getBoolean(TAG_NO_LEFT_FOREARM);
            case RIGHT_FOREARM -> data.getBoolean(TAG_NO_RIGHT_FOREARM);
            case LEFT_HAND -> data.getBoolean(TAG_NO_LEFT_HAND);
            case RIGHT_HAND -> data.getBoolean(TAG_NO_RIGHT_HAND);
            case TORSO -> data.getBoolean(TAG_TORSO);
            case SKULL -> data.getBoolean(TAG_SKULL);
        };
        if (entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zombie) {
            return tagged || zombie.hasGoreBit(bit(limb));
        }
        return tagged;
    }
    private static CompoundTag goreTag(LivingEntity entity) {
        CompoundTag goreData = new CompoundTag();
        goreData.putBoolean("h", hasLostLimb(entity, Limb.HEAD));
        goreData.putBoolean("la", hasLostLimb(entity, Limb.LEFT_ARM));
        goreData.putBoolean("ra", hasLostLimb(entity, Limb.RIGHT_ARM));
        goreData.putBoolean("ll", hasLostLimb(entity, Limb.LEFT_LEG));
        goreData.putBoolean("rl", hasLostLimb(entity, Limb.RIGHT_LEG));
        goreData.putBoolean("lf", hasLostLimb(entity, Limb.LEFT_FOREARM));
        goreData.putBoolean("rf", hasLostLimb(entity, Limb.RIGHT_FOREARM));
        goreData.putBoolean("lh", hasLostLimb(entity, Limb.LEFT_HAND));
        goreData.putBoolean("rh", hasLostLimb(entity, Limb.RIGHT_HAND));
        goreData.putBoolean("to", hasLostLimb(entity, Limb.TORSO));
        goreData.putBoolean("sk", hasLostLimb(entity, Limb.SKULL));
        return goreData;
    }
    private static boolean anyLimbLost(LivingEntity entity) {
        for (Limb limb : Limb.values()) {
            if (hasLostLimb(entity, limb)) return true;
        }
        return false;
    }
    private static void syncGoreToClient(LivingEntity entity) {
        if (entity.level().isClientSide) return;
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity),
                new S2CSyncGorePacket(entity.getId(), goreTag(entity)));
    }
    public static void syncToPlayer(LivingEntity entity, ServerPlayer player) {
        if (entity.level().isClientSide || player == null || !anyLimbLost(entity)) return;
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncGorePacket(entity.getId(), goreTag(entity)));
    }
    public static void clearWorld(ServerLevel level) {
        List<Entity> gone = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof BloodDecalEntity || entity instanceof GorePieceEntity
                    || entity instanceof me.cryo.zombierool.entity.CrawlerCorpse
                    || (entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zombie && !zombie.isAlive())) gone.add(entity);
        }
        gone.forEach(Entity::discard);
    }
    public static boolean canDismember(net.minecraft.world.damagesource.DamageSource source) {
        if (source.is(net.minecraft.tags.DamageTypeTags.IS_EXPLOSION)) return true;
        if (!(source.getEntity() instanceof LivingEntity attacker)) return false;
        if (!me.cryo.zombierool.core.system.WeaponFacade.isWeapon(attacker.getMainHandItem())) return false;
        var def = me.cryo.zombierool.core.system.WeaponFacade.getDefinition(attacker.getMainHandItem());
        return canDismember(def);
    }
    public static boolean canDismember(me.cryo.zombierool.core.system.WeaponSystem.Definition def) {
        if (def == null) return true;
        // Use base weapon power: headshots, insta-kill and low victim health must not turn a pistol into a heavy weapon.
        String id = def.id == null ? "" : def.id.toLowerCase(java.util.Locale.ROOT).replaceFirst("^[^:]+:", "");
        return !java.util.Set.of("m1911", "mauserc96", "mauser_c96", "c96").contains(id) && !"MELEE".equalsIgnoreCase(def.type);
    }
    private static void spray(LivingEntity entity, int mist, double y) {
        if (entity instanceof me.cryo.zombierool.entity.MrChiefEntity) return;
        if (!(entity.level() instanceof ServerLevel server)) return;
        BloodDecalEntity.pool(server, entity.getX(), entity.getY(), entity.getZ(), 1);
        server.sendParticles(ZombieroolModParticleTypes.BLOOD_STAIN.get(),
                entity.getX(), y, entity.getZ(), mist, 0.18, 0.12, 0.18, 0.42);
    }
    private static void launch(LivingEntity entity, Limb limb) {
        if (!(entity.level() instanceof ServerLevel server)) return;
        int skin = Math.floorMod(entity.getId(), 5);
        String custom = entity instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity zombie ? zombie.getCustomSkin() : "";
        byte kind = switch (limb) {
            case RIGHT_ARM -> GorePieceEntity.RIGHT_UPPER;
            case LEFT_ARM -> GorePieceEntity.LEFT_UPPER;
            case RIGHT_FOREARM -> GorePieceEntity.RIGHT_FORE;
            case LEFT_FOREARM -> GorePieceEntity.LEFT_FORE;
            case RIGHT_HAND -> GorePieceEntity.RIGHT_HAND;
            case LEFT_HAND -> GorePieceEntity.LEFT_HAND;
            case TORSO -> GorePieceEntity.TORSO;
            case SKULL -> GorePieceEntity.SKULL;
            case HEAD -> GorePieceEntity.SKULL;
            default -> GorePieceEntity.MEAT;
        };
        GorePieceEntity.spawn(server, entity, kind, skin, custom);
        if (limb == Limb.HEAD) {
            GorePieceEntity.spawn(server, entity, GorePieceEntity.JAW, skin, custom);
        }
        if (limb == Limb.RIGHT_ARM || limb == Limb.LEFT_ARM) {
            GorePieceEntity.spawn(server, entity, limb == Limb.RIGHT_ARM ? GorePieceEntity.RIGHT_FORE : GorePieceEntity.LEFT_FORE, skin, custom);
            GorePieceEntity.spawn(server, entity, limb == Limb.RIGHT_ARM ? GorePieceEntity.RIGHT_HAND : GorePieceEntity.LEFT_HAND, skin, custom);
        }
        GorePieceEntity.spawn(server, entity, GorePieceEntity.MEAT, skin, custom);
    }
    private static void severRandomPiece(LivingEntity entity, boolean death) {
        Limb[] pool = death
                ? new Limb[] {Limb.LEFT_ARM, Limb.RIGHT_ARM, Limb.LEFT_FOREARM, Limb.RIGHT_FOREARM,
                    Limb.LEFT_HAND, Limb.RIGHT_HAND, Limb.TORSO, Limb.SKULL, Limb.HEAD, Limb.LEFT_LEG, Limb.RIGHT_LEG}
                : new Limb[] {Limb.LEFT_FOREARM, Limb.RIGHT_FOREARM, Limb.LEFT_HAND, Limb.RIGHT_HAND,
                    Limb.LEFT_ARM, Limb.RIGHT_ARM};
        Limb limb = pool[RANDOM.nextInt(pool.length)];
        if (hasLostLimb(entity, limb)) return;
        setLimbLost(entity, limb, true);
        launch(entity, limb);
        if (entity.level() instanceof ServerLevel server) {
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(),
                    SoundEvents.SLIME_BLOCK_BREAK, SoundSource.HOSTILE, 0.7f, 0.7f + RANDOM.nextFloat() * 0.4f);
        }
        syncGoreToClient(entity);
    }
}
