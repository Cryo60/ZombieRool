package net.mcreator.zombierool.mixins;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import javax.annotation.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import net.mcreator.zombierool.bonuses.BonusManager;
import net.mcreator.zombierool.entity.WhiteKnightEntity;
import net.mcreator.zombierool.player.PlayerDownManager; // NOUVEL IMPORT

@Mixin(Mob.class)
public abstract class CustomMobMixin {

    private static final double SCAN_RADIUS = 220.0; // General scan radius

    @Shadow
    @Nullable
    public abstract LivingEntity getTarget();

    @Shadow
    public abstract void setTarget(@Nullable LivingEntity target);

    @Inject(method = "customServerAiStep", at = @At("TAIL"))
    private void onAiUpdate(CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;

        if (mob instanceof WhiteKnightEntity) {
            return;
        }

        Level world = mob.level();

        if (!world.isClientSide()) {
            LivingEntity chosenTarget = null;
            double chosenTargetDistanceSqr = Double.MAX_VALUE;

            // Find the nearest valid player target (not spectator, not Zombie Blood active, NOT DOWN)
            Player nearestPlayer = getNearestPlayerIgnoringZombieBloodAndDown(world, mob, SCAN_RADIUS); // CHANGED METHOD CALL
            if (nearestPlayer != null) {
                chosenTarget = nearestPlayer;
                chosenTargetDistanceSqr = mob.distanceToSqr(nearestPlayer);
            }

            // Find the nearest White Knight (any mode - now just one behavior)
            WhiteKnightEntity nearestKnight = getNearestWhiteKnight(world, mob, SCAN_RADIUS);

            if (nearestKnight != null) {
                double knightDistanceSqr = mob.distanceToSqr(nearestKnight);

                // If a valid player is found, compare distances
                if (chosenTarget != null) {
                    if (knightDistanceSqr < chosenTargetDistanceSqr) {
                        chosenTarget = nearestKnight;
                    }
                } else {
                    // If no player was found, the knight becomes the target
                    chosenTarget = nearestKnight;
                }
            }

            // Set the chosen target
            mob.setTarget(chosenTarget);

            // Clear target if the current target is a player with Zombie Blood and it wasn't re-targeted
            // OR if the target is now DOWN (handled by onSetTarget, but good to double check)
            if (this.getTarget() instanceof Player currentTargetPlayer) {
                if (BonusManager.isZombieBloodActive(currentTargetPlayer) || PlayerDownManager.isPlayerDown(currentTargetPlayer.getUUID())) {
                    this.setTarget(null);
                }
            }
        }
    }

    // NOUVELLE MÉTHODE : Inclut la vérification de l'état "down"
    private Player getNearestPlayerIgnoringZombieBloodAndDown(Level world, Mob mob, double scanRadius) {
        AABB scanArea = new AABB(mob.getX() - scanRadius, mob.getY() - scanRadius, mob.getZ() - scanRadius,
            mob.getX() + scanRadius, mob.getY() + scanRadius, mob.getZ() + scanRadius);

        List<Player> players = world.getEntitiesOfClass(
            Player.class,
            scanArea,
            player -> !player.isSpectator() && // Pas en mode spectateur
                    mob.distanceToSqr(player) <= scanRadius * scanRadius && // Dans le rayon de scan
                    !BonusManager.isZombieBloodActive(player) && // Pas sous l'effet Zombie Blood
                    !PlayerDownManager.isPlayerDown(player.getUUID()) // NOUVEAU: Pas dans l'état "down"
        );

        Player nearest = null;
        double closestDistance = Double.MAX_VALUE;

        for (Player player : players) {
            double distance = mob.distanceToSqr(player);
            if (distance < closestDistance) {
                closestDistance = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private WhiteKnightEntity getNearestWhiteKnight(Level world, Mob mob, double scanRadius) {
        AABB scanArea = new AABB(mob.getX() - scanRadius, mob.getY() - scanRadius, mob.getZ() - scanRadius,
            mob.getX() + scanRadius, mob.getY() + scanRadius, mob.getZ() + scanRadius);

        List<WhiteKnightEntity> whiteKnights = world.getEntitiesOfClass(
            WhiteKnightEntity.class,
            scanArea,
            whiteKnight -> mob.distanceToSqr(whiteKnight) <= scanRadius * scanRadius
        );

        WhiteKnightEntity nearest = null;
        double closestDistance = Double.MAX_VALUE;

        for (WhiteKnightEntity wk : whiteKnights) {
            double distance = mob.distanceToSqr(wk);
            if (distance < closestDistance) {
                closestDistance = distance;
                nearest = wk;
            }
        }
        return nearest;
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void onSetTarget(LivingEntity target, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;

        if (mob instanceof WhiteKnightEntity) {
            return;
        }

        if (target instanceof WhiteKnightEntity) {
            return;
        }

        // Cancel the target if it's a player with active Zombie Blood OR if the player is "down"
        if (target instanceof Player player && (BonusManager.isZombieBloodActive(player) || PlayerDownManager.isPlayerDown(player.getUUID()))) { // CHANGED CONDITION
            ci.cancel();
        }
    }
}
