package me.cryo.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraft.world.level.Level;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import me.cryo.zombierool.init.ZombieroolModEntities;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import java.util.Random;

public class ZombieEntity extends AbstractZombieRoolEntity {
    public static final EntityType<ZombieEntity> TYPE = ZombieroolModEntities.ZOMBIE.get();

    private int ambientSoundCooldown = 0;
    private double baseSpeed = 0.18;
    private static final Random STATIC_RANDOM = new Random();

    private int lightUpdateTimer = 0;
    private static final EntityDataAccessor<Boolean> IS_SUPER_SPRINTER = SynchedEntityData.defineId(ZombieEntity.class, EntityDataSerializers.BOOLEAN);

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
    }

    public boolean isSuperSprinter() {
        return this.entityData.get(IS_SUPER_SPRINTER);
    }

    public boolean hasHalloweenLight() {
        return this.getPersistentData().getBoolean("zombierool:halloween_light");
    }

    public void setSuperSprinter(boolean value) {
        this.entityData.set(IS_SUPER_SPRINTER, value);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
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
                ParticleTypes.FLAME,
                this.getX(),
                this.getY() + this.getBbHeight() * 0.95,
                this.getZ(),
                1,
                0.15, 0.1, 0.15,
                0.001
            );
            if (this.random.nextFloat() < 0.3f) {
                serverLevel.sendParticles(
                    ParticleTypes.LAVA,
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
                return this.mob.getBbWidth() * this.mob.getBbWidth() + entity.getBbWidth();
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

        if (currentSpeed > 0.22) {
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
        // Fix des dégâts : le Zombie inflige toujours 2.0 (1 coeur entier)
        float damage = this.isSuperSprinter() ? 4.0f : 2.0f;
        boolean flag = entity.hurt(this.damageSources().mobAttack(this), damage);

        if (flag && !this.level().isClientSide) {
            int idx = 1 + this.random.nextInt(16);
            SoundEvent attackSound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "attack" + idx));
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
        int idx = 1 + this.random.nextInt(2);
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "hurt" + idx));
    }

    @Override
    public SoundEvent getDeathSound() {
        int idx = 1 + this.random.nextInt(10);
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "death" + idx));
    }

    public static void init() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.18);
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
            } else if (globalBehindYouSoundCooldown == 0) {
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

            List<ZombieEntity> nearbyZombies = this.level().getEntitiesOfClass(
                ZombieEntity.class,
                this.getBoundingBox().inflate(0.3, 0.1, 0.3)
            );

            for (ZombieEntity other : nearbyZombies) {
                if (other == this) continue;
                double distSqr = this.distanceToSqr(other);
                if (distSqr < 0.25) {
                    Vec3 direction = this.position().subtract(other.position()).normalize();
                    float push = 0.025f;
                    other.push(direction.x * push, 0, direction.z * push);
                    this.push(-direction.x * push, 0, -direction.z * push);
                    if (Math.abs(direction.x) < 0.1) {
                        this.push((this.random.nextBoolean() ? 1 : -1) * 0.02, 0, 0);
                    }
                    if (Math.abs(direction.z) < 0.1) {
                        this.push(0, 0, (this.random.nextBoolean() ? 1 : -1) * 0.02);
                    }
                }
            }
            
            updateHalloweenLight();
        }
    }
}