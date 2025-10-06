package net.mcreator.zombierool.events;

import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.ColdWaterEffectManager;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
// Removed: import net.minecraftforge.event.entity.living.LivingFallEvent; // No longer needed for gravity
import net.minecraftforge.event.TickEvent;
// Removed: import net.minecraftforge.event.entity.living.LivingEvent; // No longer needed for gravity
import net.minecraftforge.eventbus.api.SubscribeEvent;
// Removed: import net.minecraftforge.eventbus.api.EventPriority; // No longer needed for gravity
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerEventsHandler {

    // --- Gravity System (Removed) ---
    // The onLivingTick and onLivingFall methods related to custom gravity have been removed.

    // --- Cold Water Effect System ---
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            ServerPlayer player = (ServerPlayer) event.player;
            ServerLevel level = (ServerLevel) player.level();
            WorldConfig worldConfig = WorldConfig.get(level);

            if (worldConfig.isColdWaterEffectEnabled()) {
                boolean inWater = player.isUnderWater() || player.isInWater();
                
                ColdWaterEffectManager.updateIntensity(level, player, inWater);

                // Define the threshold for activating/deactivating the slowness effect and particles
                final float SLOWNESS_THRESHOLD = 0.20f; // 20% intensity

                if (ColdWaterEffectManager.getIntensity(player) >= SLOWNESS_THRESHOLD) {
                    // Apply Slowness effect (without damage) - STRENGTHENED TO AMPLIFIER 2 (Slowness III)
                    // Duration of 20 ticks (1 second), amplifier 2 (Slowness III), ambient=false, showParticles=false
                    if (!player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN) || player.getEffect(MobEffects.MOVEMENT_SLOWDOWN).getAmplifier() < 2) {
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false));
                    }

                    // Spawn powder snow particles around the player
                    // Adjust particle count and spread as desired
                    if (player.tickCount % 5 == 0) {
                        level.sendParticles(ParticleTypes.SNOWFLAKE, player.getX(), player.getY() + player.getBbHeight() / 2.0D, player.getZ(),
                                5, 0.2D, 0.5D, 0.2D, 0.0D);
                    }
                } else {
                    // If intensity drops below threshold, remove slowness and stop particles
                    if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    }
                }
            } else {
                // If cold water effect is disabled in WorldConfig, reset player intensity and remove slowness
                if (ColdWaterEffectManager.getIntensity(player) > 0.0f || player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                    ColdWaterEffectManager.resetIntensity(player);
                    if (player.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
                        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    }
                }
            }
        }
    }
}
