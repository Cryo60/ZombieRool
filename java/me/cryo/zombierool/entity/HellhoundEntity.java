package me.cryo.zombierool.entity;

import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.network.PlayMessages;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import me.cryo.zombierool.ExplosionControl;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import me.cryo.zombierool.WorldConfig;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.player.PlayerDownManager;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Comparator;
import net.minecraft.world.level.GameType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

public class HellhoundEntity extends AbstractZombieRoolEntity {
    private int breathSoundCooldown = 0;
    private int spawnTimer = 0;
    private boolean isFireVariant = false;
    private boolean spawnInitialized = false;

    public UUID assignedTarget = null;

    public static final EntityType<HellhoundEntity> TYPE = ZombieroolModEntities.HELLHOUND.get();

    private static final EntityDataAccessor<Boolean> REVEALED = SynchedEntityData.defineId(
        HellhoundEntity.class, EntityDataSerializers.BOOLEAN
    );

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(REVEALED, false);
    }

    public boolean isRevealedClient() {
        return this.entityData.get(REVEALED);
    }

    public HellhoundEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.HELLHOUND.get(), world);
    }

    public HellhoundEntity(EntityType<HellhoundEntity> type, Level world) {
        super(type, world);
    }

    public void setFireVariant(boolean fire) {
        this.isFireVariant = fire;
        if (fire && this.entityData.get(REVEALED)) this.setSecondsOnFire(15);
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return EntityDimensions.fixed(0.8F, 0.9F);
    }

    @Override
    protected float getStandingEyeHeight(Pose pose, EntityDimensions size) {
        return 0.7F * size.height;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.targetSelector.addGoal(1, new TargetGoal(this, false, false) {
            @Override
            public boolean canUse() {
                if (!HellhoundEntity.this.isRevealedClient()) return false;
                if (HellhoundEntity.this.assignedTarget == null) return false;
                
                Player p = HellhoundEntity.this.level().getPlayerByUUID(HellhoundEntity.this.assignedTarget);
                if (p != null && p.isAlive() && !PlayerDownManager.isPlayerDown(p.getUUID()) && !BonusManager.isZombieBloodActive(p)) {
                    if (p instanceof ServerPlayer sp) {
                        if (sp.gameMode.getGameModeForPlayer() != GameType.SURVIVAL && sp.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                            return false;
                        }
                    }
                    this.mob.setTarget(p);
                    return true;
                }
                return false;
            }
            @Override
            public boolean canContinueToUse() {
                return canUse();
            }
        });
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.4, false) {
            @Override
            public boolean canUse() {
                return HellhoundEntity.this.isRevealedClient() && super.canUse();
            }
            @Override
            public boolean canContinueToUse() {
                return HellhoundEntity.this.isRevealedClient() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this) {
            @Override
            public boolean canUse() {
                return HellhoundEntity.this.isRevealedClient() && super.canUse();
            }
        });
    }

    @Override
    public MobType getMobType() {
        return MobType.UNDEFINED;
    }

    @Override
    public SoundEvent getAmbientSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_living"));
    }

    @Override
    public void playStepSound(BlockPos pos, BlockState blockIn) {
    }

    @Override
    public SoundEvent getDeathSound() {
        return ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_mort"));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        if (!this.isRevealedClient()) return false;
        boolean flag = target.hurt(this.damageSources().mobAttack(this), 1.0f);
        if (flag) {
            this.playSound(ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_bite")), 1.0f, 1.0f);
        }
        return flag;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (!this.isRevealedClient()) return false;
        
        if (source.is(DamageTypes.IN_FIRE) ||
            source.is(DamageTypes.FALL) ||
            source.is(DamageTypes.DROWN) ||
            source.is(DamageTypes.LIGHTNING_BOLT) ||
            source.is(DamageTypes.EXPLOSION)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause); 
        
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            if (isFireVariant) {
                ExplosionControl.doCustomExplosion(this.level(), this, this.position(), 15.0f, 2.5f, 1.0f, 0.0f, 0.0f, 0.5f, "EXPLOSION", "NONE", false);
                this.level().playSound(null, this.blockPosition(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:hellhound_explosion")),
                    this.getSoundSource(), 1.0f, 1.0f);
            }
        }
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor world, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        return super.finalizeSpawn(world, difficulty, reason, spawnData, dataTag);
    }

    public static void init() {
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.3);
        builder = builder.add(Attributes.MAX_HEALTH, 6);
        builder = builder.add(Attributes.ARMOR, 0);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 1);
        builder = builder.add(Attributes.FOLLOW_RANGE, 1024.0); 
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }

    private void validateOrAssignTarget() {
        ServerLevel serverLevel = (ServerLevel) this.level();

        if (assignedTarget != null) {
            ServerPlayer p = serverLevel.getServer().getPlayerList().getPlayer(assignedTarget);
            if (p != null && p.isAlive() && !PlayerDownManager.isPlayerDown(p.getUUID()) && !BonusManager.isZombieBloodActive(p) && (p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)) {
                return; 
            }
        }

        List<ServerPlayer> validPlayers = serverLevel.getServer().getPlayerList().getPlayers().stream()
            .filter(p -> p.isAlive() && !PlayerDownManager.isPlayerDown(p.getUUID()) && !BonusManager.isZombieBloodActive(p) && (p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE))
            .collect(Collectors.toList());

        if (!validPlayers.isEmpty()) {
            Map<UUID, Integer> targetCounts = new HashMap<>();
            for (ServerPlayer p : validPlayers) targetCounts.put(p.getUUID(), 0);

            List<HellhoundEntity> hounds = serverLevel.getEntitiesOfClass(HellhoundEntity.class, this.getBoundingBox().inflate(200));
            for (HellhoundEntity h : hounds) {
                if (h != this && h.assignedTarget != null && targetCounts.containsKey(h.assignedTarget)) {
                    targetCounts.put(h.assignedTarget, targetCounts.get(h.assignedTarget) + 1);
                }
            }

            ServerPlayer chosen = validPlayers.stream()
                .min(Comparator.comparingInt(p -> targetCounts.get(p.getUUID())))
                .orElse(validPlayers.get(this.random.nextInt(validPlayers.size())));
            
            assignedTarget = chosen.getUUID();
        } else {
            assignedTarget = null;
        }
    }

    @Override
    public void travel(Vec3 travelVector) {
        if (!this.isRevealedClient()) {
            this.setDeltaMovement(Vec3.ZERO);
            super.travel(Vec3.ZERO);
            return;
        }
        super.travel(travelVector);
    }

    @Override
    public void tick() {
        super.tick();

        if (!spawnInitialized && !this.level().isClientSide()) {
            spawnInitialized = true;
            if (WorldConfig.get((ServerLevel)this.level()).isHellhoundFireVariant() && this.random.nextInt(4) == 0) {
                isFireVariant = true;
            }
            this.setInvisible(true);
            this.entityData.set(REVEALED, false);
            spawnTimer = 40;
            this.level().playSound(
                null, this.blockPosition(),
                ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_prespawn")),
                this.getSoundSource(), 1.0f, 1.0f
            );
        }

        if (this.level().isClientSide() && this.isAlive() && !this.entityData.get(REVEALED)) {
            for (int i = 0; i < 5; i++) {
                this.level().addParticle(ParticleTypes.FLAME, this.getRandomX(1.0), this.getY() + this.random.nextDouble() * 2.0, this.getRandomZ(1.0), 0, 0.1, 0);
            }
        }

        if (!this.level().isClientSide() && spawnInitialized && !this.entityData.get(REVEALED)) {
            this.setDeltaMovement(0, this.getDeltaMovement().y, 0);

            if (spawnTimer-- <= 0) {
                this.setInvisible(false);
                this.entityData.set(REVEALED, true);
                if (isFireVariant) {
                    this.setSecondsOnFire(15);
                }
                this.level().playSound(
                    null, this.blockPosition(),
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_strike")),
                    this.getSoundSource(), 1.0f, 1.0f
                );
                
                if (this.level() instanceof ServerLevel serverLevel) {
                    for (int i = 0; i < 50; i++) {
                        double dx = (this.random.nextDouble() - 0.5) * 1.5;
                        double dy = this.random.nextDouble() * this.getBbHeight();
                        double dz = (this.random.nextDouble() - 0.5) * 1.5;
                        serverLevel.sendParticles(
                            ParticleTypes.FLAME,
                            this.getX() + dx, this.getY() + dy, this.getZ() + dz,
                            1, 0, 0, 0, 0.01
                        );
                        if (this.random.nextDouble() < 0.3) {
                            serverLevel.sendParticles(
                                ParticleTypes.LAVA,
                                this.getX() + dx, this.getY() + dy, this.getZ() + dz,
                                1, 0, 0.05, 0, 0.02
                            );
                        }
                    }
                }
            }
        }

        if (isFireVariant && this.entityData.get(REVEALED)) {
            this.setSecondsOnFire(15);
            this.setRemainingFireTicks(15);
            this.setSharedFlag(0, true);
        }

        if (this.level().isClientSide() && this.isAlive() && this.entityData.get(REVEALED)) {
            if (breathSoundCooldown-- <= 0) {
                this.playSound(
                    ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_breath_loop")),
                    1.0f, 1.0f
                );
                if (isFireVariant) {
                    this.playSound(
                        ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool:dog_fire_loop")),
                        1.0f, 1.0f
                    );
                }
                breathSoundCooldown = 80;
            }
        }

        if (!this.level().isClientSide() && this.entityData.get(REVEALED)) {
            if (this.tickCount % 20 == 0 || assignedTarget == null) {
                validateOrAssignTarget();
            }
        }
    }
}