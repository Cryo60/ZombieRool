package net.mcreator.zombierool.entity;

import net.minecraftforge.network.PlayMessages;
import net.minecraftforge.network.NetworkHooks;

import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.core.particles.ParticleTypes; // Import for particles

import net.mcreator.zombierool.init.ZombieroolModEntities;
import net.mcreator.zombierool.entity.ZombieEntity; 
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;
// Import for the mob effect
import net.mcreator.zombierool.init.ZombieroolModMobEffects; // Adjust this import to your actual effect's location


import java.util.List;
import java.util.EnumSet;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;


public class WhiteKnightEntity extends TamableAnimal {

    private static final EntityDataAccessor<Boolean> SPAWNED_BY_PLAYER = SynchedEntityData.defineId(WhiteKnightEntity.class, EntityDataSerializers.BOOLEAN);

    private int sweepAttackCooldown = 0;
    private static final int SWEEP_COOLDOWN_TICKS = 60; // 3 seconds

    // Define the aggro radius as a constant
    public static final double AGGRO_RADIUS = 24.0D; // Matches FOLLOW_RANGE attribute
    // Define a smaller protection radius around the owner
    public static final double OWNER_PROTECTION_RADIUS = 5.0D; 

    public WhiteKnightEntity(PlayMessages.SpawnEntity packet, Level world) {
        this(ZombieroolModEntities.WHITE_KNIGHT.get(), world);
    }

