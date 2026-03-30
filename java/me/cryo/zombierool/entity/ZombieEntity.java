package me.cryo.zombierool.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.core.manager.GoreManager;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.particles.DustParticleOptions;
import org.joml.Vector3f;

public class ZombieEntity extends AbstractZombieRoolEntity {
    public static final EntityType<ZombieEntity> TYPE = ZombieroolModEntities.ZOMBIE.get();
    private int ambientSoundCooldown = 0;
    private double baseSpeed = 0.23;
    private static final Random STATIC_RANDOM = new Random();
    private int lightUpdateTimer = 0;

    private static final EntityDataAccessor<Boolean> IS_SUPER_SPRINTER = SynchedEntityData.defineId(ZombieEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> IS_FAST_CRAWLER = SynchedEntityData.defineId(ZombieEntity.class, EntityDataSerializers.BOOLEAN);

    private int behindYouSoundCooldown = 0;
    private static int globalBehindYouSoundCooldown = 0;
    private static final int BEHIND_YOU_MIN_COOLDOWN_TICKS = 20 * 5;
    private static final int BEHIND_YOU_MAX_COOLDOWN_TICKS = 20 * 15;
    private static final double BEHIND_YOU_CHECK_RADIUS = 8.0;
    private static final float BEHIND_YOU_CHANCE = 0.005f;
    private static final double INSTANT_BEHIND_YOU_DISTANCE = 1.5;
    private static final double INSTANT_BEHIND_YOU_DOT_PRODUCT_THRESHOLD = -0.7;

    public ZombieEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.ZOMBIE.get(), world);
    }

    public ZombieEntity(EntityType<ZombieEntity> type, Level world) {
        super(type, world);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(IS_SUPER_SPRINTER, false);
        this.entityData.define(IS_FAST_CRAWLER, false);
    }

    public boolean isSuperSprinter() {
        return this.entityData.get(IS_SUPER_SPRINTER);
    }

    public void setSuperSprinter(boolean value) {
        this.entityData.set(IS_SUPER_SPRINTER, value);
    }

    public boolean isFastCrawler() {
        return this.entityData.get(IS_FAST_CRAWLER);
    }

    public void setFastCrawler(boolean value) {
        this.entityData.set(IS_FAST_CRAWLER, value);
    }

    public boolean hasHalloweenLight() {
        return this.getPersistentData().getBoolean("zombierool:halloween_light");
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("IsFastCrawler", this.isFastCrawler());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if (compound.contains("IsFastCrawler")) {
            this.setFastCrawler(compound.getBoolean("IsFastCrawler"));
        }
    }

    public boolean isCrawler() {
        return GoreManager.hasLostLimb(this, GoreManager.Limb.LEFT_LEG) && GoreManager.hasLostLimb(this, GoreManager.Limb.RIGHT_LEG);
    }

    public void makeCrawler() {
        if (isCrawler()) return;

        GoreManager.triggerLegsExplosion(this);
        this.setHealth(Math.min(this.getHealth(), 5.0f));
        this.setSuperSprinter(false);

        boolean isFast = this.random.nextFloat() < 0.35f;
        this.setFastCrawler(isFast);

        if (this.getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            this.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(isFast ? 0.20 : 0.12);
        }

        this.refreshDimensions();
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float scale = getScale();
        if (isCrawler()) {
            return EntityDimensions.fixed(0.6f, 0.8f).scale(scale); 
        }
        return EntityDimensions.fixed(0.6f, 1.95f).scale(scale); 
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
        if (isCrawler()) {
            return size.height * 0.7F;
        }
        return size.height * 0.92F;
    }

    public void setBaseSpeed(double speed) {
        this.baseSpeed = speed;
    }

    private void updateHalloweenLight() {
        if (!hasHalloweenLight()) return;
        if (this.level().isClientSide) return;

        lightUpdateTimer++;
        if (lightUpdateTimer < 10) return;
        lightUpdateTimer = 0;

        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.FLAME,
                this.getX(),
                this.getY() + this.getBbHeight() * 0.95,
                this.getZ(),
                1,
                0.15, 0.1, 0.15,
                0.001
            );
            
            if (this.random.nextFloat() < 0.3f) {
                serverLevel.sendParticles(
                    net.minecraft.core.particles.ParticleTypes.LAVA,
                    this.getX(),
                    this.getY() + this.getBbHeight() * 0.95,
                    this.getZ(),
                    1,
                    0.1, 0.05, 0.1,
                    0.0
                );
            }
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Player.class, false, false));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            protected double getAttackReachSqr(LivingEntity entity) {
                return (this.mob.getBbWidth() * 2.5F * this.mob.getBbWidth() * 2.5F + entity.getBbWidth());
            }
        });
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(3, new BreakDoorGoal(this, enumDifficulty -> true) {
            @Override
            public boolean canUse() {
                return super.canUse() && this.mob.getTarget() != null;
            }
        });
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEAD;
    }

    @Override
    public double getMyRidingOffset() {
        return -0.35D;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return null;
    }

    private void playAmbientBasedOnContext() {
        if (this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 32.0, false) == null) {
            return;
        }

        double currentSpeed = this.getAttribute(Attributes.MOVEMENT_SPEED).getValue();
        SoundEvent sound = null;

        if (isCrawler()) {
            if (this.isFastCrawler()) {
                sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawl"));
            } else {
                sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "slow_crawl"));
            }
        } else if (currentSpeed > 0.22) {
            int idx = 1 + this.random.nextInt(9);
            sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "sprint" + idx));
        } else {
            int idx = 1 + this.random.nextInt(10);
            sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "ambiant" + idx));
        }

        if (sound != null) {
            this.level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, 1f, 1f);
        }
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        float damage = this.isSuperSprinter() ? 4.0f : 2.0f;
        boolean flag = entity.hurt(this.damageSources().mobAttack(this), damage);
        
        if (flag && !this.level().isClientSide) {
            SoundEvent attackSound;
            
            if (isCrawler()) {
                attackSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_attack"));
            } else {
                int idx = 1 + this.random.nextInt(16);
                attackSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "attack" + idx));
            }

            if (attackSound != null) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), attackSound, SoundSource.HOSTILE, 1.0F, 1.0F);
            }

            if (entity instanceof Player) {
                if (STATIC_RANDOM.nextInt(20) == 0) {
                    SoundEvent punchSound = ZombieroolModSounds.PUNCH.get();
                    if (punchSound != null) {
                        this.level().playSound(
                            null,
                            entity.getX(), entity.getY(), entity.getZ(),
                            punchSound,
                            SoundSource.PLAYERS,
                            1.0F,
                            1.0F + (STATIC_RANDOM.nextFloat() * 0.2F - 0.1F)
                        );
                    }
                }
            }
        }
        return flag;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource source) {
        if (isCrawler()) return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_hurt"));
        int idx = 1 + this.random.nextInt(2);
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "hurt" + idx));
    }

    @Override
    public SoundEvent getDeathSound() {
        if (isCrawler()) return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_death"));
        int idx = 1 + this.random.nextInt(10);
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "death" + idx));
    }

    public static void init() {}

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.19);
        builder = builder.add(Attributes.MAX_HEALTH, 4);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 2);
        builder = builder.add(Attributes.FOLLOW_RANGE, 1024.0); 
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (ambientSoundCooldown > 0) {
                ambientSoundCooldown--;
            } else {
                playAmbientBasedOnContext();
                ambientSoundCooldown = 60 + this.random.nextInt(130);
            }

            if (globalBehindYouSoundCooldown > 0) globalBehindYouSoundCooldown--;

            if (this.behindYouSoundCooldown > 0) {
                this.behindYouSoundCooldown--;
            } else if (globalBehindYouSoundCooldown == 0 && !isCrawler()) {
                List<Player> players = this.level().getEntitiesOfClass(
                    Player.class,
                    this.getBoundingBox().inflate(BEHIND_YOU_CHECK_RADIUS)
                );

                for (Player player : players) {
                    if (!player.isAlive() || player.isSpectator()) continue;
                    if (player.hasEffect(MobEffects.BLINDNESS)) continue;

                    Vec3 playerLook = player.getLookAngle().normalize();
                    Vec3 toZombie = this.position().subtract(player.position()).normalize();
                    double dot = playerLook.dot(toZombie);
                    double distSqr = this.distanceToSqr(player);

                    boolean isBehind = dot <= INSTANT_BEHIND_YOU_DOT_PRODUCT_THRESHOLD
                        || (distSqr <= INSTANT_BEHIND_YOU_DISTANCE * INSTANT_BEHIND_YOU_DISTANCE && dot <= INSTANT_BEHIND_YOU_DOT_PRODUCT_THRESHOLD);

                    if (isBehind && this.level().random.nextFloat() < BEHIND_YOU_CHANCE) {
                        player.level().playSound(
                            null,
                            player.getX(), player.getY(), player.getZ(),
                            ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "behind_you")),
                            SoundSource.HOSTILE,
                            1.0F, 1.0F
                        );

                        int cooldown = this.random.nextInt(
                            BEHIND_YOU_MAX_COOLDOWN_TICKS - BEHIND_YOU_MIN_COOLDOWN_TICKS
                        ) + BEHIND_YOU_MIN_COOLDOWN_TICKS;
                        this.behindYouSoundCooldown = cooldown;
                        globalBehindYouSoundCooldown = cooldown;
                        break;
                    }
                }
            }

            updateHalloweenLight();
        }
    }
}