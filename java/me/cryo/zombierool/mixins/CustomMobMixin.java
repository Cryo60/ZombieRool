package me.cryo.zombierool.mixins;

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

import javax.annotation.Nullable;
import java.util.List;

import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.entity.WhiteKnightEntity;
import me.cryo.zombierool.player.PlayerDownManager;

@Mixin(Mob.class)
public abstract class CustomMobMixin {

    private static final double SCAN_RADIUS = 1024.0; // OMNISCIENT SCAN

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

            Player nearestPlayer = getNearestPlayerIgnoringZombieBloodAndDown(world, mob, SCAN_RADIUS);
            if (nearestPlayer != null) {
                chosenTarget = nearestPlayer;
                chosenTargetDistanceSqr = mob.distanceToSqr(nearestPlayer);
            }

            WhiteKnightEntity nearestKnight = getNearestWhiteKnight(world, mob, SCAN_RADIUS);
            if (nearestKnight != null) {
                double knightDistanceSqr = mob.distanceToSqr(nearestKnight);
                if (chosenTarget != null) {
                    if (knightDistanceSqr < chosenTargetDistanceSqr) {
                        chosenTarget = nearestKnight;
                    }
                } else {
                    chosenTarget = nearestKnight;
                }
            }

            mob.setTarget(chosenTarget);

            if (this.getTarget() instanceof Player currentTargetPlayer) {
                if (BonusManager.isZombieBloodActive(currentTargetPlayer) || PlayerDownManager.isPlayerDown(currentTargetPlayer.getUUID())) {
                    this.setTarget(null);
                }
            }
        }
    }

    private Player getNearestPlayerIgnoringZombieBloodAndDown(Level world, Mob mob, double scanRadius) {
        AABB scanArea = new AABB(mob.getX() - scanRadius, mob.getY() - scanRadius, mob.getZ() - scanRadius,
            mob.getX() + scanRadius, mob.getY() + scanRadius, mob.getZ() + scanRadius);

        List<Player> players = world.getEntitiesOfClass(
            Player.class,
            scanArea,
            player -> !player.isSpectator() && 
                    mob.distanceToSqr(player) <= scanRadius * scanRadius && 
                    !BonusManager.isZombieBloodActive(player) && 
                    !PlayerDownManager.isPlayerDown(player.getUUID()) 
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

        if (target instanceof Player player && (BonusManager.isZombieBloodActive(player) || PlayerDownManager.isPlayerDown(player.getUUID()))) { 
            ci.cancel();
        }
    }
}