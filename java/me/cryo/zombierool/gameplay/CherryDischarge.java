package me.cryo.zombierool.gameplay;

import me.cryo.zombierool.core.manager.DamageManager;
import me.cryo.zombierool.entity.CrawlerCorpse;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;

/** Shared by magazine reloads, shell reloads and TacZ reloads. */
public final class CherryDischarge {
    private CherryDischarge() {}

    public static boolean isTarget(LivingEntity entity) {
        return entity instanceof Monster && !(entity instanceof CrawlerCorpse)
                && entity.isAlive() && !entity.isRemoved() && entity.deathTime == 0;
    }

    public static void trigger(Player player) {
        if (!(player.level() instanceof ServerLevel level)
                || !player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_CHERRY.get())) return;

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(3.0), CherryDischarge::isTarget)) {
            // A lethal discharge still hits a living zombie. Reject existing corpses
            // before damage, and don't play the reaction when the hit is cancelled.
            if (!isTarget(target) || !DamageManager.applyDamage(target,
                    level.damageSources().playerAttack(player), 5.0f)) continue;
            if (target.isAlive()) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 4));
            if (target instanceof ZombieEntity || target instanceof CrawlerEntity || target instanceof HellhoundEntity) {
                level.playSound(null, target.getX(), target.getY(), target.getZ(),
                        ZombieroolModSounds.ZOMBIE_ELEC.get(), SoundSource.HOSTILE, 0.4f, 1.0f);
            }
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, player.getX(), player.getY() + 1,
                player.getZ(), 30, 1.5, 1.0, 1.5, 0.1);
        level.playSound(null, player.blockPosition(), ZombieroolModSounds.RELOADING_WITH_CHERRY.get(),
                SoundSource.PLAYERS, 0.35f, 1.0f);
    }
}
