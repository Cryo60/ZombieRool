package net.mcreator.zombierool;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.packet.SyncColdWaterStatePacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer; // NEW: Import ServerPlayer

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the cold water effect intensity for each player on the server side.
 * This data is transient per session unless explicitly saved (e.g., as part of player NBT or a world saved data).
 * For simplicity, we'll store it in a map. If persistence across server restarts is needed,
 * it would require saving this map to a custom SavedData.
 */
public class ColdWaterEffectManager {

    // Using a simple static map for demonstration. For persistence, this would be a SavedData.
    // Key: Player UUID, Value: Current cold water intensity (0.0 to 1.0)
    private static final Map<UUID, Float> coldWaterIntensities = new HashMap<>();

    // Max intensity value (1.0f means full effect)
    private static final float MAX_INTENSITY = 1.0f;
    // How much intensity increases per tick while in water (ADJUSTED FOR SLOWER FADE-IN)
    private static final float INTENSITY_INCREASE_RATE = 0.005f; // Increases by 0.5% per tick for slow fade-in
    // How much intensity decreases per tick while out of water (ADJUSTED FOR FASTER FADE-OUT)
    private static final float INTENSITY_DECREASE_RATE = 0.01f; // Increased from 0.0025f for faster fade-out

    /**
     * Gets the current cold water intensity for a player.
     * Returns 0.0 if the player is not tracked.
     */
    public static float getIntensity(Player player) {
        return coldWaterIntensities.getOrDefault(player.getUUID(), 0.0f);
    }

    /**
     * Updates the cold water intensity for a player.
     * If in water, intensity increases. If out of water, intensity decreases.
     * Clamps the intensity between 0.0 and MAX_INTENSITY.
     * Sends a synchronization packet to the client if the intensity changes significantly.
     */
    public static void updateIntensity(ServerLevel level, Player player, boolean inWater) {
        float currentIntensity = getIntensity(player);
        float newIntensity = currentIntensity;

        if (inWater) {
            newIntensity += INTENSITY_INCREASE_RATE;
        } else {
            newIntensity -= INTENSITY_DECREASE_RATE;
        }

        newIntensity = Math.max(0.0f, Math.min(MAX_INTENSITY, newIntensity));

        if (newIntensity != currentIntensity) {
            coldWaterIntensities.put(player.getUUID(), newIntensity);
            // Send packet to client only if intensity has changed
            // Ensure the player is a ServerPlayer before sending the packet
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SyncColdWaterStatePacket(newIntensity));
            }
        }
    }

    /**
     * Checks if the cold water effect for a player is at maximum intensity.
     */
    public static boolean isMaxIntensity(Player player) {
        return getIntensity(player) >= MAX_INTENSITY;
    }

    /**
     * Resets the cold water intensity for a player.
     */
    public static void resetIntensity(Player player) {
        if (coldWaterIntensities.containsKey(player.getUUID())) {
            coldWaterIntensities.remove(player.getUUID());
            // Send packet to reset client display
            // Ensure the player is a ServerPlayer before sending the packet
            if (player instanceof ServerPlayer serverPlayer) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SyncColdWaterStatePacket(0.0f));
            }
        }
    }
}
