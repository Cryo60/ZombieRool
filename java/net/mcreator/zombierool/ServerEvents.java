// Dans net.mcreator.zombierool.server.ServerEvents
package net.mcreator.zombierool.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.network.chat.Component;

import net.mcreator.zombierool.WorldConfig; // Importez votre WorldConfig

// Added for the tick delay
import net.minecraftforge.event.TickEvent;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerEvents {

    // Queue to hold players for whom we need to update fog after a tick
    private static final Queue<ServerPlayer> playersToUpdateFog = new ConcurrentLinkedQueue<>();

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Add to queue to update fog on next tick
            playersToUpdateFog.add(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeGameMode(PlayerEvent.PlayerChangeGameModeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Add to queue to update fog on next tick
            playersToUpdateFog.add(player);
        }
    }

    // NEW: Event listener for server ticks to process the queue
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) { // Only process at the end of the tick
            while (!playersToUpdateFog.isEmpty()) {
                ServerPlayer player = playersToUpdateFog.poll(); // Get and remove player from queue
                if (player != null && player.isAlive()) { // Ensure player is still valid
                    updateFogForPlayer(player);
                }
            }
        }
    }


    private static void updateFogForPlayer(ServerPlayer player) {
        // Check if the player is still valid (not logged out in the same tick)
        if (player == null || player.server == null || !player.server.isDedicatedServer() && player.server.isSingleplayerOwner(player.getGameProfile())) {
            // In a single-player environment, the server is the client. The client-side code will handle it.
            // Or if it's a dedicated server, ensure it's a valid player.
            // This check might be too complex for a simple fog update, simpler to just rely on client side logic.
            // Let's simplify this check to avoid issues with player state during login/logout
            if (player == null || !player.isAlive()) return; // Simple check if player is valid
        }

        // Get the world config
        WorldConfig worldConfig = WorldConfig.get(player.serverLevel());

        String clientFogPreset;
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE || player.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
            // If player is in creative or spectator, force "none" preset
            clientFogPreset = "none";
        } else {
            // Otherwise, use the preset from world config
            clientFogPreset = worldConfig.getFogPreset();
        }

        // Send chat message to client to update fog preset
        player.sendSystemMessage(Component.literal("ZOMBIEROOL_FOG_PRESET:" + clientFogPreset));
    }
}