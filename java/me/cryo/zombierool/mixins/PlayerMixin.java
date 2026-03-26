package me.cryo.zombierool.mixins;

import me.cryo.zombierool.ZombiePlayerHandler; 
import me.cryo.zombierool.init.ZombieroolModMobEffects; 
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void zombierool_onPlayerTickMixin(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        AttributeInstance maxHealthAttribute = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxHealthAttribute != null) {
            if (maxHealthAttribute.getBaseValue() != ZombiePlayerHandler.BASE_MAX_HEALTH) {
                maxHealthAttribute.setBaseValue(ZombiePlayerHandler.BASE_MAX_HEALTH);
            }

            if (!player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_JUGGERNOG.get())) {
                if (maxHealthAttribute.getModifier(me.cryo.zombierool.potion.PerksEffectJuggernogMobEffect.JUGGERNOG_UUID) != null) {
                    maxHealthAttribute.removeModifier(me.cryo.zombierool.potion.PerksEffectJuggernogMobEffect.JUGGERNOG_UUID);
                }
            }

            double effectiveMaxHealth = maxHealthAttribute.getValue();
            if (player.getHealth() > effectiveMaxHealth) {
                player.setHealth((float) effectiveMaxHealth);
            }
        }

        if (ZombiePlayerHandler.DISABLE_HUNGER) {
            FoodData foodData = player.getFoodData();
            foodData.setFoodLevel(20);
            foodData.setSaturation(0);
        }

        if (ZombiePlayerHandler.DISABLE_XP) {
            player.experienceLevel = 0;
            player.totalExperience = 0;
            player.experienceProgress = 0;
        }

        if (player.tickCount - ZombiePlayerHandler.getLastHurtTimestamp() > ZombiePlayerHandler.REGEN_DELAY &&
            player.getHealth() < player.getMaxHealth()) {
            player.heal(0.01F);
        }
    }

    @Inject(method = "updatePlayerPose", at = @At("HEAD"), cancellable = true)
    protected void zombierool_updatePlayerPose(CallbackInfo ci) {
        Player player = (Player) (Object) this;

        boolean isDownCrawl = false;
        if (player.level().isClientSide) {
            isDownCrawl = me.cryo.zombierool.handlers.KeyInputHandler.downPlayers.contains(player.getUUID());
        } else {
            isDownCrawl = me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID());
        }

        boolean isManualCrawl = me.cryo.zombierool.player.PlayerCrawlManager.isCrawling(player.getUUID());

        if (isDownCrawl || isManualCrawl) {
            player.setPose(net.minecraft.world.entity.Pose.SWIMMING);
            ci.cancel();
        }
    }
}