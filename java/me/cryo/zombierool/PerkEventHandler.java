package me.cryo.zombierool.event;

import me.cryo.zombierool.init.ZombieroolModMobEffects;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool")
public class PerkEventHandler {

    // --- Blood Rage Constants ---
    private static final Random RANDOM = new Random();
    private static final Map<Player, Long> LAST_BLOOD_RAGE_TRIGGER = new ConcurrentHashMap<>();
    private static final int BR_COOLDOWN = 20;
    private static final int BR_CHANCE = 20;
    private static final float BR_HEAL_PCT = 0.15F;

    // --- PHD Flopper Constants ---
    private static final float PHD_MIN_FALL = 3.0F;
    private static final float PHD_EXPLOSION_POWER = 2.0F;

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        // CORRECTION ICI : Pas de instanceof car getEntity() renvoie déjà LivingEntity
        LivingEntity entity = event.getEntity();
        
        // PHD Flopper Immunity
        if (entity.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
            DamageSource src = event.getSource();
            if (src.is(DamageTypes.EXPLOSION) || src.is(DamageTypes.ON_FIRE) || src.is(DamageTypes.IN_FIRE) ||
                src.is(DamageTypes.LAVA) || src.is(DamageTypes.HOT_FLOOR) || src.is(DamageTypes.FALLING_BLOCK)) {
                event.setCanceled(true);
                return;
            }
        }

        // Blood Rage Trigger
        if (event.getSource().getEntity() instanceof Player attacker) {
            if (attacker.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_BLOOD_RAGE.get())) {
                long now = attacker.level().getGameTime();
                long last = LAST_BLOOD_RAGE_TRIGGER.getOrDefault(attacker, 0L);
                
                if (now - last >= BR_COOLDOWN) {
                    float hpPct = attacker.getHealth() / attacker.getMaxHealth();
                    int chance = (int) (BR_CHANCE * hpPct);
                    if (RANDOM.nextInt(Math.max(1, chance)) == 0) {
                        triggerBloodRage(attacker, event.getAmount());
                        LAST_BLOOD_RAGE_TRIGGER.put(attacker, now);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        // PHD Flopper Explosion
        if (entity.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get())) {
            if (event.getDistance() > PHD_MIN_FALL) {
                event.setCanceled(true);
                if (!entity.level().isClientSide()) {
                    int amplifier = 0;
                    if (entity.getEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()) != null) {
                        amplifier = entity.getEffect(ZombieroolModMobEffects.PERKS_EFFECT_PHD_FLOPPER.get()).getAmplifier();
                    }
                    
                    ExplosionDamageCalculator calc = new ExplosionDamageCalculator() {
                        public float getEntityDamageAmount(Explosion explosion, Entity targetEntity) {
                            return targetEntity instanceof Player ? 0.0F : 1.0F;
                        }
                    };

                    entity.level().explode(entity, entity.damageSources().generic(), calc,
                            entity.getX(), entity.getY(), entity.getZ(),
                            PHD_EXPLOSION_POWER + amplifier, false, Level.ExplosionInteraction.NONE);
                            
                    entity.setDeltaMovement(0, 0, 0);
                    entity.hurtMarked = true;
                }
            }
        }
    }

    private static void triggerBloodRage(Player player, float damage) {
        Level world = player.level();
        float heal = damage * BR_HEAL_PCT;
        float cur = player.getHealth();
        float max = player.getMaxHealth();
        
        player.setHealth(Math.min(cur + heal, max));
        
        float overflow = Math.max(0, (cur + heal) - max);
        if (overflow > 0) {
            float abs = player.getAbsorptionAmount();
            player.setAbsorptionAmount(Math.min(abs + overflow, 10));
        }

        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 100, 0));
        if (!world.isClientSide) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0F,
                world.random.nextFloat() * 0.1F + 0.9F);
        }
    }
}