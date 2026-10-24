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
import java.util.WeakHashMap;
import net.minecraftforge.eventbus.api.EventPriority;

@Mod.EventBusSubscriber(modid = "zombierool")
public class PerkEventHandler {

    // --- Blood Rage Constants ---
    private static final Map<Player, Long> LAST_BLOOD_RAGE_TRIGGER = new WeakHashMap<>();
    private static final int BR_COOLDOWN = 20;
    private static final int BR_CHANCE = 20;
    private static final float BR_HEAL_PCT = 0.15F;

    // --- PHD Flopper Constants ---
    private static final float PHD_MIN_FALL = 3.0F;
    private static final float PHD_EXPLOSION_POWER = 2.0F;

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0 || event.isCanceled()) return;
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
        Entity owner = event.getSource().getEntity();
        if (!(owner instanceof Player) && event.getSource().getDirectEntity() instanceof net.minecraft.world.entity.projectile.Projectile projectile) owner = projectile.getOwner();
        if (owner instanceof Player attacker && attacker != entity && attacker.isAlive()) {
            if (attacker.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_BLOOD_RAGE.get())) {
                long now = attacker.level().getGameTime();
                long last = LAST_BLOOD_RAGE_TRIGGER.getOrDefault(attacker, Long.MIN_VALUE / 2);

                if (now < last || now - last >= BR_COOLDOWN) {
                    float hpPct = attacker.getHealth() / attacker.getMaxHealth();
                    float chance = BR_CHANCE / 100.0F + 0.60F * (1.0F - hpPct);
                    if (attacker.getRandom().nextFloat() < chance) {
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
        if (world instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART, player.getX(), player.getY()+1.2, player.getZ(), 3, .25, .25, .25, 0);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0F,
                world.random.nextFloat() * 0.1F + 0.9F);
        }
    }
}
