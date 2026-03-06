package me.cryo.zombierool;
import me.cryo.zombierool.bonuses.BonusManager; 
import me.cryo.zombierool.entity.ZombieEntity; 
import me.cryo.zombierool.entity.HellhoundEntity; 
import me.cryo.zombierool.entity.CrawlerEntity; 
import me.cryo.zombierool.init.ZombieroolModEntities; 
import me.cryo.zombierool.init.ZombieroolModItems; 
import me.cryo.zombierool.player.PlayerDownManager; 
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
import me.cryo.zombierool.spawner.SpawnerRegistry;
import me.cryo.zombierool.block.entity.SpawnerDogBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerCrawlerBlockEntity;
import me.cryo.zombierool.block.entity.SpawnerZombieBlockEntity;
import net.minecraft.world.Difficulty;
import net.minecraftforge.event.entity.living.LivingDeathEvent; 
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import me.cryo.zombierool.network.NetworkHandler; 
import me.cryo.zombierool.network.SpecialWavePacket; 
import me.cryo.zombierool.network.WaveUpdatePacket; 
import net.minecraftforge.network.PacketDistributor; 
import java.util.concurrent.atomic.AtomicInteger; 
import java.util.HashSet; 
import java.util.List; 
import java.util.Random; 
import java.util.Set; 
import java.util.ArrayList; 
import java.util.Collections; 
import java.util.Map; 
import java.util.concurrent.ConcurrentHashMap; 
import net.minecraft.world.level.GameType; 
import net.minecraft.world.item.ItemStack; 
import net.minecraft.client.Minecraft; 
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent; 
import net.minecraftforge.event.server.ServerStoppingEvent; 
import net.minecraftforge.event.TickEvent; 
import java.util.concurrent.Executors; 
import java.util.concurrent.ScheduledExecutorService; 
import java.util.concurrent.TimeUnit; 
import java.util.concurrent.Future; 
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