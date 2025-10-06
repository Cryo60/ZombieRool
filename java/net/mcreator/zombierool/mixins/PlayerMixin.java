package net.mcreator.zombierool.mixins;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// IMPORTE TA CLASSE ZOMBIEPLAYERHANDLER MAINTENANT QU'ELLE EXISTE
import net.mcreator.zombierool.ZombiePlayerHandler; 
import net.mcreator.zombierool.init.ZombieroolModMobEffects; // Import your effect init class

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void zombierool_onPlayerTickMixin(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        // --- Gestion de la vie maximale ---
        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            // Always ensure base health is correct
            if (maxHealthAttribute.getBaseValue() != ZombiePlayerHandler.BASE_MAX_HEALTH) {
                maxHealthAttribute.setBaseValue(ZombiePlayerHandler.BASE_MAX_HEALTH);
            }

            // Check if Mastodonte effect is active. If not, ensure the modifier is removed.
            // This is largely handled by Mojang's effect system for transient modifiers,
            // but it's good to keep the health clamping logic robust.
            if (!player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_MASTODONTE.get())) {
                // If the effect is NOT active, ensure the Mastodonte attribute modifier is NOT present.
                // This typically isn't strictly necessary as vanilla handles transient modifiers,
                // but it's a good fail-safe if anything goes wrong.
                if (maxHealthAttribute.getModifier(net.mcreator.zombierool.potion.PerksEffectMastodonteMobEffect.MASTODONTE_UUID) != null) {
                    maxHealthAttribute.removeModifier(net.mcreator.zombierool.potion.PerksEffectMastodonteMobEffect.MASTODONTE_UUID);
                }
            }


            // This part is crucial for clamping health when max health decreases
            double effectiveMaxHealth = maxHealthAttribute.getValue();
            if (player.getHealth() > effectiveMaxHealth) {
                player.setHealth((float) effectiveMaxHealth);
            }
        }

        // --- Désactivation de la faim ---
        if (ZombiePlayerHandler.DISABLE_HUNGER) {
            FoodData foodData = player.getFoodData();
            foodData.setFoodLevel(20);
            foodData.setSaturation(0);
        }

        // --- Désactivation de l'expérience (XP) ---
        if (ZombiePlayerHandler.DISABLE_XP) {
            player.experienceLevel = 0;
            player.totalExperience = 0;
            player.experienceProgress = 0;
        }

        // --- Régénération de vie lente ---
        if (player.tickCount - ZombiePlayerHandler.getLastHurtTimestamp() > ZombiePlayerHandler.REGEN_DELAY &&
            player.getHealth() < player.getMaxHealth()) {
            player.heal(0.01F);
        }
    }
}