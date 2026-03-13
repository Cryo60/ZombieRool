package me.cryo.zombierool.event;

import me.cryo.zombierool.WaveManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;

@Mod.EventBusSubscriber(modid = "zombierool")
public class SpawnerEventHandler {
    private static ResourceLocation lastLoadedDimension = null;

    private static boolean isEnglishClient(Player player) {
        return true; 
    }

    private static Component getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    @SubscribeEvent
    public static void onWorldUnload(LevelEvent.Unload event) {
        if (!event.getLevel().isClientSide()) {
            SpawnerRegistry.clearRegistry((ServerLevel) event.getLevel()); 
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.getEntity().level().isClientSide() && event.getEntity().level() instanceof ServerLevel serverLevel) {
            ResourceLocation dimensionId = serverLevel.dimension().location();
            if (serverLevel.getServer().getPlayerList().getPlayers().size() == 1 && WaveManager.isGameRunning()) {
                serverLevel.getServer().getPlayerList().getPlayers().forEach(p -> {
                    p.sendSystemMessage(getTranslatedComponent(p, "§cFin de la partie : Le dernier joueur a quitté le serveur.", "§cGame Over: The last player left the server."));
                });
                WaveManager.endGame(serverLevel, getTranslatedComponent(null, "§cFin de la partie : Le dernier joueur a quitté le serveur.", "§cGame Over: The last player left the server."));
            }
            if (lastLoadedDimension != null && !lastLoadedDimension.equals(dimensionId)) {
                SpawnerRegistry.clearRegistry(serverLevel); 
                lastLoadedDimension = null; 
            }
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (event.getServer().isSingleplayer() || lastLoadedDimension == null) {
            lastLoadedDimension = event.getServer().overworld().dimension().location();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        SpawnerRegistry.clearRegistry(event.getServer().overworld());
        lastLoadedDimension = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) { 
            ServerLevel overworld = event.getServer().overworld();
            if (overworld == null) return;
            if (WaveManager.isGameRunning() && overworld.getServer().getPlayerList().getPlayers().isEmpty()) {
                overworld.getServer().getPlayerList().getPlayers().forEach(p -> {
                    p.sendSystemMessage(getTranslatedComponent(p, "§4GAME OVER ! Plus de joueurs sur le serveur.", "§4GAME OVER! No more players on the server."));
                });
                WaveManager.endGame(overworld, getTranslatedComponent(null, "§4GAME OVER ! Plus de joueurs sur le serveur.", "§4GAME OVER! No more players on the server."));
            }
        }
    }
}