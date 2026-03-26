package me.cryo.zombierool.mixins;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
import me.cryo.zombierool.item.throwable.MonkeyBomb;

@Mixin(Mob.class)
public abstract class CustomMobMixin {
    private static final double MONKEY_SCAN_RADIUS_SQR = 32.0 * 32.0;
    private static final double KNIGHT_SCAN_RADIUS = 32.0;

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
            
            // 1. PRIORITÉ ABSOLUE : BOMBE SINGE (Via liste statique ultra-rapide)
            MonkeyBomb.MonkeyBombEntity closestMonkey = null;
            double closestMonkeyDistSqr = MONKEY_SCAN_RADIUS_SQR;

            for (MonkeyBomb.MonkeyBombEntity mb : MonkeyBomb.MonkeyBombEntity.ACTIVE_MONKEYS) {
                if (mb.level() == world && mb.isLanded()) {
                    double distSqr = mob.distanceToSqr(mb);
                    if (distSqr < closestMonkeyDistSqr) {
                        closestMonkeyDistSqr = distSqr;
                        closestMonkey = mb;
                    }
                }
            }

            if (closestMonkey != null) {
                this.setTarget(null); // On efface la cible joueur
                // On met à jour le pathfinding seulement 2 fois par seconde pour éviter le lag massif
                if (mob.tickCount % 10 == 0) {
                    mob.getNavigation().moveTo(closestMonkey.getX(), closestMonkey.getY(), closestMonkey.getZ(), 1.25);
                }
                return; // On coupe l'IA ici, le zombie est hypnotisé par le singe
            }

            // 2. COMPORTEMENT STANDARD (Joueurs / Chevaliers)
            
            // Optimisation : au lieu de scanner le monde entier (lag), on itère juste sur les joueurs connectés
            Player nearestPlayer = null;
            double closestPlayerDistSqr = Double.MAX_VALUE;

            for (Player p : world.players()) {
                if (p.isSpectator() || BonusManager.isZombieBloodActive(p) || PlayerDownManager.isPlayerDown(p.getUUID())) {
                    continue;
                }
                double distSqr = mob.distanceToSqr(p);
                if (distSqr < closestPlayerDistSqr) {
                    closestPlayerDistSqr = distSqr;
                    nearestPlayer = p;
                }
            }

            // Recherche des chevaliers dans un petit rayon (32 blocs max)
            WhiteKnightEntity nearestKnight = null;
            double closestKnightDistSqr = KNIGHT_SCAN_RADIUS * KNIGHT_SCAN_RADIUS;
            List<WhiteKnightEntity> knights = world.getEntitiesOfClass(WhiteKnightEntity.class, mob.getBoundingBox().inflate(KNIGHT_SCAN_RADIUS));
            
            for (WhiteKnightEntity wk : knights) {
                double distSqr = mob.distanceToSqr(wk);
                if (distSqr < closestKnightDistSqr) {
                    closestKnightDistSqr = distSqr;
                    nearestKnight = wk;
                }
            }

            LivingEntity chosenTarget = nearestPlayer;
            if (nearestKnight != null && closestKnightDistSqr < closestPlayerDistSqr) {
                chosenTarget = nearestKnight;
            }

            mob.setTarget(chosenTarget);

            // Sécurité additionnelle
            if (this.getTarget() instanceof Player currentTargetPlayer) {
                if (BonusManager.isZombieBloodActive(currentTargetPlayer) || PlayerDownManager.isPlayerDown(currentTargetPlayer.getUUID())) {
                    this.setTarget(null);
                }
            }
        }
    }

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void onSetTarget(LivingEntity target, CallbackInfo ci) {
        Mob mob = (Mob) (Object) this;
        if (mob instanceof WhiteKnightEntity || target instanceof WhiteKnightEntity) return;

        if (target instanceof Player player && (BonusManager.isZombieBloodActive(player) || PlayerDownManager.isPlayerDown(player.getUUID()))) { 
            ci.cancel();
        }
    }
}