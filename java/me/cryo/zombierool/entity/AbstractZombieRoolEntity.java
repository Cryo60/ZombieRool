package me.cryo.zombierool.entity;
import me.cryo.zombierool.gameplay.WaveManager;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.core.manager.GoreManager;
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
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
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
import java.util.List;
public abstract class AbstractZombieRoolEntity extends Monster {
    protected boolean headshotDeath = false;
    protected int headshotDeathTicks = 0;
    private int corpseLinger = 0;

    public int getCorpseLinger() { return this.corpseLinger; }
    protected DamageSource headshotSource;
    protected boolean hasTriggeredHeadshotKill = false;
    protected int stuckTimer = 0;
    protected Vec3 lastPos = Vec3.ZERO;
    private static final EntityDataAccessor<String> CUSTOM_SKIN = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> ZR_SCALE = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GORE_MASK = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CHARRED = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.BOOLEAN);
    public static final int GORE_HEAD = 1;
    public static final int GORE_LEFT_ARM = 1 << 1;
    public static final int GORE_RIGHT_ARM = 1 << 2;
    public static final int GORE_LEFT_LEG = 1 << 3;
    public static final int GORE_RIGHT_LEG = 1 << 4;
    public static final int GORE_LEFT_FOREARM = 1 << 5;
    public static final int GORE_RIGHT_FOREARM = 1 << 6;
    public static final int GORE_LEFT_HAND = 1 << 7;
    public static final int GORE_RIGHT_HAND = 1 << 8;
    public static final int GORE_TORSO = 1 << 9;
    public static final int GORE_SKULL = 1 << 10;
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
    public void resetStuckTimer() {
        this.stuckTimer = 0;
    }
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(CUSTOM_SKIN, "");
        this.entityData.define(ZR_SCALE, 1.0f);
        this.entityData.define(GORE_MASK, 0);
        this.entityData.define(CHARRED, false);
    }
    public void setCharred(boolean charred) { this.entityData.set(CHARRED, charred); }
    public boolean isCharred() { return this.entityData.get(CHARRED); }
    public void setGoreBit(int bit, boolean lost) {
        int mask = this.entityData.get(GORE_MASK);
        int next = lost ? (mask | bit) : (mask & ~bit);
        if (next != mask) this.entityData.set(GORE_MASK, next);
    }
    public boolean hasGoreBit(int bit) {
        return (this.entityData.get(GORE_MASK) & bit) != 0;
    }
    public String getCustomSkin() { return this.entityData.get(CUSTOM_SKIN); }
    public void setCustomSkin(String skinId) { this.entityData.set(CUSTOM_SKIN, skinId); }
    public float getScale() { return this.entityData.get(ZR_SCALE); }
    public void setScale(float scale) {
        this.entityData.set(ZR_SCALE, scale);
        this.refreshDimensions();
    }
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        if (ZR_SCALE.equals(pKey)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(pKey);
    }
    @Override
    public EntityDimensions getDimensions(Pose pose) {
        float scale = getScale();
        if (scale != 1.0f) {
            return super.getDimensions(pose).scale(scale);
        }
        return super.getDimensions(pose);
    }
    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putString("CustomSkin", getCustomSkin());
        compound.putFloat("zr_scale", getScale());
        compound.putInt("GoreMask", this.entityData.get(GORE_MASK));
        compound.putBoolean("Charred", this.isCharred());
    }
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        setCustomSkin(compound.getString("CustomSkin"));
        if (compound.contains("zr_scale")) {
            setScale(compound.getFloat("zr_scale"));
        }
        if (compound.contains("GoreMask")) {
            this.entityData.set(GORE_MASK, compound.getInt("GoreMask"));
        }
        if (compound.contains("Charred")) {
            this.setCharred(compound.getBoolean("Charred"));
        }
    }
    public void setHeadshotDeath(boolean value) {
        this.headshotDeath = value;
        if (value) {
            this.headshotDeathTicks = 0;
        }
    }
    public boolean isHeadshotDeath() { return this.headshotDeath; }
    @Override
    public int getMaxFallDistance() { return 20; }
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) { return false; }
    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
    @Override
    public float getPickRadius() { return 0.3F * getScale(); }
    @Override
    public boolean isPushable() {
        return false; 
    }
    @Override
    protected void doPush(Entity entityIn) {
    }
    @Override
    public void push(Entity entityIn) {
    }
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.getEntity() instanceof Player player && BonusManager.isInstaKillActive(player)) {
            amount = 100000f;
        }
        boolean quietHit = source.is(DamageTypes.MOB_ATTACK)
                || this.getPersistentData().getBoolean("zombierool:no_gore")
                || this.getPersistentData().getBoolean("zr_fire_kill");
        boolean headshot = false;
        double headshotThreshold = this.getY() + this.getBbHeight() * 0.75;
        boolean isProjectile = !quietHit && source.getDirectEntity() instanceof Projectile;
        boolean isHitscan = !quietHit && !isProjectile && source.getEntity() instanceof Player;
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
        if (!quietHit && this.getPersistentData().getBoolean(me.cryo.zombierool.core.manager.DamageManager.HEADSHOT_TAG)) {
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
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive() && !quietHit) {
            GoreManager.onHit(this);
        }
        return hurt;
    }

    @Override
    protected void tickDeath() {
        boolean reduced = this.level() instanceof ServerLevel serverLevel && WorldConfig.get(serverLevel).isReducedGore();
        if (reduced) {
            super.tickDeath();
            return;
        }
        this.setNoAi(true);
        net.minecraft.world.phys.Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(0.0, motion.y, 0.0);
        ++this.deathTime;
        if (this.deathTime > 19) {
            this.deathTime = 19;
        }
        this.corpseLinger++;
        if (!this.level().isClientSide && this.corpseLinger > 3600) {
            this.remove(Entity.RemovalReason.KILLED);
        }
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
        boolean fireKill = this.getPersistentData().getBoolean("zr_fire_kill")
                || cause.is(DamageTypes.ON_FIRE) || cause.is(DamageTypes.IN_FIRE)
                || cause.is(DamageTypes.LAVA) || cause.is(DamageTypes.HOT_FLOOR);
        boolean skipGore = fireKill || this.getPersistentData().getBoolean("zombierool:no_gore");
        if (fireKill) {
            this.setCharred(true);
            this.setRemainingFireTicks(0);
        }
        super.die(cause);
        if (!this.level().isClientSide) {
            if (fireKill) GoreManager.onChar(this);
            else if (!skipGore) GoreManager.onDeath(this);
        }
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
                    me.cryo.zombierool.gameplay.FireSaleHandler.startFireSale(player);
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
        if (!this.level().isClientSide() && this.isAlive()) {
            List<Entity> nearby = this.level().getEntities(this, this.getBoundingBox().inflate(0.3D), 
                e -> e instanceof AbstractZombieRoolEntity && e.isAlive());
            if (!nearby.isEmpty()) {
                double pushX = 0;
                double pushZ = 0;
                int count = 0;
                for (Entity other : nearby) {
                    double dx = this.getX() - other.getX();
                    double dz = this.getZ() - other.getZ();
                    double distSq = dx * dx + dz * dz;
                    if (distSq < 0.25) { 
                        double dist = Math.max(0.001, Math.sqrt(distSq));
                        double force = (0.5 - dist) * 0.06; 
                        pushX += (dx / dist) * force;
                        pushZ += (dz / dist) * force;
                        count++;
                    }
                }
                if (count > 0) {
                    this.setDeltaMovement(this.getDeltaMovement().add(pushX, 0, pushZ));
                }
            }
        }
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