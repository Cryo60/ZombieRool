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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffectInstance;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
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
        boolean flag = entity.hurt(this.damageSources().mobAttack(this), 1.0f);
        if (flag && !this.level().isClientSide) {
            SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(new ResourceLocation("zombierool", "crawler_attack"));
            if (sound != null) {
                this.level().playSound(null, this.getX(), this.getY(), this.getZ(), sound, SoundSource.HOSTILE, 1.0F, 1.0F);
            }
            if (entity instanceof Player p && p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
                return flag;
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
                CrawlerGasManager.addGasCloud(serverLevel, this.position(), 240);
                for (int i = 0; i < 60; i++) {
                    double dx = this.random.nextGaussian() * 1.5;
                    double dy = this.random.nextDouble() * 1.5;
                    double dz = this.random.nextGaussian() * 1.5;
                    serverLevel.sendParticles(ZombieroolModParticleTypes.TOXIC_SMOKE.get(), this.getX() + dx, this.getY() + dy, this.getZ() + dz, 1, 0, 0, 0, 0.01);
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
    @Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class CrawlerGasManager {
        private static final Random STATIC_RANDOM = new Random();
        private static class GasCloud {
            ServerLevel level;
            Vec3 pos;
            int ticksRemaining;
            float radius;
            GasCloud(ServerLevel level, Vec3 pos, int ticksRemaining, float radius) {
                this.level = level;
                this.pos = pos;
                this.ticksRemaining = ticksRemaining;
                this.radius = radius;
            }
        }
        private static final List<GasCloud> activeClouds = new ArrayList<>();
        public static void addGasCloud(ServerLevel level, Vec3 pos, int duration) {
            activeClouds.add(new GasCloud(level, pos, duration, 2.5f));
        }
        @SubscribeEvent
        public static void onServerTick(TickEvent.ServerTickEvent event) {
            if (event.phase != TickEvent.Phase.END) return;
            Iterator<GasCloud> iterator = activeClouds.iterator();
            while (iterator.hasNext()) {
                GasCloud cloud = iterator.next();
                cloud.ticksRemaining--;
                if (cloud.ticksRemaining <= 0) {
                    iterator.remove();
                    continue;
                }
                cloud.radius = Math.max(0, cloud.radius - 0.01f); 
                if (cloud.ticksRemaining % 5 == 0) {
                    int particleCount = (int) (cloud.radius * 10);
                    for (int i = 0; i < particleCount; i++) {
                        double dx = (STATIC_RANDOM.nextGaussian() * cloud.radius) * 0.5;
                        double dy = STATIC_RANDOM.nextDouble() * 1.5;
                        double dz = (STATIC_RANDOM.nextGaussian() * cloud.radius) * 0.5;
                        if (dx * dx + dz * dz <= cloud.radius * cloud.radius) {
                            cloud.level.sendParticles(ZombieroolModParticleTypes.TOXIC_SMOKE.get(), cloud.pos.x + dx, cloud.pos.y + dy, cloud.pos.z + dz, 1, 0.1, 0.5, 0.1, 0.01);
                        }
                    }
                }
                if (cloud.ticksRemaining % 10 == 0) {
                    AABB box = new AABB(
                        cloud.pos.x - cloud.radius, cloud.pos.y - 0.5, cloud.pos.z - cloud.radius,
                        cloud.pos.x + cloud.radius, cloud.pos.y + 2.0, cloud.pos.z + cloud.radius
                    );
                    List<LivingEntity> entities = cloud.level.getEntitiesOfClass(LivingEntity.class, box);
                    for (LivingEntity target : entities) {
                        if (!target.isAlive() || target.isSpectator()) continue;
                        if (target.distanceToSqr(cloud.pos) > cloud.radius * cloud.radius) continue;
                        if (target instanceof Player p) {
                            if (p.hasEffect(me.cryo.zombierool.init.ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
                                continue;
                            }
                            target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 6));
                            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                            target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 1));
                        }
                    }
                }
            }
        }
    }
}
