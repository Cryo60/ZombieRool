package me.cryo.zombierool.entity;

import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.init.ZombieroolModParticleTypes;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector3f;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.nbt.CompoundTag;

public abstract class AbstractZombieRoolEntity extends Monster {
    protected boolean headshotDeath = false;
    protected int headshotDeathTicks = 0;
    protected DamageSource headshotSource;
    protected boolean hasTriggeredHeadshotKill = false;
    protected int stuckTimer = 0;
    protected Vec3 lastPos = Vec3.ZERO;

    private static final EntityDataAccessor<String> CUSTOM_SKIN = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.STRING);

    public AbstractZombieRoolEntity(EntityType<? extends Monster> type, Level level) {
        super(type, level);
        setMaxUpStep(1.25f);
        xpReward = 0;
        setNoAi(false);
        setPersistenceRequired();

        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CUSTOM_SKIN, "");
    }

    public String getCustomSkin() {
        return this.entityData.get(CUSTOM_SKIN);
    }

    public void setCustomSkin(String skinId) {
        this.entityData.set(CUSTOM_SKIN, skinId);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("CustomSkin", getCustomSkin());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setCustomSkin(compound.getString("CustomSkin"));
    }

    public void setHeadshotDeath(boolean value) {
        this.headshotDeath = value;
        if (value) {
            this.headshotDeathTicks = 0;
        }
    }

    public boolean isHeadshotDeath() {
        return this.headshotDeath;
    }

    @Override
    public int getMaxFallDistance() {
        return 20; 
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getDirectEntity() instanceof Player player && BonusManager.isInstaKillActive(player)) {
            return super.hurt(source, Float.MAX_VALUE);
        }

        boolean headshot = false;
        double headshotThreshold = this.getY() + this.getBbHeight() * 0.85;

        boolean isProjectile = source.getDirectEntity() instanceof Projectile;
        boolean isHitscan = !isProjectile && source.getEntity() instanceof Player;

        if (isProjectile) {
            Projectile p = (Projectile) source.getDirectEntity();
            headshot = p.getY() >= headshotThreshold;
        } else if (isHitscan && source.getEntity() instanceof Player player) {
            Vec3 playerEyePos = player.getEyePosition(1.0f);
            Vec3 lookVec = player.getViewVector(1.0f);
            Vec3 start = playerEyePos;
            Vec3 end = start.add(lookVec.scale(100));

            var hitResult = this.getBoundingBox().clip(start, end);
            if (hitResult.isPresent()) {
                Vec3 actualHitPos = hitResult.get();
                if (actualHitPos.y >= headshotThreshold) {
                    headshot = true;
                }
            }
        }

        if (this.getPersistentData().getBoolean(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG)) {
            headshot = true;
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
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);

        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            WaveManager.onMobDeath(this, serverLevel);

            Entity direct = cause.getDirectEntity();
            Entity src = cause.getEntity();
            Player player = null;
            
            if (src instanceof Player p1) {
                player = p1;
            } else if (direct instanceof Projectile proj && proj.getOwner() instanceof Player p2) {
                player = p2;
            }

            if (player != null) {
                boolean hasIngot = player.getInventory().items.stream()
                    .anyMatch(st -> st.getItem() instanceof me.cryo.zombierool.item.IngotSaleItem);
                
                if (!hasIngot && player.level().random.nextFloat() < 0.0015f) {
                    player.getInventory().add(new ItemStack(
                        me.cryo.zombierool.init.ZombieroolModItems.INGOT_SALE.get()));
                    me.cryo.zombierool.FireSaleHandler.startFireSale(player);
                }
            }

            if (player != null && WorldConfig.get(serverLevel).isBonusDropsEnabled()) {
                int zombiesKilledSinceLastBonus = WaveManager.getZombiesKilledSinceLastBonus(serverLevel);
                boolean shouldSpawnBonus = false;

                if (zombiesKilledSinceLastBonus >= 150) {
                    shouldSpawnBonus = true;
                    WaveManager.resetZombiesKilledSinceLastBonus(serverLevel);
                } else if (this.random.nextFloat() < 0.005f) {
                    shouldSpawnBonus = true;
                }

                if (shouldSpawnBonus) {
                    BonusManager.Bonus randomBonus = BonusManager.getRandomBonus(player);
                    if (randomBonus != null) {
                        Vec3 bonusPos = new Vec3(this.getX(), this.getY() + 0.5, this.getZ());
                        BonusManager.spawnBonus(randomBonus, serverLevel, bonusPos);
                    }
                }
            }
        }
    }

    @Override
    public void tick() {
        super.tick();

        if (this.headshotDeath && !hasTriggeredHeadshotKill) {
            headshotDeathTicks++;
            if (headshotDeathTicks >= 5 && !this.level().isClientSide) {
                hasTriggeredHeadshotKill = true;
                DamageSource ds = headshotSource != null ? headshotSource : this.damageSources().generic();
                if (this.isAlive()) {
                    this.setHealth(0);
                    this.die(ds);
                }
            }
        }

        if (!this.level().isClientSide) {
            if (this.tickCount % 20 == 0) {
                double distMoved = this.position().distanceToSqr(this.lastPos);
                boolean nearPlayer = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 20.0, false) != null;
                
                if (distMoved < 0.01 && !nearPlayer && this.getTarget() != null) {
                    this.stuckTimer += 20;
                } else {
                    this.stuckTimer = 0;
                }

                if (this.stuckTimer >= 300) { 
                    WaveManager.recycleMob(this, (ServerLevel) this.level());
                }

                this.lastPos = this.position();
            }
        }
    }
}
