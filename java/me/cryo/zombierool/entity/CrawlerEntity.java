package me.cryo.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

public class CrawlerEntity extends AbstractZombieRoolEntity {
    private int ambientSoundCooldown = 0;
    private static final int CRAWLER_AMBIENT_COOLDOWN_TICKS = 20 * 5;

    private static final EntityDataAccessor<Boolean> HALLOWEEN_SKIN = 
        SynchedEntityData.defineId(CrawlerEntity.class, EntityDataSerializers.BOOLEAN);

    public CrawlerEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.CRAWLER.get(), world);
    }

    public CrawlerEntity(EntityType<CrawlerEntity> type, Level world) {
        super(type, world);
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HALLOWEEN_SKIN, false);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.55f, 0.55f);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 0.25f;
    }

    public void setHalloweenSkin(boolean halloween) {
        this.entityData.set(HALLOWEEN_SKIN, halloween);
    }

    public boolean hasHalloweenSkin() {
        return this.entityData.get(HALLOWEEN_SKIN);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("HalloweenSkin", this.entityData.get(HALLOWEEN_SKIN));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.entityData.set(HALLOWEEN_SKIN, compound.getBoolean("HalloweenSkin"));
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false, false));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.4, false));
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return null;
    }

    @Override
    public SoundEvent getHurtSound(DamageSource ds) {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_hurt"));
    }

    @Override
    public SoundEvent getDeathSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_death"));
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        boolean flag = super.doHurtTarget(entity);
        if (flag && !this.level().isClientSide) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_attack"));
            if (sound != null) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            if (entity instanceof LivingEntity le) {
                le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 1));
            }
        }
        return flag;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof ThrownPotion
         || source.getDirectEntity() instanceof AreaEffectCloud
         || source.is(DamageTypes.FALL)) {
            return false;
        }

        return super.hurt(source, amount);
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            if (ambientSoundCooldown > 0) {
                ambientSoundCooldown--;
            } else {
                SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_ambient"));
                if (sound != null) {
                    if (this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 32.0, false) != null) {
                        this.level().playSound(null, getX(), getY(), getZ(), sound, SoundSource.HOSTILE, 0.8f, 1.0f);
                    }
                }
                ambientSoundCooldown = CRAWLER_AMBIENT_COOLDOWN_TICKS + this.random.nextInt(CRAWLER_AMBIENT_COOLDOWN_TICKS);
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause); 
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            if (WorldConfig.get(serverLevel).isCrawlerGasExplosion()) {
                AreaEffectCloud cloud = new AreaEffectCloud(serverLevel, this.getX(), this.getY() + 0.5, this.getZ());
                cloud.setOwner(this);
                cloud.setRadius(2.5F);
                cloud.setDuration(240);
                cloud.setRadiusPerTick(-0.01F);
                cloud.setParticle(ParticleTypes.DRAGON_BREATH);
                cloud.setFixedColor(0x22FF22);
                
                cloud.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 6));
                cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                cloud.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
                
                serverLevel.addFreshEntity(cloud);
                
                for (int i = 0; i < 60; i++) {
                    double dx = this.random.nextGaussian() * 1.5;
                    double dy = this.random.nextDouble() * 1.5;
                    double dz = this.random.nextGaussian() * 1.5;
                    serverLevel.sendParticles(ParticleTypes.ENTITY_EFFECT, this.getX() + dx, this.getY() + dy, this.getZ() + dz, 0, 0.1, 0.8, 0.1, 1.0);
                    serverLevel.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, this.getX() + dx, this.getY() + dy, this.getZ() + dz, 1, 0, 0, 0, 0.01);
                }
            }

            serverLevel.sendParticles(
                ParticleTypes.LARGE_SMOKE,
                this.getX(),
                this.getY() + this.getBbHeight() / 2.0D,
                this.getZ(),
                15,
                0.3D, 0.3D, 0.3D,
                0.02D
            );
            serverLevel.levelEvent(2001, this.blockPosition(), 0);
        }
    }

    public static void init() {}

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.22);
        builder = builder.add(Attributes.MAX_HEALTH, 5);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 1);
        builder = builder.add(Attributes.FOLLOW_RANGE, 1024.0); 
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }
}