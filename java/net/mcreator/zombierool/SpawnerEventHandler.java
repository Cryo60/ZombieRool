package net.mcreator.zombierool;

import net.mcreator.zombierool.bonuses.BonusManager; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.entity.ZombieEntity; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.entity.HellhoundEntity; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.entity.CrawlerEntity; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.init.ZombieroolModEntities; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.init.ZombieroolModItems; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.player.PlayerDownManager; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.stream.Collectors;
import java.util.Optional;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.mcreator.zombierool.spawner.SpawnerRegistry;
import net.mcreator.zombierool.block.entity.SpawnerDogBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import net.mcreator.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.minecraft.world.Difficulty;
import net.minecraftforge.event.entity.living.LivingDeathEvent; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.network.NetworkHandler; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.network.SpecialWavePacket; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.mcreator.zombierool.network.WaveUpdatePacket; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.minecraftforge.network.PacketDistributor; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le

import java.util.concurrent.atomic.AtomicInteger; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.HashSet; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.List; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.Random; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.Set; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.ArrayList; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.Collections; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.Map; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import java.util.concurrent.ConcurrentHashMap; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le

import net.minecraft.world.level.GameType; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.minecraft.world.item.ItemStack; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le
import net.minecraft.client.Minecraft; // Assurez-vous que cet import est toujours nécessaire, sinon supprimez-le

// IMPORTS SPÉCIFIQUES À SPWNEREVENTHANDLER
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent; // Ajouté pour la gestion du démarrage du serveur
import net.minecraftforge.event.server.ServerStoppingEvent; // Ajouté pour la gestion de l'arrêt du serveur
import net.minecraftforge.event.TickEvent; // Ajouté pour la gestion du tick du serveur

import java.util.concurrent.Executors; // Ajouté pour les threads
import java.util.concurrent.ScheduledExecutorService; // Ajouté pour les threads
import java.util.concurrent.TimeUnit; // Ajouté pour les threads
import java.util.concurrent.Future; // Ajouté pour les threads


@Mod.EventBusSubscriber(modid = "zombierool")
public class SpawnerEventHandler {

    private static ResourceLocation lastLoadedDimension = null;

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient(Player player) {
        // For server-side, we can't directly access client language settings.
        // This is a placeholder. In a real scenario, you'd need client-server sync
        // to pass the player's language preference to the server.
        // For now, we'll assume English if a player context is available.
        return true; 
    }

    // Helper method for dynamic translation
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
            
            // Si c'est le dernier joueur à se déconnecter d'une dimension spécifique, et que le jeu est en cours.
            // On peut aussi vérifier si c'est le seul joueur restant qui quitte.
            if (serverLevel.getServer().getPlayerList().getPlayers().size() == 1 && WaveManager.isGameRunning()) {
                serverLevel.getServer().getPlayerList().getPlayers().forEach(p -> {
                    p.sendSystemMessage(getTranslatedComponent(p, "§cFin de la partie : Le dernier joueur a quitté le serveur.", "§cGame Over: The last player left the server."));
                });
                WaveManager.endGame(serverLevel, getTranslatedComponent(null, "§cFin de la partie : Le dernier joueur a quitté le serveur.", "§cGame Over: The last player left the server."));
            }
            
            // Logique pour la gestion de la dimension (si l'hôte quitte une dimension différente)
            // Cette partie dépend de votre logique de gestion de "l'hôte" et des dimensions.
            // Le "lastLoadedDimension" pourrait être plus pertinent pour un scénario de changement de monde.
            if (lastLoadedDimension != null && !lastLoadedDimension.equals(dimensionId)) {
                // Si l'hôte (le dernier joueur) quitte la dimension "principale" après avoir chargé une autre dimension
                // ou si le jeu est spécifiquement lié à cette dimension principale.
                // Ce message est plus spécifique si l'hôte est vraiment considéré comme le dernier joueur restant.
                // Sinon, la condition ci-dessus (taille de la liste des joueurs) est plus générique.
                // WaveManager.endGame(serverLevel, getTranslatedComponent(null, "§cFin de la partie : L'hôte a quitté le serveur.", "§cGame Over: The host left the server."));
                SpawnerRegistry.clearRegistry(serverLevel); // Assurez-vous que cette signature est correcte aussi
                lastLoadedDimension = null; // Réinitialiser après avoir géré le changement de dimension
            } else if (event.getEntity().level().isClientSide()) {
                // Client-side, clear client-side data
                WaveManager.setClientWave(0);
            }
        }
    }

    // Gestion du chargement/déchargement de la dimension pour le dernier joueur
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        // Au démarrage du serveur, si c'est une partie solo ou si c'est la première dimension chargée
        if (event.getServer().isSingleplayer() || lastLoadedDimension == null) {
            lastLoadedDimension = event.getServer().overworld().dimension().location();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        // Quand le serveur s'arrête, effacer les données de registre
        SpawnerRegistry.clearRegistry(event.getServer().overworld());
        lastLoadedDimension = null;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.getServer().getTickCount() % 20 == 0) { // Toutes les secondes
            ServerLevel overworld = event.getServer().overworld();
            if (overworld == null) return;

            // Vérifier si le jeu est en cours et s'il n'y a plus de joueurs sur le serveur
            if (WaveManager.isGameRunning() && overworld.getServer().getPlayerList().getPlayers().isEmpty()) {
                // Send message to all connected players (if any were still connected before they left)
                overworld.getServer().getPlayerList().getPlayers().forEach(p -> {
                    p.sendSystemMessage(getTranslatedComponent(p, "§4GAME OVER ! Plus de joueurs sur le serveur.", "§4GAME OVER! No more players on the server."));
                });
                WaveManager.endGame(overworld, getTranslatedComponent(null, "§4GAME OVER ! Plus de joueurs sur le serveur.", "§4GAME OVER! No more players on the server."));
            }
        }
    }
}