    public WhiteKnightEntity(EntityType<WhiteKnightEntity> type, Level world) {
        super(type, world);
        setMaxUpStep(0.6f);
        xpReward = 0;
        setNoAi(false);
        setPersistenceRequired();
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SHIELD));

        if (this.getNavigation() instanceof GroundPathNavigation groundPathNavigation) {
            groundPathNavigation.setCanOpenDoors(true);
            groundPathNavigation.setCanPassDoors(true);
            groundPathNavigation.setCanWalkOverFences(true);
            groundPathNavigation.setCanFloat(false);
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
            this.getAttribute(Attributes.ARMOR).setBaseValue(20); // Fixed armor
        }
        if (this.getAttribute(Attributes.ATTACK_DAMAGE) != null) {
            this.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(6); // Fixed attack damage
        }
    }


    @Override
    protected void registerGoals() {
        super.registerGoals();
        
        // Clear all existing goals to ensure a clean state
        this.goalSelector.removeAllGoals(goal -> true);
        this.targetSelector.removeAllGoals(goal -> true);

        // --- GOALS (Comportement) ---
        this.goalSelector.addGoal(0, new FloatGoal(this));
        
        // Custom Melee Attack Goal handles priority based on nearby mobs to owner
        this.goalSelector.addGoal(1, new CustomMeleeAttackGoal(this, 1.2D, false)); // Highest priority for attacking when allowed
        this.goalSelector.addGoal(2, new FollowOwnerGoal(this, 1.2D, 2.5F, 1.5F, false)); // Lower priority, acts as fallback
        
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(5, new net.minecraft.world.entity.ai.goal.RandomStrollGoal(this, 0.8D));


        // --- TARGETS (Ciblage) ---
        // OwnerHurtByTargetGoal and OwnerHurtTargetGoal are essential for a tamed mob's defense of its owner.
        // We'll keep them, but the 'hurt' method ensures the knight won't retaliate against players.
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this)); // Targets whatever hurt the owner
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this)); // Targets the owner's target
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, ZombieEntity.class, true, true));
        this.targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, CrawlerEntity.class, true, true));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, HellhoundEntity.class, true, true));
        this.targetSelector.addGoal(6, new HurtByTargetGoal(this)); // Targets whatever hurt the knight itself
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
        // Allow fall damage
        if (source.is(DamageTypes.FALL)) {
            return super.hurt(source, amount);
        }

        // --- IMPORTANT CHANGE: IGNORE ALL DAMAGE FROM PLAYERS ---
        // Check if the damage source is a player or caused by a player's projectile
        // This makes the White Knight immune to player attacks and player projectiles
        if (source.getEntity() instanceof Player || (source.getDirectEntity() instanceof AbstractArrow && ((AbstractArrow) source.getDirectEntity()).getOwner() instanceof Player)) {
            return false; // Ignore all damage from players or their projectiles
        }
        
        // For any other damage source (e.g., mobs, environment other than fall), process normally
        return super.hurt(source, amount);
    }

    @Override
    public InteractionResult mobInteract(Player sourceentity, InteractionHand hand) {
        ItemStack itemstack = sourceentity.getItemInHand(hand);
        Item item = itemstack.getItem();

        // Taming logic remains
        if (!this.isTame() && this.isSpawnedByPlayer()) {
            this.tame(sourceentity);
            this.level().broadcastEntityEvent(this, (byte) 7);
            this.setPersistenceRequired();
            this.applyFixedAttributes();
            this.registerGoals(); // Re-register goals after taming to ensure proper behavior
            sourceentity.displayClientMessage(Component.literal("§aLe Chevalier Blanc a été apprivoisé !"), true);
            return InteractionResult.SUCCESS;
        }

        if (this.isTame() && this.isOwnedBy(sourceentity)) {
            // Healing logic remains
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
        builder = builder.add(Attributes.MOVEMENT_SPEED, 0.22);        
        builder = builder.add(Attributes.MAX_HEALTH, 30);
        builder = builder.add(Attributes.ARMOR, 10);
        builder = builder.add(Attributes.ATTACK_DAMAGE, 3.5);
        builder = builder.add(Attributes.FOLLOW_RANGE, AGGRO_RADIUS);
        builder = builder.add(Attributes.KNOCKBACK_RESISTANCE, 1);
        return builder;
    }

    @Override
    public void tick() {
        super.tick();
        
        if (this.sweepAttackCooldown > 0) {
            this.sweepAttackCooldown--;
        }

        // --- Particle Effect for Aggro Radius (Client-side only) ---
        if (this.level().isClientSide() && this.isTame()) {
            int particlesPerSide = 16;
            for (int i = 0; i < particlesPerSide; i++) {
                double angle = Math.toRadians((double) i / particlesPerSide * 360.0D);
                double xOffset = Math.cos(angle) * AGGRO_RADIUS;
                double zOffset = Math.sin(angle) * AGGRO_RADIUS;
                
                this.level().addParticle(ParticleTypes.CRIT,
                    this.getX() + xOffset, 
                    this.getY() + 0.5D,
                    this.getZ() + zOffset, 
                    0.0D, 0.0D, 0.0D
                );
            }
        }

        // --- DISAPPEARANCE LOGIC ---
        if (!this.level().isClientSide() && this.isTame()) { // Only execute on the server side for proper despawn
            LivingEntity owner = this.getOwner();
            // If the owner is null (e.g., owner disconnected or unloaded) or the owner is dead
            if (owner == null || !owner.isAlive()) {
                this.discard(); // Remove the entity from the world
                return; // Stop further tick processing for this entity
            }

            // Check if the owner has the required mob effect
            // Replace ZombieroolModMobEffects.PERKS_EFFECT_ROYAL_BEER_MOB_EFFECT.get() with the actual path to your mob effect.
            if (!owner.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_ROYAL_BEER.get())) { // Corrected line
			    this.discard(); // Remove the entity if the owner loses the effect
			}
        }
    }

    public void performSweepAttack(LivingEntity target) {
        if (this.sweepAttackCooldown > 0) {
            return;
        }
    
        this.level().broadcastEntityEvent(this, (byte) 29); // animation sweep
    
        double sweepRange = 1.5;
        AABB sweepArea = target.getBoundingBox().inflate(sweepRange, 0.25D, sweepRange);
    
        List<LivingEntity> targetsInSweep = this.level().getEntitiesOfClass(LivingEntity.class, sweepArea, (entity) -> {
            return entity != this &&
                   (this.getOwner() == null || entity != this.getOwner()) && // Do not hit owner
                   ! (entity instanceof Player) && // Explicitly do not hit players
                   this.distanceToSqr(entity) < (sweepRange * sweepRange * 1.5) &&
                   !this.isAlliedTo(entity);
        });
    
        int hitCount = 0;
        float baseDamage = (float) this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float sweepDamageMultiplier = 0.75f;
    
        for (LivingEntity entity : targetsInSweep) {
            if (hitCount >= 4) break;
    
            float damage = baseDamage * sweepDamageMultiplier;
            entity.hurt(this.damageSources().mobAttack(this), damage);
    
            double deltaX = entity.getX() - this.getX();
            double deltaZ = entity.getZ() - this.getZ();
            double distance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
            if (distance != 0.0D) {
                entity.push(deltaX / distance * 0.4D, 0.2D, deltaZ / distance * 0.4D);
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

    // --- New method to determine if arrows pass through ---
    @Override
    public boolean isPickable() {
        return false; // Makes arrows and other projectiles pass through this entity
    }

    static class CustomMeleeAttackGoal extends MeleeAttackGoal {
        private final WhiteKnightEntity knight;
        private int lastPathFindTick;
        private final double speedModifier;

        public CustomMeleeAttackGoal(WhiteKnightEntity mob, double speedModifier, boolean followingTarget) {
            super(mob, speedModifier, followingTarget);
            this.knight = mob;
            this.speedModifier = speedModifier;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            // Only use this goal if the knight is tamed
            if (!this.knight.isTame()) {
                return false;
            }

            LivingEntity owner = this.knight.getOwner();
            if (owner == null) {
                return false;
            }

            // Check for aggressive mobs near the owner
            boolean hasAggressiveMobsNearOwner = false;
            AABB ownerProtectionArea = owner.getBoundingBox().inflate(WhiteKnightEntity.OWNER_PROTECTION_RADIUS);
            List<LivingEntity> mobsNearOwner = this.knight.level().getEntitiesOfClass(LivingEntity.class, ownerProtectionArea, (entity) -> {
                return this.isAggressive(entity.getType()) && entity.isAlive() && entity != owner;
            });

            if (!mobsNearOwner.isEmpty()) {
                this.knight.setTarget(null); // Clear target if protecting owner is higher priority
                return false; 
            }
            
            // If no aggressive mobs are near the owner, then find a target and engage
            LivingEntity bestTarget = findBestReachableTarget();
            if (bestTarget != null) {
                this.knight.setTarget(bestTarget);
                return super.canUse(); // Let the super class validate if a target is present
            }
            return false;
        }

        @Override
        public boolean canContinueToUse() {
            // Continue if tamed, has a valid target, and super says so,
            // and no aggressive mobs have appeared near the owner.
            if (!this.knight.isTame()) {
                return false;
            }

            LivingEntity owner = this.knight.getOwner();
            if (owner == null) {
                return false;
            }

            // Re-check for aggressive mobs near the owner
            boolean hasAggressiveMobsNearOwner = false;
            AABB ownerProtectionArea = owner.getBoundingBox().inflate(WhiteKnightEntity.OWNER_PROTECTION_RADIUS);
            List<LivingEntity> mobsNearOwner = this.knight.level().getEntitiesOfClass(LivingEntity.class, ownerProtectionArea, (entity) -> {
                return this.isAggressive(entity.getType()) && entity.isAlive() && entity != owner;
            });

            if (!mobsNearOwner.isEmpty()) {
                // If mobs are now near the owner, stop attacking and let FollowOwnerGoal take over
                this.knight.setTarget(null);
                return false; 
            }

            return super.canContinueToUse(); 
        }

        @Override
        public void start() {
            super.start();
            this.lastPathFindTick = 0;
        }

        @Override
        public void stop() {
            super.stop();
        }

        @Override
        public void tick() {
            super.tick();
        
            LivingEntity target = this.knight.getTarget();
            if (target == null) {
                return;
            }
        
            // Perform sweep attack logic
            if (this.knight.distanceToSqr(target) < (this.getAttackReachSqr(target) * 1.5) && this.knight.sweepAttackCooldown == 0) {
                AABB checkArea = this.knight.getBoundingBox().inflate(1.5D); 
                List<LivingEntity> nearbyHostiles = this.knight.level().getEntitiesOfClass(LivingEntity.class, checkArea, (e) -> {
                    return e != this.knight && (this.knight.getOwner() == null || e != this.knight.getOwner()) && this.knight.hasLineOfSight(e) && this.isAggressive(e.getType());
                });
                
                if (!nearbyHostiles.isEmpty()) {
                    this.knight.performSweepAttack(target);
                    return; 
                }
            }
        
            // Pathfinding Logic:
            if (this.knight.tickCount - this.lastPathFindTick >= 20) { // Check every second (20 ticks)
                this.lastPathFindTick = this.knight.tickCount;
                
                // Try to find the best reachable target, prioritizing closer ones
                LivingEntity bestReachableTarget = findBestReachableTarget();
                
                if (bestReachableTarget != null && bestReachableTarget != target) {
                    // If a better reachable target is found, switch to it
                    this.knight.setTarget(bestReachableTarget);
                    this.knight.getNavigation().moveTo(bestReachableTarget, this.speedModifier);
                } else if (!this.knight.getNavigation().moveTo(target, this.speedModifier)) {
                    // If current target is unreachable, try moving to a nearby random point
                    Vec3 targetPos = target.position();
                    for (int i = 0; i < 3; i++) { 
                        double offsetX = (this.knight.getRandom().nextDouble() - 0.5D) * 3.0D;
                        double offsetZ = (this.knight.getRandom().nextDouble() - 0.5D) * 3.0D; 
                        Vec3 newPos = new Vec3(targetPos.x + offsetX, targetPos.y, targetPos.z + offsetZ);
                        if (this.knight.getNavigation().moveTo(newPos.x, newPos.y, newPos.z, this.speedModifier)) { 
                            return; 
                        }
                    }
                }
            }
        }
        
        // Helper to check if a mob is aggressive (a hostile)
        private boolean isAggressive(EntityType<?> type) {
            return type == ZombieroolModEntities.ZOMBIE.get() || 
                   type == ZombieroolModEntities.CRAWLER.get() || 
                   type == ZombieroolModEntities.HELLHOUND.get();
        }

        private LivingEntity findBestReachableTarget() {
            double bestDistSqr = Double.MAX_VALUE;
            LivingEntity newTarget = null;
            
            // Scan within the aggro radius for aggressive mobs
            AABB scanArea = this.knight.getBoundingBox().inflate(WhiteKnightEntity.AGGRO_RADIUS);
            List<LivingEntity> possibleTargets = this.knight.level().getEntitiesOfClass(LivingEntity.class, scanArea, (entity) -> {
                // Ensure it's an aggressive mob, not the owner, and has line of sight
                return (this.knight.getOwner() == null || entity != this.knight.getOwner()) && 
                       this.isAggressive(entity.getType()) && 
                       this.knight.hasLineOfSight(entity);
            });

            for (LivingEntity entity : possibleTargets) {
                // Check if a path can be created to the entity
                if (this.knight.getNavigation().createPath(entity, 0) != null) {
                    double distSqr = this.knight.distanceToSqr(entity);
                    // Prioritize closer reachable targets
                    if (distSqr < bestDistSqr) {
                        bestDistSqr = distSqr;
                        newTarget = entity;
                    }
                }
            }
            return newTarget;
        }
    }
}