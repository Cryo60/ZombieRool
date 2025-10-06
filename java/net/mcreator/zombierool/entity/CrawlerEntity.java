package net.mcreator.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;

import net.minecraft.world.level.Level;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
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
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.mcreator.zombierool.WaveManager;
import net.mcreator.zombierool.init.ZombieroolModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.mcreator.zombierool.bonuses.BonusManager;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;

import org.joml.Vector3f;

public class CrawlerEntity extends Monster {
    private boolean headshotDeath = false;
    private int headshotDeathTicks = 0;

    private DamageSource headshotSource;
    private boolean hasTriggeredHeadshotKill = false;

    private int ambientSoundCooldown = 0;
    private static final int CRAWLER_AMBIENT_COOLDOWN_TICKS = 20 * 5;

    // ==================== HALLOWEEN - SYNCHRONISATION ====================
    private static final EntityDataAccessor<Boolean> HALLOWEEN_SKIN = 
        SynchedEntityData.defineId(CrawlerEntity.class, EntityDataSerializers.BOOLEAN);

    public CrawlerEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.CRAWLER.get(), world);
    }

    public CrawlerEntity(EntityType<CrawlerEntity> type, Level world) {
        super(type, world);
        setMaxUpStep(0.6f);
        xpReward = 0;
        setNoAi(false);
        setPersistenceRequired();
        this.refreshDimensions();
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(HALLOWEEN_SKIN, false);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.6f, 0.6f);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions dimensions) {
        return 0.3f;
    }

    public void setHeadshotDeath(boolean value) {
        this.headshotDeath = value;
        if (value) {
            this.headshotDeathTicks = 0;
        }
    }

    public boolean isHeadshotDeath() {
        return headshotDeath;
    }

    // ==================== MÉTHODES HALLOWEEN ====================
    
    /**
     * Active ou désactive le skin Halloween
     * @param halloween true pour activer le skin Halloween
     */
    public void setHalloweenSkin(boolean halloween) {
        this.entityData.set(HALLOWEEN_SKIN, halloween);
    }

    /**
     * Vérifie si le skin Halloween est actif
     * @return true si le skin Halloween est actif
     */
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

    // ==================== FIN MÉTHODES HALLOWEEN ====================

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(1, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false, true) {
            @Override
            public boolean canContinueToUse() {
                LivingEntity target = this.mob.getTarget();
                return target != null && target.isAlive();
            }
        });

        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, false) {
            @Override
            public boolean canContinueToUse() {
                LivingEntity target = this.mob.getTarget();
                return target != null && target.isAlive();
            }

            @Override
            protected double getAttackReachSqr(LivingEntity target) {
                return 4.0D;
            }
        });
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
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
        }
        return flag;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.level().isClientSide) {
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(),
                    5,
                    0.1D, 0.1D, 0.1D,
                    0.01D
                );
            }
        }

        if (source.getDirectEntity() instanceof Player player && BonusManager.isInstaKillActive(player)) {
            return super.hurt(source, Float.MAX_VALUE);
        }

        if (source.getDirectEntity() instanceof ThrownPotion
         || source.getDirectEntity() instanceof AreaEffectCloud
         || source.is(DamageTypes.FALL)) {
            return false;
        }

        boolean headshot = false;
        if (source.getDirectEntity() instanceof Projectile p) {
            headshot = p.getY() >= this.getY() + this.getBbHeight() * 0.85;
        } else if (source.getSourcePosition() != null) {
            headshot = source.getSourcePosition().y >= this.getY() + this.getBbHeight() * 0.85;
        }

        if (headshot) {
            boolean lethal = amount >= this.getHealth();
            if (lethal && !this.headshotDeath) {
                this.headshotSource = source;
                this.headshotDeath = true;
                this.headshotDeathTicks = 0;
                this.hasTriggeredHeadshotKill = false;

                if (!this.level().isClientSide) {
                    this.level().broadcastEntityEvent(this, (byte) 99);
                }
            }
        }

        return super.hurt(source, amount);
    }


    @Override
    public void handleEntityEvent(byte id) {
        super.handleEntityEvent(id);
        if (id == 99 && this.level().isClientSide) {
            this.setHeadshotDeath(true);
            double x = this.getX(), y = this.getY() + this.getBbHeight() * 0.9, z = this.getZ();
            for (int i = 0; i < 30; i++) {
                double dx = (this.random.nextDouble() - 0.5) * this.getBbWidth();
                double dy = this.random.nextDouble() * 0.5;
                double dz = (this.random.nextDouble() - 0.5) * this.getBbWidth();
                this.level().addParticle(
                    new DustParticleOptions(new Vector3f(1f, 0f, 0f), 1f),
                    x + dx, y + dy, z + dz,
                    dx * 0.2, dy * 0.2, dz * 0.2
                );
            }
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(
                new ResourceLocation("zombierool", "death_beurk1")
            );
            if (sound != null) {
                this.level().playLocalSound(x, y, z, sound, net.minecraft.sounds.SoundSource.HOSTILE, 1f, 1f, false);
            }
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.headshotDeath) {
            headshotDeathTicks++;
        }

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
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit) {
        super.dropCustomDeathLoot(source, looting, recentlyHit);

    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            WaveManager.onMobDeath(this, serverLevel);

            boolean killedByWhisperLastBullet = false;
            Entity directSource = cause.getDirectEntity();

            if (directSource instanceof net.minecraft.world.entity.projectile.AbstractArrow proj) {
                if (proj.getPersistentData().getBoolean("zombierool:last_whisper_bullet")) {
                    killedByWhisperLastBullet = true;
                }
            }

            if (killedByWhisperLastBullet) {
                serverLevel.sendParticles(
                    ParticleTypes.SMOKE,
                    this.getX(),
                    this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(),
                    50,
                    0.3D, 0.5D, 0.3D,
                    0.1D
                );
                serverLevel.sendParticles(
                    ParticleTypes.SQUID_INK,
                    this.getX(),
                    this.getY() + this.getBbHeight() / 2.0D,
                    this.getZ(),
                    30,
                    0.2D, 0.4D, 0.2D,
                    0.05D
                );
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                                       ZombieroolModSounds.CROW_WAVE.get(),
                                       net.minecraft.sounds.SoundSource.HOSTILE, 1.0f, 0.8f + this.random.nextFloat() * 0.4f);
            } else { // Nuage d'effet toxique si ce n'est PAS la dernière balle du Whisper
                AreaEffectCloud cloud = new AreaEffectCloud(serverLevel, this.getX(), this.getY() + 0.5, this.getZ());
                cloud.setOwner(this);

                cloud.setRadius(2.0F);
                cloud.setDuration(240);
                cloud.setRadiusPerTick(-0.0083F);

                cloud.setParticle(ParticleTypes.DRAGON_BREATH);
                cloud.setFixedColor(0x00FF00);

                cloud.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 6));
                cloud.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                cloud.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));

                serverLevel.addFreshEntity(cloud);

                // NOUVEAU : Envoi explicite de particules pour assurer la visibilité sur tous les clients
                // Ceci est un "fallback" visuel si l'AreaEffectCloud elle-même n'est pas rendue pour une raison quelconque.
                serverLevel.sendParticles(
                    ParticleTypes.DRAGON_BREATH, // Utilise la même particule que le nuage
                    this.getX(),
                    this.getY() + 0.5D, // Au centre du nuage, légèrement au-dessus du sol
                    this.getZ(),
                    30,                 // Nombre de particules (augmenté pour plus de visibilité)
                    2.0D, 0.5D, 2.0D,   // Dispersion plus large pour simuler le nuage initial (X, Y, Z)
                    0.0D                // Vitesse des particules (0 pour qu'elles restent un instant)
                );
            }

            serverLevel.levelEvent(2001, this.blockPosition(), 0);
        }
    }

    public static void init() {}

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.19);
        builder = builder.add(Attributes.MAX_HEALTH, 5);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 1);
        builder = builder.add(Attributes.FOLLOW_RANGE, 16);
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }
}