package net.mcreator.zombierool.potion;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import java.util.UUID;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance; 

import net.mcreator.zombierool.entity.WhiteKnightEntity;
import net.mcreator.zombierool.init.ZombieroolModEntities;

public class PerksEffectRoyalBeerMobEffect extends MobEffect {

    private static final String WHITE_KNIGHT_UUID_TAG = "WhiteKnightUUID";

    public PerksEffectRoyalBeerMobEffect() {
        super(MobEffectCategory.BENEFICIAL, -13434676);
    }

    @Override
    public String getDescriptionId() {
        return "effect.zombierool.perks_effect_royal_beer";
    }

    @Override
    public boolean isInstantenous() {
        return false;
    }

    @Override
    public boolean isDurationEffectTick(int duration, int amplifier) {
        // This effect needs to tick every time to check conditions
        return true; 
    }

    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) entity.level();
        CompoundTag persistentData = entity.getPersistentData();

        // --- Check if the owner is still valid or alive ---
        // If the entity is dead, or if it no longer has this effect,
        // we should trigger the White Knight's removal.
        // This effectively handles cases where the owner dies or the effect wears off naturally.
        if (!entity.isAlive() || !entity.hasEffect(this)) {
            handleWhiteKnightRemoval(serverLevel, persistentData);
            // After handling removal, we should ensure the effect is fully gone if not already.
            // This prevents the tick from running unnecessarily on a 'dead' effect.
            if (entity.hasEffect(this)) { // Check again in case it was just about to expire
                 entity.removeEffect(this);
            }
            return; // Stop further processing for this tick if owner is invalid
        }


        // --- Handle White Knight spawning/existence check ---
        if (!persistentData.contains(WHITE_KNIGHT_UUID_TAG)) {
            // No White Knight UUID stored, attempt to spawn one
            WhiteKnightEntity whiteKnight = ZombieroolModEntities.WHITE_KNIGHT.get().create(serverLevel);
            if (whiteKnight != null) {
                whiteKnight.setPos(entity.getX(), entity.getY() + 0.5, entity.getZ());
                whiteKnight.setSpawnedByPlayer(true);
                whiteKnight.tame(entity instanceof Player ? (Player) entity : null);
                whiteKnight.setPersistenceRequired();
                whiteKnight.finalizeSpawn(serverLevel, serverLevel.getCurrentDifficultyAt(whiteKnight.blockPosition()),
                        MobSpawnType.MOB_SUMMONED, null, null);
                serverLevel.addFreshEntity(whiteKnight);

                persistentData.putString(WHITE_KNIGHT_UUID_TAG, whiteKnight.getUUID().toString());
            }
        } else {
            // White Knight UUID is stored, check its status
            UUID whiteKnightUUID = UUID.fromString(persistentData.getString(WHITE_KNIGHT_UUID_TAG));
            Entity existingEntity = serverLevel.getEntity(whiteKnightUUID);

            // If the entity no longer exists or is not a WhiteKnightEntity,
            // or if it's no longer tamed by this owner, then it's gone.
            if (!(existingEntity instanceof WhiteKnightEntity) || !((WhiteKnightEntity) existingEntity).isTame() || ((WhiteKnightEntity) existingEntity).getOwner() != entity) {
                // Remove the stored UUID and the effect from the owner.
                // This will cause the next tick's 'if (!entity.isAlive() || !entity.hasEffect(this))' check
                // to trigger handleWhiteKnightRemoval.
                persistentData.remove(WHITE_KNIGHT_UUID_TAG);
                entity.removeEffect(this); 
            }
        }
    }

    // IMPORTANT: We are REMOVING the onEffectStopped override.
    // The logic to despawn the knight is now handled directly in applyEffectTick
    // when the owner is no longer valid or no longer has the effect.
    /*
    @Override // This @Override was causing issues, removing it.
    public void onEffectStopped(LivingEntity entity, MobEffectInstance instance) {
        // super.onEffectStopped(entity, instance); // No super call if not overriding
        if (!entity.level().isClientSide()) {
            ServerLevel serverLevel = (ServerLevel) entity.level();
            CompoundTag persistentData = entity.getPersistentData();
            handleWhiteKnightRemoval(serverLevel, persistentData);
        }
    }
    */

    // --- Helper method to handle White Knight despawn ---
    private void handleWhiteKnightRemoval(ServerLevel serverLevel, CompoundTag persistentData) {
        if (persistentData.contains(WHITE_KNIGHT_UUID_TAG)) {
            UUID whiteKnightUUID = UUID.fromString(persistentData.getString(WHITE_KNIGHT_UUID_TAG));
            Entity whiteKnight = serverLevel.getEntity(whiteKnightUUID);

            // Ensure it's actually a WhiteKnightEntity before discarding
            if (whiteKnight instanceof WhiteKnightEntity) {
                whiteKnight.discard(); // Remove the entity from the world
            }
            persistentData.remove(WHITE_KNIGHT_UUID_TAG); // Clean up the stored UUID
        }
    }
}