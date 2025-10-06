package net.mcreator.zombierool;

import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Entity;

import net.mcreator.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.world.level.ExplosionDamageCalculator;

@Mod.EventBusSubscriber(modid = "zombierool")
public class PHDFlopperEventHandler {

    private static final float MIN_FALL_DISTANCE_FOR_EXPLOSION = 3.0F;
    private static final float BASE_EXPLOSION_STRENGTH = 2.0F;
    private static final float PLAYER_DAMAGE_MULTIPLIER = 0.0F;
    private static final float DEFAULT_DAMAGE_MULTIPLIER = 1.0F;

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();

        if (entity.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
            DamageSource source = event.getSource();

            if (source.is(DamageTypes.EXPLOSION) ||
                source.is(DamageTypes.ON_FIRE) ||
                source.is(DamageTypes.IN_FIRE) ||
                source.is(DamageTypes.LAVA) ||
                source.is(DamageTypes.HOT_FLOOR) ||
                source.is(DamageTypes.FALLING_BLOCK) ||
                source.is(DamageTypes.FALL)) {

                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
            if (event.getDistance() > MIN_FALL_DISTANCE_FOR_EXPLOSION) {
                event.setCanceled(true);

                if (!entity.level().isClientSide()) {
                    int amplifier = 0;
                    if (entity.getEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()) != null) {
                        amplifier = entity.getEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()).getAmplifier();
                    }

                    ExplosionDamageCalculator damageCalculator = new ExplosionDamageCalculator() {
                        public float getEntityDamageAmount(Explosion explosion, Entity targetEntity) {
                            if (targetEntity instanceof Player) {
                                return PLAYER_DAMAGE_MULTIPLIER;
                            }
                            return DEFAULT_DAMAGE_MULTIPLIER;
                        }
                    };

                    entity.level().explode(
                        entity,
                        entity.damageSources().generic(),
                        damageCalculator,
                        entity.getX(),
                        entity.getY(),
                        entity.getZ(),
                        BASE_EXPLOSION_STRENGTH + amplifier,
                        false,
                        Level.ExplosionInteraction.NONE
                    );

                    entity.setDeltaMovement(0, 0, 0);
                    entity.hurtMarked = true;
                }
            }
        }
    }
}
