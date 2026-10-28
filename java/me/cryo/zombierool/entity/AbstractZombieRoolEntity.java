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
    private boolean appearanceAssigned;
    public boolean hasAssignedAppearance() { return appearanceAssigned; }
    protected boolean headshotDeath = false;
    protected int headshotDeathTicks = 0;
    private int corpseLinger = 0;

    public int getCorpseLinger() { return this.corpseLinger; }
    protected DamageSource headshotSource;
    protected boolean hasTriggeredHeadshotKill = false;
    protected int stuckTimer = 0;
    protected Vec3 lastPos = Vec3.ZERO;
    private static final EntityDataAccessor<String> CUSTOM_SKIN = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<String> CUSTOM_EYE_SKIN = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> ZR_SCALE = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> GORE_MASK = SynchedEntityData.defineId(AbstractZombieRoolEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> EMERGENCE=SynchedEntityData.defineId(AbstractZombieRoolEntity.class,EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DEATH_AGE=SynchedEntityData.defineId(AbstractZombieRoolEntity.class,EntityDataSerializers.INT);
    public int getEmergenceTicks(){return entityData.get(EMERGENCE);}
    public void setEmergenceTicks(int ticks){entityData.set(EMERGENCE,Math.max(0,Math.min(me.cryo.zombierool.gameplay.SpawnEmergence.DURATION,ticks)));}
    private static final EntityDataAccessor<Boolean> CERBERUS_FEED=SynchedEntityData.defineId(AbstractZombieRoolEntity.class,EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<net.minecraft.core.BlockPos> CERBERUS_HEAD=SynchedEntityData.defineId(AbstractZombieRoolEntity.class,EntityDataSerializers.BLOCK_POS);
    public net.minecraft.core.BlockPos getCerberusHead(){return entityData.get(CERBERUS_HEAD);}
    public void setCerberusHead(net.minecraft.core.BlockPos pos){entityData.set(CERBERUS_HEAD,pos.immutable());}
    public boolean isCerberusFeeding(){return entityData.get(CERBERUS_FEED);}
    public void setCerberusFeeding(boolean value){entityData.set(CERBERUS_FEED,value);}
    protected boolean tickFeedingCorpse(){if(!isCerberusFeeding())return false;setNoAi(true);setNoGravity(true);noPhysics=true;setDeltaMovement(Vec3.ZERO);deathTime=19;return true;}
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
        this.moveControl=new me.cryo.zombierool.gameplay.DoorwayMoveControl(this);
        setMaxUpStep(1.25f);
        xpReward = 0;
        setNoAi(false);
        setPersistenceRequired();
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER,0);
        setPathfindingMalus(net.minecraft.world.level.pathfinder.BlockPathTypes.WATER_BORDER,0);
        if (this.getNavigation() instanceof GroundPathNavigation nav) {
            nav.setCanOpenDoors(true);
            nav.setCanFloat(false);
            nav.setMaxVisitedNodesMultiplier(4.0f);
        }
    }
    @Override protected net.minecraft.world.entity.ai.navigation.PathNavigation createNavigation(Level level){
        return new me.cryo.zombierool.gameplay.RouteNavigation(this,level);
    }
    public void resetStuckTimer() {
        this.stuckTimer = 0;
    }
    @Override public boolean isPushedByFluid(){return !isInWater()&&super.isPushedByFluid();}
    @Override public void travel(Vec3 input){
        if(isAlive()&&isEffectiveAi()&&isInWater()&&!noPhysics&&!isNoGravity()){
            if(me.cryo.zombierool.gameplay.EnemyWaterMovement.swims(this)){
                moveRelative(getSpeed()*(.85f*(1-.9f)/(1-.6f*.91f)),input);
                move(net.minecraft.world.entity.MoverType.SELF,getDeltaMovement());
                setDeltaMovement(getDeltaMovement().scale(.9));
                calculateEntityAnimation(false);
                return;
            }
            // Walk on the bed. No buoyancy, vertical steering or water-wall boost.
            moveRelative(getSpeed()*(.85f*(1-.9f)/(1-.6f*.91f)),new Vec3(input.x,0,input.z));
            move(net.minecraft.world.entity.MoverType.SELF,getDeltaMovement());
            Vec3 velocity=getDeltaMovement();
            setDeltaMovement(velocity.x*.9,(velocity.y-.04)*.98,velocity.z*.9);
            calculateEntityAnimation(false);
        }else super.travel(input);
    }
    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(DEATH_AGE,0);
        this.entityData.define(CUSTOM_SKIN, "");
        this.entityData.define(CUSTOM_EYE_SKIN, "");
        this.entityData.define(ZR_SCALE, 1.0f);
        this.entityData.define(GORE_MASK, 0);
        this.entityData.define(CHARRED, false);
        this.entityData.define(EMERGENCE,0);this.entityData.define(CERBERUS_FEED,false);this.entityData.define(CERBERUS_HEAD,net.minecraft.core.BlockPos.ZERO);
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
    public void setCustomSkin(String skinId) { this.entityData.set(CUSTOM_SKIN, skinId == null ? "" : skinId); appearanceAssigned = true; }
    public String getCustomEyeSkin() { return this.entityData.get(CUSTOM_EYE_SKIN); }
    public void setCustomEyeSkin(String skinId) { this.entityData.set(CUSTOM_EYE_SKIN, skinId == null ? "" : skinId); }
    public String getEyeSkinId() {
        String eye = getCustomEyeSkin();
        if (eye != null && !eye.isEmpty()) return eye;
        return getCustomSkin();
    }
    public void assignRolledSkins(String mobType) {
        String body = me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin(mobType);
        setCustomSkin(body == null || body.isEmpty() ? (level() instanceof ServerLevel server ? me.cryo.zombierool.config.EnemySkinPresets.roll(server,mobType) : "") : body);
        assignEyeSkin(mobType);
    }
    public void assignEyeSkin(String mobType) {
        setCustomEyeSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.pickEyeSkin(mobType + "_eyes", getCustomSkin()));
    }
    public boolean isLingeringCorpse() {
        return this.deathTime > 0 && !this.isRemoved() && this.isCorpseTarget();
    }
    protected boolean isCorpseTarget() {
        return false;
    }
    protected void onCorpseShot(DamageSource source) {
    }
    private static boolean isPlayerDamage(DamageSource source) {
        if (source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player) return true;
        return source.getDirectEntity() instanceof Projectile projectile && projectile.getOwner() instanceof Player;
    }
    public float getScale() { return this.entityData.get(ZR_SCALE); }
    public void setScale(float scale) {
        this.entityData.set(ZR_SCALE, scale);
        this.refreshDimensions();
    }
    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> pKey) {
        if(DEATH_AGE.equals(pKey)){deathTime=Math.max(deathTime,entityData.get(DEATH_AGE));if(deathTime>0)refreshDimensions();}
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
        compound.putString("CustomEyeSkin", getCustomEyeSkin());
        compound.putFloat("zr_scale", getScale());
        compound.putInt("GoreMask", this.entityData.get(GORE_MASK));
        compound.putBoolean("Charred", this.isCharred());
    }
    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        if(deathTime>0)entityData.set(DEATH_AGE,deathTime);
        if (compound.contains("CustomSkin")) setCustomSkin(compound.getString("CustomSkin"));
        if (compound.contains("CustomEyeSkin")) {
            setCustomEyeSkin(compound.getString("CustomEyeSkin"));
        }
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
    public boolean canBeCollidedWith() {
        return isAlive() && !isCorpseTarget();
    }
    @Override
    public boolean canCollideWith(Entity other) {
        // Keep crowds free to move, but never let a zombie walk through its victim.
        return canBeCollidedWith() && other instanceof Player player && player.isAlive() && !player.isSpectator();
    }
    @Override
    protected void doPush(Entity entityIn) {
    }
    @Override
    public void push(Entity entityIn) {
    }
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.isCorpseTarget() && (this.deathTime > 0 || this.getHealth() <= 0.0F)) {
            boolean playerHit = isPlayerDamage(source);
            if (!this.level().isClientSide() && playerHit) {
                this.onCorpseShot(source);
            }
            return playerHit;
        }
        if (source.getEntity() instanceof Player player && BonusManager.isInstaKillActive(player) && !me.cryo.zombierool.gameplay.EquipmentEffects.instantKillImmune(this)) {
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
            headshot = this.getBoundingBox().clip(new Vec3(p.xo,p.yo,p.zo),p.position().add(p.getDeltaMovement())).orElse(p.position()).y >= headshotThreshold;
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
            GoreManager.onHit(this, GoreManager.canDismember(source));
        }
        return hurt;
    }

    @Override
    protected void tickDeath() {
        if(tickFeedingCorpse())return;
        settleCorpse();
        boolean reduced = this.level() instanceof ServerLevel serverLevel && WorldConfig.get(serverLevel).isReducedGore();
        if (reduced) {
            super.tickDeath();
            return;
        }
        this.setNoAi(true);
        net.minecraft.world.phys.Vec3 motion = this.getDeltaMovement();
        this.setDeltaMovement(0.0, motion.y, 0.0);
        ++this.deathTime;
        if (this.deathTime == 1) {
            this.refreshDimensions();
        }
        if (this.deathTime > 19) {
            this.deathTime = 19;
        }
        if(!level().isClientSide&&(deathTime%5==0||deathTime==19))entityData.set(DEATH_AGE,deathTime);
        this.corpseLinger++;

    }
    /** NoAI suppresses vanilla travel, so persistent corpses need their own falling movement. */
    protected final void settleCorpse(){
        me.cryo.zombierool.gameplay.TraversalMotion.cancel(this);
        setNoAi(true);setNoGravity(false);noPhysics=false;
        double speed=getDeltaMovement().y;
        move(net.minecraft.world.entity.MoverType.SELF,new net.minecraft.world.phys.Vec3(0,speed-.08*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt(this),0));
        setDeltaMovement(0,onGround()?0:(speed-.08*me.cryo.zombierool.gameplay.LunarPhysics.gravityAt(this))*.98,0);
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
        if(!isAlive())me.cryo.zombierool.gameplay.TraversalMotion.cancel(this);
        if (!this.level().isClientSide) {
            if (fireKill) GoreManager.onChar(this);
            else if (!skipGore && !(this instanceof HellhoundEntity)) GoreManager.onDeath(this, GoreManager.canDismember(cause));
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
                if (me.cryo.zombierool.gameplay.IngotSaleDrops.consume(player,this) || !hasIngot && player.level().random.nextFloat() < 0.0015f) {
                    me.cryo.zombierool.gameplay.IngotSaleDrops.grant(player);
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
                if (shouldSpawnBonus && !getPersistentData().getBoolean("zr_forced_bonus_drop")) {
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
        if(me.cryo.zombierool.gameplay.FiestaFreeze.frozen(this))return;
        if(!this.level().isClientSide)me.cryo.zombierool.gameplay.TrapContact.tick(this);
        if (!this.level().isClientSide() && this.isAlive() && !me.cryo.zombierool.gameplay.TraversalMotion.active(this)
                && !me.cryo.zombierool.block.system.TraversalSystem.boardingZipline(this)) {
            List<Entity> nearby = this.level().getEntities(this, this.getBoundingBox().inflate(0.3D),
                e -> e instanceof AbstractZombieRoolEntity && e.isAlive()&&!me.cryo.zombierool.gameplay.ActorWaveCollision.ignores(this,e));
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
                if (distMoved < 0.01 && this.isAlive() && this.getTarget() != null && this.tickCount % 40 == 0) this.getNavigation().recomputePath();
                boolean nearPlayer = this.level().getNearestPlayer(this.getX(), this.getY(), this.getZ(), 20.0, false) != null;
                if (distMoved < 0.01 && !nearPlayer && this.getTarget() instanceof net.minecraft.world.entity.player.Player pursuit
                        && me.cryo.zombierool.gameplay.RouteNavigation.reachesPlayer(this.getNavigation().getPath(),pursuit)
                        && !me.cryo.zombierool.gameplay.TraversalMotion.active(this)) {
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
