package net.mcreator.zombierool;

import net.mcreator.zombierool.bonuses.BonusManager;
import net.mcreator.zombierool.entity.ZombieEntity;
import net.mcreator.zombierool.entity.HellhoundEntity;
import net.mcreator.zombierool.entity.CrawlerEntity;
import net.mcreator.zombierool.init.ZombieroolModEntities;
import net.mcreator.zombierool.init.ZombieroolModItems;
import net.mcreator.zombierool.player.PlayerDownManager;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.SpecialWavePacket;
import net.mcreator.zombierool.network.WaveUpdatePacket;
import net.minecraftforge.network.PacketDistributor;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import net.mcreator.zombierool.network.packet.PlayGlobalSoundPacket;
import net.mcreator.zombierool.network.packet.StartGameAnimationPacket;
import net.mcreator.zombierool.network.packet.WaveChangeAnimationPacket;

import net.mcreator.zombierool.AmmoCrateManager;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom; // OPTIMISATION: ThreadLocalRandom to avoid Random allocations
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.Minecraft; // Import Minecraft client for language check
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Future;
import java.util.function.Supplier; // Ajouté pour le Supplier dans execute
import net.minecraftforge.fml.loading.FMLLoader; // Ajouté pour FMLLoader
import net.minecraftforge.api.distmarker.Dist; // Ajouté pour Dist

