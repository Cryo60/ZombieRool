package net.mcreator.zombierool.procedures;

import net.mcreator.zombierool.potion.PerksEffectBloodRageMobEffect;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool")
public class BloodRageEventHandler {

    // Utilisation de variables statiques finales pour la configuration du perk
    private static final Random random = new Random();
    private static final Map<Player, Long> lastTriggerTick = new ConcurrentHashMap<>();
    private static final int COOLDOWN_TICKS = 20; // 1 seconde de cooldown pour des activations plus fréquentes
    private static final int BASE_CHANCE = 20; // Chance de base 1/20
    private static final float HEALTH_HEAL_PERCENTAGE = 0.15F; // Soin de 15% des dégâts infligés
    private static final int BUFF_DURATION_TICKS = 100; // Durée du buff (5 secondes)
    private static final int ABSORPTION_LIMIT = 10; // 5 coeurs d'absorption max
    private static final int BUFF_STRENGTH_LEVEL = 0; // Niveau de force (0 = Force I)
    
    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getSource().getEntity() instanceof Player attacker) {
            if (attacker.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_BLOOD_RAGE.get())) {
                
                Level world = attacker.level();
                long currentTick = world.getGameTime();
                long lastTick = lastTriggerTick.getOrDefault(attacker, 0L);
                
                // Vérifie le cooldown
                if (currentTick - lastTick >= COOLDOWN_TICKS) {
                    
                    // La chance de déclenchement augmente à mesure que le joueur perd de la vie
                    float healthPercentage = attacker.getHealth() / attacker.getMaxHealth();
                    int effectiveChance = (int) (BASE_CHANCE * healthPercentage);
                    if (random.nextInt(Math.max(1, effectiveChance)) == 0) { // S'assure que la chance ne soit pas 0
                        
                        triggerPerkEffect(attacker, event.getAmount());
                        
                        // Met à jour le cooldown
                        lastTriggerTick.put(attacker, currentTick);
                    }
                }
            }
        }
    }
    
    private static void triggerPerkEffect(Player player, float damageDealt) {
        Level world = player.level();
        
        // Calcule le soin en fonction des dégâts infligés
        float regenAmount = damageDealt * HEALTH_HEAL_PERCENTAGE;
        float currentHealth = player.getHealth();
        float maxHealth = player.getMaxHealth();
        
        // Applique le soin et l'absorption
        float potentialHealth = currentHealth + regenAmount;
        float overflow = Math.max(0, potentialHealth - maxHealth);
        player.setHealth(Math.min(potentialHealth, maxHealth));
        
        if (overflow > 0) {
            float currentAbsorption = player.getAbsorptionAmount();
            float newAbsorption = Math.min(currentAbsorption + overflow, ABSORPTION_LIMIT);
            player.setAbsorptionAmount(newAbsorption);
        }
        
        // Applique un buff offensif (Force I)
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, BUFF_DURATION_TICKS, BUFF_STRENGTH_LEVEL));
        
        // Son et particules pour le feedback
        if (!world.isClientSide) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ZOMBIE_VILLAGER_CURE, SoundSource.PLAYERS, 1.0F,
                world.random.nextFloat() * 0.1F + 0.9F);
        }
    }
}
