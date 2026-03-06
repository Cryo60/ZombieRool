package me.cryo.zombierool.potion;
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
import me.cryo.zombierool.entity.WhiteKnightEntity;
import me.cryo.zombierool.init.ZombieroolModEntities;
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
        return true; 
    }
    @Override
    public void applyEffectTick(LivingEntity entity, int amplifier) {
        if (entity.level().isClientSide()) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) entity.level();
        CompoundTag persistentData = entity.getPersistentData();
        if (!entity.isAlive() || !entity.hasEffect(this)) {
            handleWhiteKnightRemoval(serverLevel, persistentData);
            if (entity.hasEffect(this)) { 
                 entity.removeEffect(this);
            }
            return; 
        }
        if (!persistentData.contains(WHITE_KNIGHT_UUID_TAG)) {
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
            UUID whiteKnightUUID = UUID.fromString(persistentData.getString(WHITE_KNIGHT_UUID_TAG));
            Entity existingEntity = serverLevel.getEntity(whiteKnightUUID);
            if (!(existingEntity instanceof WhiteKnightEntity) || !((WhiteKnightEntity) existingEntity).isTame() || ((WhiteKnightEntity) existingEntity).getOwner() != entity) {
                // If the knight is missing or invalid, we just remove the tag reference.
                // We do NOT remove the effect from the player. This allows the logic above to run in the next tick (or fall through)
                // and spawn a new knight immediately, effectively "restoring" the perk functionality.
                persistentData.remove(WHITE_KNIGHT_UUID_TAG);
            }
        }
    }
    private void handleWhiteKnightRemoval(ServerLevel serverLevel, CompoundTag persistentData) {
        if (persistentData.contains(WHITE_KNIGHT_UUID_TAG)) {
            UUID whiteKnightUUID = UUID.fromString(persistentData.getString(WHITE_KNIGHT_UUID_TAG));
            Entity whiteKnight = serverLevel.getEntity(whiteKnightUUID);
            if (whiteKnight instanceof WhiteKnightEntity) {
                whiteKnight.discard(); 
            }
            persistentData.remove(WHITE_KNIGHT_UUID_TAG); 
        }
    }
}