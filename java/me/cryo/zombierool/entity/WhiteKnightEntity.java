package me.cryo.zombierool.entity;

import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.core.particles.ParticleTypes;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.PointManager;
import java.util.List;
import java.util.EnumSet;

public class WhiteKnightEntity extends TamableAnimal {
    private static final EntityDataAccessor<Boolean> SPAWNED_BY_PLAYER = SynchedEntityData.defineId(WhiteKnightEntity.class, EntityDataSerializers.BOOLEAN);
    
    private int sweepAttackCooldown = 0;
    private static final int SWEEP_COOLDOWN_TICKS = 60;

    public static final double AGGRO_RADIUS = 24.0D; 
    public static final double OWNER_PROTECTION_RADIUS = 8.0D; 

    public WhiteKnightEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.WHITE_KNIGHT.get(), world);
    }

    public WhiteKnightEntity(EntityType<WhiteKnightEntity> type, Level world) {
        super(type, world);
        setMaxUpStep(1.25f);
        xpReward = 0;
        setNoAi(false);
        setPersistenceRequired();
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));
        
        if (this.getNavigation() instanceof GroundPathNavigation groundPathNavigation) {
            groundPathNavigation.setCanOpenDoors(true);
            groundPathNavigation.setCanPassDoors(true);
            groundPathNavigation.setCanWalkOverFences(true);
            groundPathNavigation.setCanFloat(true);
        }
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SPAWNED_BY_PLAYER, false);
    }

    public boolean isSpawnedByPlayer() {
        return this.entityData.get(SPAWNED_BY_PLAYER);
    }

    public void setSpawnedByPlayer(boolean spawnedByPlayer) {
        this.entityData.set(SPAWNED_BY_PLAYER, spawnedByPlayer);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    public void applyFixedAttributes() {
        if (this.getAttribute(Attributes.ARMOR) != null) {
            this.getAttribute(Attributes.ARMOR).setBaseValue(20);
        }
        if (this.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6);
        }
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);

        this.goalSelector.addGoal(1, new DefensiveMeleeGoal(this, 1.3D)); 
        this.goalSelector.addGoal(2, new SmartFollowOwnerGoal(this, 1.2D, 8.0F, 3.0F)); 
        this.goalSelector.addGoal(3, new FloatGoal(this));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new LineOfSightTargetGoal<>(this, ZombieEntity.class, 10, true, false));
        this.targetSelector.addGoal(2, new LineOfSightTargetGoal<>(this, CrawlerEntity.class, 10, true, false));
        this.targetSelector.addGoal(3, new LineOfSightTargetGoal<>(this, HellhoundEntity.class, 10, true, false));
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
    public double getMyRidingOffset() {
        return -0.35D;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source.is(DamageTypes.FALL)) {
            return super.hurt(source, amount);
        }

        if (source.getEntity() instanceof Player || (source.getDirectEntity() instanceof AbstractArrow && ((AbstractArrow) source.getDirectEntity()).getOwner() instanceof Player)) {
            return false;
        }

        return super.hurt(source, amount);
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean result = super.doHurtTarget(target);
        if (result && !this.level().isClientSide() && this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (owner instanceof Player player && target instanceof LivingEntity livingTarget) {
                boolean isCustomMob = target instanceof ZombieEntity ||
                                      target instanceof CrawlerEntity ||
                                      target instanceof HellhoundEntity;
                if (isCustomMob) {
                    PointManager.modifyScore(player, 10);
                    if (!livingTarget.isAlive()) {
                        PointManager.modifyScore(player, 50);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public InteractionResult mobInteract(Player sourceentity, InteractionHand hand) {
        ItemStack itemstack = sourceentity.getItemInHand(hand);
        Item item = itemstack.getItem();

        if (!this.isTame() && this.isSpawnedByPlayer()) {
            this.tame(sourceentity);
            this.level().broadcastEntityEvent(this, (byte) 7);
            this.setPersistenceRequired();
            this.applyFixedAttributes();
            this.registerGoals();
            sourceentity.displayClientMessage(Component.literal("§aLe Chevalier Blanc a été apprivoisé !"), true);
            return InteractionResult.SUCCESS;
        }

        if (this.isTame() && this.isOwnedBy(sourceentity)) {
            if (item.isEdible() && this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(sourceentity, hand, itemstack);
                this.heal((float) item.getFoodProperties().getNutrition());
                return InteractionResult.sidedSuccess(this.level().isClientSide());
            } else if (this.isFood(itemstack) && this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(sourceentity, hand, itemstack);
                this.heal(4);
                return InteractionResult.sidedSuccess(this.level().isClientSide());
            } 
        }

        return super.mobInteract(sourceentity, hand);
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverWorld, AgeableMob ageable) {
        WhiteKnightEntity retval = ZombieroolModEntities.WHITE_KNIGHT.get().create(serverWorld);
        retval.finalizeSpawn(serverWorld, serverWorld.getCurrentDifficultyAt(retval.blockPosition()), MobSpawnType.BREEDING, null, null);
        return retval;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return List.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE).contains(stack.getItem());
    }

    public static void init() {
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        this.applyFixedAttributes();
        this.registerGoals(); 
    }

    public static AttributeSupplier.Builder createAttributes() {
        AttributeSupplier.Builder builder = Mob.createMobAttributes();
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.23); 
        builder = builder.add(Attributes.MAX_HEALTH, 30);
        builder = builder.add(Attributes.ARMOR, 10);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 3.5);
        builder = builder.add(Attributes.FOLLOW_RANGE, 24); 
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.sweepAttackCooldown > 0) {
            this.sweepAttackCooldown--;
        }

        if (!this.level().isClientSide() && this.isTame()) {
            LivingEntity owner = this.getOwner();
            if (owner == null || !owner.isAlive()) {
                this.discard();
                return;
            }
            if (!owner.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_ROYAL_BEER.get())) {
                this.discard();
            }
        }
    }

    public void performSweepAttack(LivingEntity target) {
        if (this.sweepAttackCooldown > 0) {
            return;
        }

        this.level().broadcastEntityEvent(this, (byte) 29);

        double sweepRange = 2.0;
        AABB sweepArea = target.getBoundingBox().inflate(sweepRange, 0.25D, sweepRange);

        List<LivingEntity> targetsInSweep = this.level().getEntitiesOfClass(LivingEntity.class, sweepArea, (entity) -> {
            return entity != this &&
                   (this.getOwner() == null || entity != this.getOwner()) &&
                   !(entity instanceof Player) &&
                   this.distanceToSqr(entity) < (sweepRange * sweepRange * 1.5) &&
                   !this.isAlliedTo(entity);
        });

        int hitCount = 0;
        float baseDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float sweepDamageMultiplier = 0.85f;

        for (LivingEntity entity : targetsInSweep) {
            if (hitCount >= 6) break;

            float damage = baseDamage * sweepDamageMultiplier;
            entity.hurt(this.damageSources().mobAttack(this), damage);

            double deltaX = entity.getX() - this.getX();
            double deltaZ = entity.getZ() - this.getZ();
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (distance != 0.0D) {
                entity.push(deltaX / distance * 0.5D, 0.3D, deltaZ / distance * 0.5D);
            }
            hitCount++;
        }

        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
            net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP,
            this.getSoundSource(), 1.0F, 1.0F);

        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.SWEEP_ATTACK,
                this.getX(), this.getY() + 1.0D, this.getZ(), 0.0D, 0.0D, 0.0D);
        }

        this.sweepAttackCooldown = SWEEP_COOLDOWN_TICKS;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    static class DefensiveMeleeGoal extends MeleeAttackGoal {
        private final WhiteKnightEntity knight;
        private int pathRecalcTick = 0;

        public DefensiveMeleeGoal(WhiteKnightEntity mob, double speedModifier) {
            super(mob, speedModifier, false);
            this.knight = mob;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!this.knight.isTame()) {
                return false;
            }
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return this.knight.distanceToSqr(target) < (AGGRO_RADIUS * AGGRO_RADIUS) && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.knight.isTame()) {
                return false;
            }
            LivingEntity target = this.knight.getTarget();
            if (target == null || !target.isAlive()) {
                return false;
            }
            return this.knight.distanceToSqr(target) < (AGGRO_RADIUS * AGGRO_RADIUS) && super.canContinueToUse();
        }

        @Override
        public void tick() {
            super.tick();
            LivingEntity target = this.knight.getTarget();
            if (target == null) {
                return;
            }

            if (pathRecalcTick++ >= 15) { 
                pathRecalcTick = 0;
                this.knight.getNavigation().moveTo(target, 1.3D);
            }

            if (this.knight.distanceToSqr(target) < (this.getAttackReachSqr(target) * 2.0) && this.knight.sweepAttackCooldown == 0) {
                AABB checkArea = this.knight.getBoundingBox().inflate(2.0D);
                List<LivingEntity> nearbyHostiles = this.knight.level().getEntitiesOfClass(LivingEntity.class, checkArea, (e) -> {
                    return e != this.knight && 
                           (this.knight.getOwner() == null || e != this.knight.getOwner()) && 
                           this.knight.hasLineOfSight(e) && 
                           this.isHostile(e.getType());
                });

                if (nearbyHostiles.size() >= 3) { 
                    this.knight.performSweepAttack(target);
                }
            }
        }

        private boolean isHostile(EntityType<?> type) {
            return type == ZombieroolModEntities.ZOMBIE.get() || 
                   type == ZombieroolModEntities.CRAWLER.get() || 
                   type == ZombieroolModEntities.HELLHOUND.get();
        }
    }

    static class SmartFollowOwnerGoal extends FollowOwnerGoal {
        private final WhiteKnightEntity knight;
        private final float storedMinDist;

        public SmartFollowOwnerGoal(WhiteKnightEntity tamable, double speedModifier, float minDist, float maxDist) {
            super(tamable, speedModifier, minDist, maxDist, false);
            this.knight = tamable;
            this.storedMinDist = minDist;
        }

        @Override
        public boolean canUse() {
            if (this.knight.getTarget() != null) return false;
            LivingEntity owner = this.knight.getOwner();
            if (owner == null || !owner.isAlive()) {
                return false;
            }
            double distSqr = this.knight.distanceToSqr(owner);
            if (distSqr < (this.storedMinDist * this.storedMinDist)) {
                return false;
            }
            return super.canUse();
        }
    }

    static class LineOfSightTargetGoal<T extends LivingEntity> extends NearestAttackableTargetGoal<T> {
        private final WhiteKnightEntity knight;

        public LineOfSightTargetGoal(WhiteKnightEntity mob, Class<T> targetType, int interval, boolean mustSee, boolean mustReach) {
            super(mob, targetType, interval, mustSee, mustReach, null);
            this.knight = mob;
        }

        @Override
        public boolean canUse() {
            if (!this.knight.isTame()) {
                return false;
            }
            LivingEntity owner = this.knight.getOwner();
            if (owner == null) {
                return false;
            }

            AABB searchArea = this.knight.getBoundingBox().inflate(AGGRO_RADIUS);
            List<T> visibleTargets = this.knight.level().getEntitiesOfClass(this.targetType, searchArea, (entity) -> {
                return entity.isAlive() && this.knight.hasLineOfSight(entity);
            });

            if (visibleTargets.isEmpty()) {
                return false;
            }

            for (T potential : visibleTargets) {
                if (potential instanceof Mob mob && mob.getTarget() == owner) {
                    this.target = potential;
                    return true;
                }
            }

            AABB ownerArea = owner.getBoundingBox().inflate(OWNER_PROTECTION_RADIUS);
            for (T potential : visibleTargets) {
                if (ownerArea.intersects(potential.getBoundingBox())) {
                    this.target = potential;
                    return true;
                }
            }

            T closest = null;
            double closestDist = Double.MAX_VALUE;
            for (T potential : visibleTargets) {
                double dist = this.knight.distanceToSqr(potential);
                if (dist < closestDist) {
                    closestDist = dist;
                    closest = potential;
                }
            }

            if (closest != null) {
                this.target = closest;
                return true;
            }

            return false;
        }

        @Override
        public boolean canContinueToUse() {
            if (!this.knight.isTame() || this.target == null || !this.target.isAlive()) {
                return false;
            }
            if (!this.knight.hasLineOfSight(this.target)) {
                return false;
            }
            return this.knight.distanceToSqr(this.target) < (AGGRO_RADIUS * AGGRO_RADIUS);
        }
    }
}