/**
 * Gère les vagues de zombies et les vagues spéciales (hellhounds).
 */
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaveManager {

    // ==========================================
    // ÉTAT DU JEU
    // ==========================================
    private static int currentWave = 0;
    private static boolean gameRunning = false;
    private static boolean isSpecialWave = false;
    private static volatile boolean isPausedByPlayer = false;

    // OPTIMISATION: Utiliser des ensembles concurrentiels natifs
    private static final Set<UUID> activeMobs = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> processedDeaths = ConcurrentHashMap.newKeySet();

    // OPTIMISATION: "canal" en int (évite Integer.parseInt à répétition). API publique conservée.
    private static int currentCanal = 0;

    // Client-side cache
    private static int clientCurrentWave = 0;
    public static final Map<UUID, BlockPos> PLAYER_RESPAWN_POINTS = new ConcurrentHashMap<>();

    // ==========================================
    // CONSTANTES & PARAMÈTRES
    // ==========================================
    private static final int BASE_ZOMBIES = 6;

    // Sons principaux
    private static final ResourceLocation START_SOUND = new ResourceLocation("zombierool", "start_zombie");
    private static final ResourceLocation NEXT_WAVE_SOUND = new ResourceLocation("zombierool", "next_wave_zombie");
    private static final ResourceLocation SPECIAL_WAVE_SOUND = new ResourceLocation("zombierool", "fetch_me_their_souls");

    // OPTIMISATION: Précharger les sons de fond pour éviter la création de ResourceLocation en boucle
    private static final List<ResourceLocation> BG_SPRINT_SOUNDS;
    static {
        List<ResourceLocation> tmp = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            tmp.add(new ResourceLocation("zombierool", "sprint_bg" + i));
        }
        BG_SPRINT_SOUNDS = Collections.unmodifiableList(tmp);
    }

    // Délais
    private static final long NORMAL_WAVE_DELAY_MS = 12_000; // 12s
    private static final long AFTER_SOUND_DELAY_MS = 2_000; // +2s

    // Scaling PV
    private static final float ZOMBIE_BASE_HEALTH = 4f;
    private static final float ZOMBIE_MAX_HEALTH_CAP = 1050f;
    private static final float HELLHOUND_BASE_HEALTH = 8f;
    private static final float HELLHOUND_MAX_HEALTH_CAP = 250f;
    private static final float CRAWLER_BASE_HEALTH = 5f;
    private static final float CRAWLER_MAX_HEALTH_CAP = 800f;

    // Scaling vitesse
    private static final int WAVE_FOR_SPEED_SCALING = 5;
    private static final int MAX_WAVE_FOR_SPEED_SCALING = 12;
    private static final double MIN_SPEED_CHANCE = 1.0 / 8.0;
    private static final double MAX_SPEED_CHANCE = 1.0;
    private static final double ZOMBIE_SPEED_VALUE = 0.25D; // sprinters normaux

    // Super sprinters
    private static final int SUPER_SPRINTER_ACTIVATION_WAVE = 6;
    private static final double SUPER_SPRINTER_CHANCE = 1.0 / 30.0;
    private static final double SUPER_SPRINTER_SPEED_VALUE = 0.35D;
    private static final double SUPER_SPRINTER_DAMAGE_MULTIPLIER = 2.0;

    // Limites & compteurs
    private static final AtomicInteger specialWaveTotal = new AtomicInteger(0);
    private static final AtomicInteger specialWaveKilled = new AtomicInteger(0);
    private static final AtomicInteger specialSpawnedCount = new AtomicInteger(0);
    private static final AtomicInteger hellhoundsCurrentlyActive = new AtomicInteger(0);
    private static final int MAX_ACTIVE_HELLHOUNDS_PER_PLAYER = 6;
    private static final int MAX_ACTIVE_MOBS_PER_PLAYER_NORMAL_WAVE = 50;

    private static int zombiesToKill = 0;
    private static int zombiesKilledSinceLastBonus = 0;

    // ==========================================
    // THREADING / SCHEDULER
    // ==========================================
    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);
    private static Future<?> bgSoundTask;
    private static Future<?> hellhoundSpawnTask;
    private static Future<?> nextWaveSchedulerTask;
    private static Future<?> firstWaveDelayTask;
    private static Future<?> regularWaveSpawnerTask;

    // ==========================================
    // LOCALISATION (client)
    // ==========================================
    private static boolean isEnglishClient() {
        // Only check on client-side, otherwise assume French (or default)
        if (Minecraft.getInstance() == null || Minecraft.getInstance().level == null || !Minecraft.getInstance().level.isClientSide) {
            return false;
        }
        return Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    private static String getTranslatedMessage(String frenchMessage, String englishMessage) {
        return isEnglishClient() ? englishMessage : frenchMessage;
    }

    // ==========================================
    // GETTERS/SETTERS PUBLICS
    // ==========================================
    public static boolean isGameRunning() { return gameRunning; }
    public static boolean isSpecialWave() { return isSpecialWave; }
    public static boolean isPausedByPlayer() { return isPausedByPlayer; }

    public static int getCurrentWave() {
        if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) {
            return clientCurrentWave;
        }
        return currentWave;
    }

    public static void setClientWave(int wave) { clientCurrentWave = wave; }

    public static void setCurrentCanal(int canal) { // API conservée
        // OPTIMISATION: Éviter String -> int
        currentCanal = canal;
    }

    // ==========================================
    // HELPERS OPTIMISÉS
    // ==========================================

    // OPTIMISATION: Attente respectant pause/arrêt du jeu
    private static boolean waitWithPauseCheck(long ms) {
        long remaining = ms;
        try {
            while (remaining > 0) {
                if (isPausedByPlayer) {
                    Thread.sleep(100);
                } else {
                    long step = Math.min(remaining, 100);
                    Thread.sleep(step);
                    remaining -= step;
                }
                if (!gameRunning) return false;
            }
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    // OPTIMISATION: Centraliser l'animation + son de changement de vague
    private static void triggerWaveChange(ServerLevel level, int prevWave, int nextWave) {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new WaveChangeAnimationPacket(prevWave, nextWave));
        playGlobalSound(level, NEXT_WAVE_SOUND);
    }

    // OPTIMISATION: Planification standardisée de la prochaine vague avec fade in/out déjà gérés à l'appelant
    private static void scheduleStandardNextWave(ServerLevel level, boolean nextIsSpecial) {
        scheduler.schedule(() -> scheduleNextWave(level, NORMAL_WAVE_DELAY_MS - 2000, nextIsSpecial), 2000, TimeUnit.MILLISECONDS);
    }

    private static void startBgSoundLoop(ServerLevel level) {
        if (bgSoundTask != null && !bgSoundTask.isDone()) {
            return;
        }

        bgSoundTask = scheduler.scheduleWithFixedDelay(() -> {
            int lastIdx = -1;
            while (gameRunning && currentWave >= 5 && !isSpecialWave) {
                if (isPausedByPlayer) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    continue;
                }

                if (activeMobs.size() > 3) {
                    // OPTIMISATION: ThreadLocalRandom + pool de sons préchargé
                    int idx;
                    do {
                        idx = ThreadLocalRandom.current().nextInt(0, BG_SPRINT_SOUNDS.size());
                    } while (idx == lastIdx);
                    lastIdx = idx;
                    playGlobalSound(level, BG_SPRINT_SOUNDS.get(idx));

                    if (ThreadLocalRandom.current().nextDouble() < 0.20) {
                        try {
                            Thread.sleep(ThreadLocalRandom.current().nextInt(200, 800));
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        int idx2;
                        do {
                            idx2 = ThreadLocalRandom.current().nextInt(0, 4); // 0..3 ~ sons 1..4
                        } while (idx2 == lastIdx);
                        lastIdx = idx2;
                        playGlobalSound(level, BG_SPRINT_SOUNDS.get(idx2));
                    }

                    try {
                        long wait = 4_000 + ThreadLocalRandom.current().nextInt(0, 2001);
                        Thread.sleep(wait);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }

                } else {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }, 0, 500, TimeUnit.MILLISECONDS);
    }

    private static void stopBgSoundLoop() {
        if (bgSoundTask != null) {
            bgSoundTask.cancel(true);
            bgSoundTask = null;
        }
    }

    private static void stopHellhoundSpawnThread() {
        if (hellhoundSpawnTask != null) {
            hellhoundSpawnTask.cancel(true);
            hellhoundSpawnTask = null;
        }
    }

    private static void interruptAllWaveThreads() {
        if (firstWaveDelayTask != null) { firstWaveDelayTask.cancel(true); firstWaveDelayTask = null; }
        if (nextWaveSchedulerTask != null) { nextWaveSchedulerTask.cancel(true); nextWaveSchedulerTask = null; }
        if (regularWaveSpawnerTask != null) { regularWaveSpawnerTask.cancel(true); regularWaveSpawnerTask = null; }
        stopHellhoundSpawnThread();
        stopBgSoundLoop();
    }

    public static void setGamePaused(boolean paused) {
        if (isPausedByPlayer == paused) return;

        isPausedByPlayer = paused;
        // NEW: Send a system message to all players to update client-side pause state
        if (FMLLoader.getDist() == Dist.CLIENT && Minecraft.getInstance() != null && Minecraft.getInstance().getSingleplayerServer() != null) {
            for (ServerPlayer p : Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal("ZOMBIEROOL_GAME_PAUSED:" + paused));
            }
        } else if (FMLLoader.getDist() == Dist.DEDICATED_SERVER) {
            System.out.println("[WaveManager] Tentative de pause/reprise sur un serveur dédié. La pause client ne sera pas synchronisée directement.");
        }
    }

    // ===========================
    // DÉBUT ET PLANIFICATION
    // ===========================
    public static void startGame(ServerLevel level) {
        processedDeaths.clear();
        if (gameRunning) { return; }
        gameRunning = true;
        currentWave = 0; // Commencer à la vague 0
        activeMobs.clear();
        zombiesKilledSinceLastBonus = 0;

        // Animation et son au début
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new StartGameAnimationPacket(1));
        playGlobalSound(level, START_SOUND);

        // OPTIMISATION: usage de waitWithPauseCheck
        firstWaveDelayTask = scheduler.schedule(() -> {
            if (!waitWithPauseCheck(NORMAL_WAVE_DELAY_MS)) return;
            if (!gameRunning) return;
            level.getServer().executeBlocking(() -> spawnNextWave(level));
        }, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Force le jeu à une vague spécifique.
     */
    public static void forceSetWave(ServerLevel level, int targetWave) {
        if (!gameRunning) {
            currentWave = targetWave - 1;
            sendWaveUpdateToClients(level);
            broadcast(level, getTranslatedMessage(
                "§bLa vague a été définie sur " + targetWave + ". Lancez /zombierool start pour commencer la partie.",
                "§bWave set to " + targetWave + ". Use /zombierool start to begin the game."
            ));
            return;
        }

        broadcast(level, getTranslatedMessage(
            "§eForçage de la vague à " + targetWave + "...",
            "§eForcing wave to " + targetWave + "..."
        ));
        interruptAllWaveThreads();
        clearAllActiveMobs(level);

        int prevWave = currentWave;
        currentWave = targetWave - 1;

        zombiesToKill = 0;
        specialWaveKilled.set(0);
        specialWaveTotal.set(0);
        specialSpawnedCount.set(0);
        hellhoundsCurrentlyActive.set(0);
        zombiesKilledSinceLastBonus = 0;

        respawnAllSpectatorPlayers(level);

        scheduler.schedule(() -> {
            if (!gameRunning) return;
            level.getServer().executeBlocking(() -> {
                boolean nextIsSpecial = (targetWave) % 6 == 0 && targetWave != 0;
                if (nextIsSpecial && SpawnerRegistry.hasActiveSpawnerOfType(level, SpawnerDogBlockEntity.class)) {
                    isSpecialWave = true;
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(true));
                    playGlobalSound(level, SPECIAL_WAVE_SOUND);
                    scheduler.schedule(() -> {
                        if (!waitWithPauseCheck(AFTER_SOUND_DELAY_MS)) return;
                        if (!gameRunning) return;
                        level.getServer().executeBlocking(() -> spawnSpecialWave(level));
                    }, 0, TimeUnit.MILLISECONDS);
                } else {
                    isSpecialWave = false;
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(false));
                    triggerWaveChange(level, prevWave, targetWave);
                    spawnNextWave(level);
                }
            });
        }, 500, TimeUnit.MILLISECONDS);
    }

    private static void scheduleNextWave(ServerLevel level, long delayMs, boolean special) {
        if (nextWaveSchedulerTask != null) {
            nextWaveSchedulerTask.cancel(true);
        }

        nextWaveSchedulerTask = scheduler.schedule(() -> {
            if (!waitWithPauseCheck(delayMs)) return;
            if (!gameRunning) return;

            // Vérifie les spawners de hellhounds pour la vague spéciale
            if (special && !SpawnerRegistry.hasActiveSpawnerOfType(level, SpawnerDogBlockEntity.class)) {
                isSpecialWave = false;
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(false));
                level.getServer().executeBlocking(() -> spawnNextWave(level));
                sendWaveUpdateToClients(level);
                return;
            }

            level.getServer().executeBlocking(() -> {
                if (special) {
                    stopBgSoundLoop();
                    isSpecialWave = true;
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(true));

                    playGlobalSound(level, SPECIAL_WAVE_SOUND);
                    scheduler.schedule(() -> {
                        if (!waitWithPauseCheck(AFTER_SOUND_DELAY_MS)) return;
                        if (!gameRunning) return;
                        level.getServer().executeBlocking(() -> spawnSpecialWave(level));
                    }, 0, TimeUnit.MILLISECONDS);
                } else {
                    isSpecialWave = false;
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(false));
                    spawnNextWave(level);
                }
            });
        }, 0, TimeUnit.MILLISECONDS);
    }

    private static void respawnAllSpectatorPlayers(ServerLevel level) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                p.setGameMode(GameType.SURVIVAL);
                p.setHealth(p.getMaxHealth());
                p.removeAllEffects();

                BlockPos respawnPos = PLAYER_RESPAWN_POINTS.get(p.getUUID());
                if (respawnPos != null) {
                    p.teleportTo(level, respawnPos.getX() + 0.5, respawnPos.getY() + 1.0, respawnPos.getZ() + 0.5, p.getYRot(), p.getXRot());
                    p.sendSystemMessage(Component.literal(getTranslatedMessage(
                        "§aVous avez été ramené au combat pour la manche " + currentWave + " !",
                        "§aYou have been brought back to the fight for wave " + currentWave + "!"
                    )));
                    PointManager.modifyScore(p, 500);
                    ItemStack pistol = new ItemStack(ZombieroolModItems.M_1911_WEAPON.get());
                    if (!p.getInventory().add(pistol)) {
                        p.drop(pistol, false);
                    }
                } else {
                    p.sendSystemMessage(Component.literal(getTranslatedMessage(
                        "§cErreur: Impossible de trouver votre point de réapparition.",
                        "§cError: Unable to find your respawn point."
                    )));
                    System.err.println("[WaveManager] Joueur " + p.getName().getString() + " (UUID: " + p.getUUID() + ") n'a pas de point de respawn défini.");
                }
            }
        }
    }

    private static void spawnNextWave(ServerLevel level) {
        currentWave++;
        sendWaveUpdateToClients(level);
        broadcast(level, getTranslatedMessage(
            "§aDébut de la manche " + currentWave + " !",
            "§aWave " + currentWave + " begins!"
        ));

        respawnAllSpectatorPlayers(level);

        spawnRegularWave(level);
    }

    // =====================
    // Analyse du monde
    // =====================
    private static Optional<BlockPos> getRandomSpawnerPosition(ServerLevel level, Class<? extends BlockEntity> type) {
        int canal = currentCanal; // OPTIMISATION: pas de parse
        List<BlockEntity> candidats = SpawnerRegistry.getAllSpawnersByCanal(level, canal).stream()
                .filter(be -> {
                    int c = 0;
                    if (be instanceof SpawnerZombieBlockEntity z) c = z.getCanal();
                    else if (be instanceof SpawnerCrawlerBlockEntity c2) c = c2.getCanal();
                    else if (be instanceof SpawnerDogBlockEntity d) c = d.getCanal();
                    return c == canal;
                })
                .filter(type::isInstance)
                .collect(Collectors.toList());
        if (candidats.isEmpty()) return Optional.empty();
        BlockEntity choisi = candidats.get(ThreadLocalRandom.current().nextInt(candidats.size()));
        return Optional.of(choisi.getBlockPos());
    }

    // =================
    // Apparition des zombies
    // =================
    private static void spawnRegularWave(ServerLevel level) {
        int count = BASE_ZOMBIES + (int) (currentWave * 3.5);
        zombiesToKill = count;

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) { return; }

        int numPlayers = players.size();
        final int maxMobsAllowed = MAX_ACTIVE_MOBS_PER_PLAYER_NORMAL_WAVE * numPlayers;

        Random rand = ThreadLocalRandom.current();

        int minC = Math.max(1, (int) (count * 0.15));
        int maxC = Math.max(1, (int) (count * 0.40));
        int numCrawlers = rand.nextInt(maxC - minC + 1) + minC;
        boolean hasCrawlerSpawners = SpawnerRegistry.hasActiveSpawnerOfType(level, SpawnerCrawlerBlockEntity.class);
        if (!hasCrawlerSpawners) {
            numCrawlers = 0;
        }

        List<Boolean> order = new ArrayList<>(count);
        for (int j = 0; j < numCrawlers; j++) order.add(true);
        for (int j = 0; j < count - numCrawlers; j++) order.add(false);
        Collections.shuffle(order, rand);

        double rawDelay = 4000.0 * Math.pow(0.95, currentWave - 1);
        long delayMs = Math.max((long) rawDelay, 100L);

        if (regularWaveSpawnerTask != null) {
            regularWaveSpawnerTask.cancel(true);
        }

        AtomicInteger mobsToSpawnInThisWave = new AtomicInteger(count);

        regularWaveSpawnerTask = scheduler.scheduleAtFixedRate(() -> {
            if (!gameRunning) { regularWaveSpawnerTask.cancel(false); return; }
            if (isPausedByPlayer) { return; }
            if (mobsToSpawnInThisWave.get() <= 0) { regularWaveSpawnerTask.cancel(false); return; }
            if (activeMobs.size() >= maxMobsAllowed) { return; }

            final int idx = order.size() - 1;
            if (idx < 0) { return; }

            final boolean wantedCrawler = order.remove(idx);
            mobsToSpawnInThisWave.decrementAndGet();

            final Class<? extends BlockEntity> spawnType = (wantedCrawler && hasCrawlerSpawners)
                    ? SpawnerCrawlerBlockEntity.class
                    : SpawnerZombieBlockEntity.class;

            level.getServer().executeBlocking(() -> {
                Optional<BlockPos> optPos = getRandomSpawnerPosition(level, spawnType);
                if (optPos.isEmpty()) {
                    System.err.println("[WaveManager] Aucun spawner actif pour " + spawnType.getSimpleName());
                    return;
                }

                BlockPos pos = optPos.get();
                Vec3 v = Vec3.atBottomCenterOf(pos);

                Mob mob;
                if (spawnType == SpawnerCrawlerBlockEntity.class) {
                    CrawlerEntity c = new CrawlerEntity(ZombieroolModEntities.CRAWLER.get(), level);
                    applyHealthScaling(c, currentWave, level);
                    mob = c;
                } else {
                    ZombieEntity z = new ZombieEntity(ZombieroolModEntities.ZOMBIE.get(), level);
                    applyHealthScaling(z, currentWave, level);
                    applySpeedScaling(z, currentWave, rand, level);
                    mob = z;
                }

                mob.moveTo(v.x, v.y, v.z, rand.nextFloat() * 360F, 0);
                level.addFreshEntity(mob);
                activeMobs.add(mob.getUUID());
            });
        }, 0, delayMs, TimeUnit.MILLISECONDS);

        if (currentWave >= 5 && !isSpecialWave) {
            startBgSoundLoop(level);
        }
    }

    // =====================
    // Apparition des vagues spéciales (HELLHOUNDS)
    // =====================
    private static void spawnSpecialWave(ServerLevel level) {
        currentWave++;
        sendWaveUpdateToClients(level);

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            endGame(level, Component.literal(getTranslatedMessage(
                "§cFin de la partie : Plus de joueurs sur le serveur.",
                "§cGame Over: No more players on the server."
            )));
            return;
        }

        broadcast(level, getTranslatedMessage(
            "§cUne vague spéciale approche !",
            "§cSpecial wave approaching!"
        ));

        respawnAllSpectatorPlayers(level);

        int numPlayers = players.size();
        specialWaveTotal.set((4 + 2 * numPlayers) * 2);
        specialWaveKilled.set(0);
        hellhoundsCurrentlyActive.set(0);
        specialSpawnedCount.set(0);

        isSpecialWave = true;
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(true));

        if (hellhoundSpawnTask != null) {
            hellhoundSpawnTask.cancel(true);
        }

        hellhoundSpawnTask = scheduler.scheduleAtFixedRate(() -> {
            if (!gameRunning || specialSpawnedCount.get() >= specialWaveTotal.get()) { hellhoundSpawnTask.cancel(false); return; }
            if (isPausedByPlayer) { return; }
            int currentPlayers = level.getServer().getPlayerList().getPlayers().size();
            if (hellhoundsCurrentlyActive.get() < MAX_ACTIVE_HELLHOUNDS_PER_PLAYER * currentPlayers) {
                level.getServer().executeBlocking(() -> spawnOneHellhound(level));
            }
        }, 500, 2000, TimeUnit.MILLISECONDS);
    }

    private static void spawnOneHellhound(ServerLevel level) {
        Optional<BlockPos> opt = getRandomSpawnerPosition(level, SpawnerDogBlockEntity.class);
        if (opt.isEmpty()) {
            System.err.println("[WaveManager] Aucun spawner actif pour HellhoundEntity.");
            return;
        }

        Vec3 v = Vec3.atBottomCenterOf(opt.get());
        HellhoundEntity h = ZombieroolModEntities.HELLHOUND.get().create(level);
        if (h == null) { return; }
        applyHealthScaling(h, currentWave, level);
        h.moveTo(v.x, v.y, v.z, level.getRandom().nextFloat() * 360f, 0);
        level.addFreshEntity(h);

        activeMobs.add(h.getUUID());
        specialSpawnedCount.incrementAndGet();
        hellhoundsCurrentlyActive.incrementAndGet();
    }

    // ============
    // Utilitaires de mise à l'échelle
    // ============

    private static void applyHealthScaling(Mob mob, int wave, ServerLevel level) {
        float baseHealth;
        float maxCap;

        if (mob instanceof ZombieEntity) {
            baseHealth = ZOMBIE_BASE_HEALTH;
            maxCap = ZOMBIE_MAX_HEALTH_CAP;
            if (wave < 2) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth);
                mob.setHealth(baseHealth);
                return;
            }
        } else if (mob instanceof HellhoundEntity) {
            if (wave < 6) { return; }
            baseHealth = HELLHOUND_BASE_HEALTH;
            maxCap = HELLHOUND_MAX_HEALTH_CAP;
        } else if (mob instanceof CrawlerEntity) {
            baseHealth = CRAWLER_BASE_HEALTH;
            maxCap = CRAWLER_MAX_HEALTH_CAP;
            if (wave < 2) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth);
                mob.setHealth(baseHealth);
                return;
            }
        } else {
            baseHealth = 20f;
            maxCap = 40f;
        }

        float newMaxHealth = Math.min(baseHealth + wave * 2.0f, maxCap);
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
        mob.setHealth(newMaxHealth);
    }

    private static void applySpeedScaling(ZombieEntity zombie, int wave, Random rand, ServerLevel level) {
        WorldConfig worldConfig = WorldConfig.get(level);
        boolean isSuperSprinter = false;

        if (worldConfig.areSuperSprintersEnabled() && wave >= SUPER_SPRINTER_ACTIVATION_WAVE) {
            if (rand.nextDouble() < SUPER_SPRINTER_CHANCE) {
                zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(SUPER_SPRINTER_SPEED_VALUE);
                isSuperSprinter = true;
                zombie.getAttribute(Attributes.ATTACK_DAMAGE)
                      .removeModifier(UUID.fromString("9381c8b3-3a56-42d4-a1f9-0c6a51d4e0e5"));

                zombie.getAttribute(Attributes.ATTACK_DAMAGE)
                        .addTransientModifier(new AttributeModifier(UUID.fromString("9381c8b3-3a56-42d4-a1f9-0c6a51d4e0e5"),
                                "Super Sprinter Damage", (zombie.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * (SUPER_SPRINTER_DAMAGE_MULTIPLIER - 1.0)), AttributeModifier.Operation.ADDITION));

                System.out.println("[WaveManager] Spawned Super Sprinter Zombie at wave " + wave);
            }
        }
        zombie.setSuperSprinter(isSuperSprinter);

        if (!isSuperSprinter && wave >= WAVE_FOR_SPEED_SCALING) {
            int minWave = WAVE_FOR_SPEED_SCALING;
            int maxWave = MAX_WAVE_FOR_SPEED_SCALING;

            double minChance = MIN_SPEED_CHANCE;
            double maxChance = MAX_SPEED_CHANCE;
            double progress = (double) (wave - minWave) / (maxWave - minWave);
            progress = Math.min(1.0, Math.max(0.0, progress));
            double chance = minChance + (maxChance - minChance) * progress;
            if (rand.nextDouble() <= chance) {
                zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(ZOMBIE_SPEED_VALUE);
            }
        }
    }

    private static void clearAllActiveMobs(ServerLevel level) {
        // OPTIMISATION: plus besoin de synchronized externe grâce à newKeySet
        Set<UUID> mobsToKill = new HashSet<>(activeMobs);
        for (UUID mobId : mobsToKill) {
            Mob mob = (Mob) level.getEntity(mobId);
            if (mob != null) {
                mob.kill();
            }
        }
        activeMobs.clear();
        processedDeaths.clear();
    }

    // =====================
    // Gestion des morts d'entités
    // =====================
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!gameRunning) { return; }
        if (isPausedByPlayer) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = (ServerLevel) player.level();
            boolean anyPlayerInGame = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .anyMatch(p -> !PlayerDownManager.isPlayerDown(p.getUUID()));
            if (!anyPlayerInGame) {
                endGame(level, Component.literal(getTranslatedMessage(
                    "§cFin de la partie : Tous les joueurs sont tombés.",
                    "§cGame Over: All players are down."
                )));
            }
        }

        if (event.getEntity() instanceof ZombieEntity || event.getEntity() instanceof CrawlerEntity || event.getEntity() instanceof HellhoundEntity) {
            Mob mob = (Mob) event.getEntity();
            ServerLevel level = (ServerLevel) mob.level();
            onMobDeath(mob, level);
        }
    }

    public static void onMobDeath(Mob mob, ServerLevel level) {
        if (isPausedByPlayer) return;
        handleMobDeath(mob, level);
        if (!(mob instanceof HellhoundEntity)) {
            zombiesKilledSinceLastBonus++;
        }
    }

    private static void handleMobDeath(Mob mob, ServerLevel level) {
        if (!gameRunning) { return; }
        if (isPausedByPlayer) return;

        UUID id = mob.getUUID();
        if (!activeMobs.contains(id) || !processedDeaths.add(id)) { return; }
        activeMobs.remove(id);

        if (mob instanceof HellhoundEntity) {
            hellhoundsCurrentlyActive.decrementAndGet();
            int killed = specialWaveKilled.incrementAndGet();

            if (killed >= specialWaveTotal.get() && activeMobs.stream().allMatch(uuid -> !(level.getEntity(uuid) instanceof HellhoundEntity))) {
                stopHellhoundSpawnThread();

                if (!BonusManager.bonusSpawnDisabledByNuke) {
                    BonusManager.spawnBonus(BonusManager.BonusType.MAX_AMMO, level, mob.position());
                }

                int prevWave = currentWave;
                int nextWave = currentWave + 1;
                triggerWaveChange(level, prevWave, nextWave);

                isSpecialWave = false;
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(false));
                boolean nextIsSpecial = (currentWave + 1) % 6 == 0;

                // NEW: fade out/in messages (conservés mais commentés dans l'original)
                scheduleStandardNextWave(level, nextIsSpecial);
            }
            return;
        }

        zombiesToKill--;
        if (zombiesToKill <= 0 && activeMobs.stream().allMatch(uuid -> !(level.getEntity(uuid) instanceof ZombieEntity || level.getEntity(uuid) instanceof CrawlerEntity))) {
            int prevWave = currentWave;
            int nextWave = currentWave + 1;
            triggerWaveChange(level, prevWave, nextWave);

            isSpecialWave = false;
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SpecialWavePacket(false));
            boolean nextIsSpecial = (currentWave + 1) % 6 == 0;

            // NEW: fade out/in messages (conservés mais commentés dans l'original)
            scheduleStandardNextWave(level, nextIsSpecial);
        }
    }

    public static synchronized void endGame(ServerLevel level, Component message) {
	    if (!gameRunning) { return; }
	    interruptAllWaveThreads();
	
	    gameRunning = false;
	    isSpecialWave = false;
	    activeMobs.clear();
	    processedDeaths.clear();
	    currentWave = 0;
	    zombiesToKill = 0;
	    specialWaveKilled.set(0);
	    specialWaveTotal.set(0);
	    specialSpawnedCount.set(0);
	    hellhoundsCurrentlyActive.set(0);
	    zombiesKilledSinceLastBonus = 0;
	
	    // Réinitialise l'AmmoCrate Manager
	    AmmoCrateManager ammoCrateManager = AmmoCrateManager.get(level);
	    ammoCrateManager.resetAllData();
	
	    sendWaveUpdateToClients(level);
	
	    level.getServer().getWorldData().setDifficulty(Difficulty.PEACEFUL);
	    for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
	        if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR || PlayerDownManager.isPlayerDown(p.getUUID())) {
	            p.setGameMode(GameType.SURVIVAL);
	            p.setHealth(p.getMaxHealth());
	            p.removeAllEffects();
	        }
	    }
	
	    broadcast(level, message);
	}

    public static int getZombiesKilledSinceLastBonus(ServerLevel level) { return zombiesKilledSinceLastBonus; }
    public static void resetZombiesKilledSinceLastBonus(ServerLevel level) { zombiesKilledSinceLastBonus = 0; }

    public static void sendWaveUpdateToClients(ServerLevel level) {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new WaveUpdatePacket(currentWave));
    }

    private static void playGlobalSound(ServerLevel level, ResourceLocation soundRes) {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayGlobalSoundPacket(soundRes));
    }

    // OPTIMISATION: surcharge pour éviter les reconstructions inutiles
    private static void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
    private static void broadcast(ServerLevel level, String msg) {
        broadcast(level, Component.literal(msg));
    }

    // ===========================================
    // Gestion de la pause du joueur
    // ===========================================

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (event.getServer().isSingleplayer()) {
            setGamePaused(true);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) { // Removed extra 'void' keyword
        if (event.getServer().isSingleplayer()) {
            setGamePaused(false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer && event.getEntity().getServer().isSingleplayer()) {
            setGamePaused(false);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer && event.getEntity().getServer().isSingleplayer()) {
            setGamePaused(true);
        }
    }
}