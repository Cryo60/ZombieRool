// [main\java\me\cryo\zombierool\WaveManager.java]
package me.cryo.zombierool;

import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PickableManager;
import me.cryo.zombierool.player.PlayerDownManager;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.bonuses.BonusManager;
import me.cryo.zombierool.spawner.SpawnerRegistry;
import me.cryo.zombierool.block.system.UniversalSpawnerSystem;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CProgressChallengePacket;
import me.cryo.zombierool.network.packet.S2CWaveChangeAnimationPacket;
import me.cryo.zombierool.network.packet.S2CStartGameAnimationPacket;
import me.cryo.zombierool.network.packet.S2CPlayGlobalSoundPacket;
import me.cryo.zombierool.network.packet.S2CMatchRecapPacket;
import me.cryo.zombierool.network.S2CWaveUpdatePacket;
import me.cryo.zombierool.network.S2CSpecialWavePacket;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class WaveManager {

    public static final Set<Integer> UNLOCKED_CHANNELS = ConcurrentHashMap.newKeySet();
    public static final Set<Integer> LOCKED_CHANNELS = ConcurrentHashMap.newKeySet();
    public static final Set<String> UNLOCKED_ZONES = ConcurrentHashMap.newKeySet();

    private static int currentWave = 0;
    private static boolean gameRunning = false;
    private static boolean isSpecialWave = false;
    private static volatile boolean isPausedByPlayer = false;

    private static final Set<UUID> activeMobs = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> processedDeaths = ConcurrentHashMap.newKeySet();

    private static int clientCurrentWave = 0;
    private static boolean clientIsSpecialWave = false;
    private static boolean clientGameRunning = false;

    public static final Map<UUID, BlockPos> PLAYER_RESPAWN_POINTS = new ConcurrentHashMap<>();

    private static final ResourceLocation START_SOUND = new ResourceLocation("zombierool", "start_zombie");
    private static final ResourceLocation NEXT_WAVE_SOUND = new ResourceLocation("zombierool", "next_wave_zombie");
    private static final List<ResourceLocation> BG_SPRINT_SOUNDS;

    static {
        List<ResourceLocation> tmp = new ArrayList<>();
        for (int i = 1; i <= 10; i++) tmp.add(new ResourceLocation("zombierool", "sprint_bg" + i));
        BG_SPRINT_SOUNDS = Collections.unmodifiableList(tmp);
    }

    private static final double SUPER_SPRINTER_DAMAGE_MULTIPLIER = 2.0;

    private static final AtomicInteger specialWaveTotal = new AtomicInteger(0);
    private static final AtomicInteger specialWaveKilled = new AtomicInteger(0);
    private static final AtomicInteger specialSpawnedCount = new AtomicInteger(0);
    private static final AtomicInteger hellhoundsCurrentlyActive = new AtomicInteger(0);

    private static int zombiesToKill = 0;
    private static int zombiesKilledSinceLastBonus = 0;

    private enum WaveState { OFF, DELAY_FIRST, DELAY_NEXT, DELAY_SPECIAL_SOUND, DELAY_SPECIAL_SPAWN, SPAWNING_NORMAL, SPAWNING_SPECIAL, WAITING_CLEAR }
    private static WaveState currentState = WaveState.OFF;
    private static int stateTimer = 0;
    private static int spawnTimer = 0;
    private static int currentSpawnInterval = 0;

    private static int bgSoundTimer = 0;
    private static int bgSoundState = 0; 

    private static List<Boolean> currentWaveSpawnOrder = new ArrayList<>();
    private static int mobsToSpawnInThisWave = 0;

    public static String currentSessionMusic = "default";

    public static boolean isGameRunning() {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) return clientGameRunning;
        }
        return gameRunning;
    }

    public static void setClientGameRunning(boolean running) {
        clientGameRunning = running;
    }

    public static boolean isSpecialWave() { return isSpecialWave; }
    public static boolean isPausedByPlayer() { return isPausedByPlayer; }

    public static int getCurrentWave() {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            if (Minecraft.getInstance().level != null && Minecraft.getInstance().level.isClientSide) return clientCurrentWave;
        }
        return currentWave;
    }

    public static void setClientWave(int wave) { clientCurrentWave = wave; }
    public static boolean isClientSpecialWave() { return clientIsSpecialWave; }
    public static void setClientSpecialWave(boolean special) { clientIsSpecialWave = special; }

    public static void unlockChannel(int channel) {
        if (channel > 0) {
            UNLOCKED_CHANNELS.add(channel);
            LOCKED_CHANNELS.remove(channel);
        }
    }

    public static void lockChannel(int channel) {
        UNLOCKED_CHANNELS.remove(channel);
        LOCKED_CHANNELS.add(channel);
    }

    private static void triggerWaveChange(ServerLevel level, int prevWave, int nextWave) {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CWaveChangeAnimationPacket(prevWave, nextWave));
        playGlobalSound(level, NEXT_WAVE_SOUND);
    }

    public static void setGamePaused(boolean paused) {
        if (isPausedByPlayer == paused) return;
        isPausedByPlayer = paused;
        if (FMLLoader.getDist() == Dist.CLIENT && Minecraft.getInstance() != null && Minecraft.getInstance().getSingleplayerServer() != null) {
            for (ServerPlayer p : Minecraft.getInstance().getSingleplayerServer().getPlayerList().getPlayers()) {
                p.sendSystemMessage(Component.literal("ZOMBIEROOL_GAME_PAUSED:" + paused));
            }
        }
    }

    public static void startGame(ServerLevel level) {
        processedDeaths.clear();
        if (gameRunning) return;
        gameRunning = true;
        currentWave = 0;
        activeMobs.clear();
        zombiesKilledSinceLastBonus = 0;

        PlayerStatsManager.reset(level);
        PickableManager.initGame(level);
        PlayerDownManager.resetAll();
        me.cryo.zombierool.AmmoCrateManager.get(level).resetAllData();
        me.cryo.zombierool.handlers.ServerInteractionHandler.resetAll();

        net.minecraft.world.scores.Scoreboard scoreboard = level.getScoreboard();
        net.minecraft.world.scores.Objective obj = scoreboard.getObjective(me.cryo.zombierool.event.ServerEventHandler.OBJECTIVE_ID);
        if (obj != null) {
            scoreboard.removeObjective(obj);
        }
        obj = scoreboard.addObjective(
            me.cryo.zombierool.event.ServerEventHandler.OBJECTIVE_ID,
            net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
            net.minecraft.network.chat.Component.literal("Points"),
            net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER
        );

        WorldConfig worldConfig = WorldConfig.get(level);
        worldConfig.setMeteoriteFragmentsFound(0); 

        currentSessionMusic = worldConfig.getMusicPreset();
        if (currentSessionMusic == null || currentSessionMusic.isEmpty()) {
            currentSessionMusic = "default";
        }

        for (BlockPos pos : worldConfig.getMeteoritePositions()) {
            if (level.isLoaded(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof me.cryo.zombierool.block.system.MeteoriteEasterEgg.MeteoriteBlock) {
                    level.setBlock(pos, state.setValue(me.cryo.zombierool.block.system.MeteoriteEasterEgg.MeteoriteBlock.ACTIVE, true), 3);
                }
            }
        }

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();

        UNLOCKED_CHANNELS.clear();
        LOCKED_CHANNELS.clear();
        UNLOCKED_ZONES.clear();
        UNLOCKED_CHANNELS.add(0); 

        PLAYER_RESPAWN_POINTS.clear();

        List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> playerSpawners = new ArrayList<>();
        for (BlockPos pos : worldConfig.getPlayerSpawnerPositions()) {
            level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, true);
            if (level.getBlockEntity(pos) instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity ube && 
                ube.getMobType() == UniversalSpawnerSystem.SpawnerMobType.PLAYER) {
                playerSpawners.add(ube); 
            }
        }

        if (!playerSpawners.isEmpty()) {
            Map<String, List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity>> zones = new HashMap<>();
            for (UniversalSpawnerSystem.UniversalSpawnerBlockEntity spawner : playerSpawners) {
                String z = spawner.getZone();
                if (z == null || z.isBlank()) z = "DEFAULT";
                else z = z.trim().toUpperCase(java.util.Locale.ROOT);
                zones.computeIfAbsent(z, k -> new ArrayList<>()).add(spawner);
            }

            List<String> zoneKeys = new ArrayList<>(zones.keySet());
            Collections.shuffle(zoneKeys);

            int zoneIndex = 0;
            int charId = 1;
            for (ServerPlayer player : players) {
                player.getPersistentData().putInt("zr_character_id", charId);
                charId++;
                if (charId > 4) charId = 1;

                String currentZoneKey = zoneKeys.get(zoneIndex % zoneKeys.size());
                List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> currentZoneSpawners = zones.get(currentZoneKey);
                UniversalSpawnerSystem.UniversalSpawnerBlockEntity chosenSpawner = currentZoneSpawners.get(ThreadLocalRandom.current().nextInt(currentZoneSpawners.size()));

                BlockPos spawnPos = chosenSpawner.getBlockPos();
                PLAYER_RESPAWN_POINTS.put(player.getUUID(), spawnPos.immutable());
                player.teleportTo(spawnPos.getX() + 0.5, spawnPos.getY() + 1.0, spawnPos.getZ() + 0.5);

                player.getPersistentData().remove("zr_has_bowie_knife"); 
                player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                    cap.setPoints(500);
                    cap.resetStats();
                    cap.resetTotalPoints();
                    cap.resetPerkPurchases();
                    cap.setLethalType(worldConfig.getStartingLethal()); 
                    cap.setLethalCount(5); 
                    cap.sync(player);
                });

                UNLOCKED_ZONES.add(currentZoneKey);
                zoneIndex++;
            }
        } else {
            UNLOCKED_ZONES.add("DEFAULT");
            int charId = 1;
            for (ServerPlayer player : players) {
                player.getPersistentData().putInt("zr_character_id", charId);
                charId++;
                if (charId > 4) charId = 1;

                player.getPersistentData().remove("zr_has_bowie_knife"); 
                player.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                    cap.setPoints(500);
                    cap.resetStats();
                    cap.resetTotalPoints();
                    cap.resetPerkPurchases();
                    cap.setLethalType(worldConfig.getStartingLethal()); 
                    cap.setLethalCount(5); 
                    cap.sync(player);
                });
            }
        }

        PlayerStatsManager.syncAll(level);
        LuaScriptManager.callEvent("OnGameStart");

        Set<BlockPos> mysteryBoxPositions = worldConfig.getMysteryBoxPositions();
        if (!mysteryBoxPositions.isEmpty()) { 
            me.cryo.zombierool.MysteryBoxManager mysteryBoxManager = me.cryo.zombierool.MysteryBoxManager.get(level);
            mysteryBoxManager.setupInitialMysteryBox(level, 0); 
        }

        Set<BlockPos> wunderfizzPositions = worldConfig.getWunderfizzPositions();
        if (!wunderfizzPositions.isEmpty()) {
            List<BlockPos> wunderfizzList = new ArrayList<>();
            for(BlockPos p : wunderfizzPositions) {
                if (!worldConfig.isWunderfizzExcluded(p)) wunderfizzList.add(p);
            }
            if (wunderfizzList.isEmpty()) wunderfizzList.addAll(wunderfizzPositions);
            Collections.shuffle(wunderfizzList);
            BlockPos chosenWunderfizz = wunderfizzList.get(0);
            worldConfig.setActiveWunderfizzPosition(chosenWunderfizz, level);

            for (BlockPos wunderfizzPos : wunderfizzPositions) {
                BlockState wunderfizzState = level.getBlockState(wunderfizzPos);
                if (wunderfizzState.getBlock() instanceof me.cryo.zombierool.block.DerWunderfizzBlock) {
                    net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(wunderfizzPos);
                    if (be instanceof me.cryo.zombierool.block.entity.DerWunderfizzBlockEntity dbe) {
                        dbe.resetToIdleState();
                    }
                }
            }
            worldConfig.setDirty();
        }

        for (ServerPlayer player : players) {
            PlayerVoiceManager.playLevelStart(player, level);
        }

        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CStartGameAnimationPacket(1, currentSessionMusic));
        playGlobalSound(level, START_SOUND);

        currentState = WaveState.DELAY_FIRST;
        stateTimer = 240;
    }

    public static void forceSetWave(ServerLevel level, int targetWave) {
        if (!gameRunning) {
            currentWave = targetWave - 1;
            sendWaveUpdateToClients(level);
            broadcast(level, Component.translatable("message.zombierool.wave.set", targetWave).withStyle(ChatFormatting.AQUA));
            return;
        }

        WorldConfig config = WorldConfig.get(level);
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

        boolean nextIsSpecial = config.isSpecialWavesEnabled() && targetWave >= config.getSpecialWaveStart() && (targetWave - config.getSpecialWaveStart()) % config.getSpecialWaveInterval() == 0;
        if (nextIsSpecial && !SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND)) {
            nextIsSpecial = false;
        }

        if (nextIsSpecial) {
            isSpecialWave = true;
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(true));
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                PlayerVoiceManager.playSpecialStart(player, level);
            }
            playGlobalSound(level, new ResourceLocation("zombierool", "fetch_me_their_souls"));
            currentState = WaveState.DELAY_SPECIAL_SPAWN;
            stateTimer = 160; 
        } else {
            isSpecialWave = false;
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(false));
            triggerWaveChange(level, prevWave, targetWave);
            currentState = WaveState.DELAY_NEXT;
            stateTimer = 10;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !gameRunning || isPausedByPlayer) return;
        ServerLevel level = event.getServer().overworld();

        switch (currentState) {
            case DELAY_FIRST:
            case DELAY_NEXT:
                if (--stateTimer <= 0) spawnNextWave(level);
                break;
            case DELAY_SPECIAL_SOUND:
                if (--stateTimer <= 0) {
                    isSpecialWave = true;
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(true));
                    for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                        PlayerVoiceManager.playSpecialStart(player, level);
                    }
                    playGlobalSound(level, new ResourceLocation("zombierool", "fetch_me_their_souls"));
                    currentState = WaveState.DELAY_SPECIAL_SPAWN;
                    stateTimer = 160; 
                }
                break;
            case DELAY_SPECIAL_SPAWN:
                if (--stateTimer <= 0) spawnSpecialWave(level);
                break;
            case SPAWNING_NORMAL:
                if (--spawnTimer <= 0 && mobsToSpawnInThisWave > 0) {
                    int maxActive = WorldConfig.get(level).getMaxActiveMobsPerPlayer();
                    if (currentWave >= 5) {
                        String intensity = WorldConfig.get(level).getSpawnIntensity();
                        if ("flood".equals(intensity)) maxActive = (int)(maxActive * 1.5);
                        else if ("chaos".equals(intensity)) maxActive = (int)(maxActive * 2.0);
                    }
                    if (activeMobs.size() < maxActive * level.getServer().getPlayerList().getPlayers().size()) {
                        spawnOneRegularMob(level);
                        spawnTimer = currentSpawnInterval;
                    } else {
                        spawnTimer = 10;
                    }
                }
                if (mobsToSpawnInThisWave <= 0) currentState = WaveState.WAITING_CLEAR;
                handleBgSound(level);
                break;
            case SPAWNING_SPECIAL:
                if (--spawnTimer <= 0 && specialSpawnedCount.get() < specialWaveTotal.get()) {
                    if (hellhoundsCurrentlyActive.get() < 6 * level.getServer().getPlayerList().getPlayers().size()) {
                        spawnOneHellhound(level);
                        spawnTimer = 50;
                    } else {
                        spawnTimer = 10;
                    }
                }
                if (specialSpawnedCount.get() >= specialWaveTotal.get()) currentState = WaveState.WAITING_CLEAR;
                break;
            case WAITING_CLEAR:
                boolean hasActiveEnemies = false;
                for (UUID id : new ArrayList<>(activeMobs)) {
                    Entity e = level.getEntity(id);
                    if (e == null || !e.isAlive()) {
                        activeMobs.remove(id);
                    } else if (isSpecialWave && e instanceof HellhoundEntity) {
                        hasActiveEnemies = true;
                    } else if (!isSpecialWave && (e instanceof ZombieEntity || e instanceof CrawlerEntity || e instanceof HellhoundEntity)) {
                        hasActiveEnemies = true;
                    }
                }
                if (!hasActiveEnemies) {
                    if (isSpecialWave) triggerSpecialWaveEnd(level, null);
                    else {
                        LuaScriptManager.callEvent("OnAllZombiesDead");
                        triggerNormalWaveEnd(level);
                    }
                }
                handleBgSound(level);
                break;
        }
    }

    public static void recalculateSpawnInterval(ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        double rawDelay = 2500.0 * Math.pow(0.90, currentWave - 1);
        currentSpawnInterval = Math.max(10, (int)(rawDelay / 50.0));
        
        if (currentWave >= 5) {
            String intensity = config.getSpawnIntensity();
            if ("flood".equals(intensity)) {
                currentSpawnInterval = Math.max(5, currentSpawnInterval / 2);
            } else if ("chaos".equals(intensity)) {
                currentSpawnInterval = Math.max(2, currentSpawnInterval / 4);
            }
        }
        spawnTimer = Math.min(spawnTimer, currentSpawnInterval);
    }

    private static void handleBgSound(ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        if (config.isSprintBgSoundsEnabled() && currentWave >= config.getZombieSprintWave() && !isSpecialWave) {
            if (activeMobs.size() > 3) {
                if (--bgSoundTimer <= 0) {
                    if (bgSoundState == 0) {
                        int idx = ThreadLocalRandom.current().nextInt(0, BG_SPRINT_SOUNDS.size());
                        playGlobalSound(level, BG_SPRINT_SOUNDS.get(idx));
                        if (ThreadLocalRandom.current().nextDouble() < 0.20) {
                            bgSoundState = 1;
                            bgSoundTimer = ThreadLocalRandom.current().nextInt(4, 17);
                        } else {
                            bgSoundState = 0;
                            bgSoundTimer = 80 + ThreadLocalRandom.current().nextInt(41);
                        }
                    } else if (bgSoundState == 1) {
                        int idx = ThreadLocalRandom.current().nextInt(0, 4);
                        playGlobalSound(level, BG_SPRINT_SOUNDS.get(idx));
                        bgSoundState = 0;
                        bgSoundTimer = 80 + ThreadLocalRandom.current().nextInt(41);
                    }
                }
            } else {
                bgSoundTimer = 10;
            }
        }
    }

    public static void respawnAllSpectatorPlayers(ServerLevel level) {
        String penalty = WorldConfig.get(level).getDeathPenalty();
        if ("spectator".equalsIgnoreCase(penalty) || "kick".equalsIgnoreCase(penalty)) return;

        List<BlockPos> activePlayerSpawners = new ArrayList<>();
        for (BlockPos pos : WorldConfig.get(level).getPlayerSpawnerPositions()) {
            if (level.getBlockEntity(pos) instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity ube && 
                ube.getMobType() == UniversalSpawnerSystem.SpawnerMobType.PLAYER && ube.isActive(level)) {
                activePlayerSpawners.add(pos);
            }
        }

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                p.setGameMode(GameType.ADVENTURE);
                p.setHealth(p.getMaxHealth());
                p.removeAllEffects();

                BlockPos respawnPos = PLAYER_RESPAWN_POINTS.get(p.getUUID()); 
                if (!activePlayerSpawners.isEmpty()) {
                    respawnPos = activePlayerSpawners.stream()
                        .min(Comparator.comparingDouble(pos -> p.distanceToSqr(pos.getX(), pos.getY(), pos.getZ())))
                        .orElse(respawnPos);
                }

                if (respawnPos != null) {
                    p.teleportTo(level, respawnPos.getX() + 0.5, respawnPos.getY() + 1.0, respawnPos.getZ() + 0.5, p.getYRot(), p.getXRot());
                    me.cryo.zombierool.PointManager.modifyScore(p, 500);

                    ResourceLocation starterItemId = WorldConfig.get(level).getStarterItem();
                    ItemStack pistol = me.cryo.zombierool.core.system.WeaponFacade.createWeaponStack(starterItemId.toString(), false, p);
                    if (pistol.isEmpty()) {
                        Item starterItemVanilla = ForgeRegistries.ITEMS.getValue(starterItemId);
                        if (starterItemVanilla == null) starterItemVanilla = net.minecraft.world.item.Items.WOODEN_SWORD;
                        pistol = new ItemStack(starterItemVanilla);
                    }
                    if (!p.getInventory().add(pistol)) p.drop(pistol, false);

                    PlayerVoiceManager.playRespawn(p, level);
                }
            }
        }
    }

    private static void spawnNextWave(ServerLevel level) {
        currentWave++;
        sendWaveUpdateToClients(level);
        respawnAllSpectatorPlayers(level);
        LuaScriptManager.callEvent("OnWaveStart", currentWave);

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                if (cap.getLethalCount() < 5) {
                    cap.setLethalCount(cap.getLethalCount() + 1);
                    cap.sync(p);
                }
            });
        }

        setupRegularWave(level);
    }

    private static Optional<BlockPos> getRandomSpawnerPosition(ServerLevel level, UniversalSpawnerSystem.SpawnerMobType type) {
        List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> valid = SpawnerRegistry.getSpawners(level).stream()
                .filter(be -> be.getMobType() == type && be.isActive(level))
                .collect(Collectors.toList());

        if (valid.isEmpty() && type == UniversalSpawnerSystem.SpawnerMobType.HELLHOUND) {
            valid = SpawnerRegistry.getSpawners(level).stream()
                    .filter(be -> be.getMobType() == UniversalSpawnerSystem.SpawnerMobType.ZOMBIE && be.isActive(level))
                    .collect(Collectors.toList());
        }

        if (valid.isEmpty()) return Optional.empty();

        int totalWeight = valid.stream().mapToInt(UniversalSpawnerSystem.UniversalSpawnerBlockEntity::getSpawnWeight).sum();
        if (totalWeight <= 0) return Optional.of(valid.get(0).getBlockPos());

        int r = ThreadLocalRandom.current().nextInt(totalWeight);
        for (UniversalSpawnerSystem.UniversalSpawnerBlockEntity be : valid) {
            r -= be.getSpawnWeight();
            if (r < 0) return Optional.of(be.getBlockPos());
        }
        return Optional.of(valid.get(0).getBlockPos());
    }

    private static void setupRegularWave(ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        int playerCount = (int) level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.isAlive())
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .filter(p -> !PlayerDownManager.isPlayerDown(p.getUUID()))
                .count();
        if (playerCount == 0) playerCount = 1;

        int count = (int) ((config.getBaseZombies() + (currentWave * 3.5)) * (1.0 + (playerCount - 1) * 0.5));
        
        if (currentWave >= 5) {
            String intensity = config.getSpawnIntensity();
            if ("flood".equals(intensity)) {
                count = (int)(count * 1.3);
            } else if ("chaos".equals(intensity)) {
                count = (int)(count * 1.6);
            }
        }
        
        zombiesToKill = count;
        mobsToSpawnInThisWave = count;

        Random rand = ThreadLocalRandom.current();
        int minC = Math.max(1, (int) (count * 0.15));
        int maxC = Math.max(1, (int) (count * 0.40));
        int numCrawlers = rand.nextInt(maxC - minC + 1) + minC;

        if (!SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.CRAWLER)) {
            numCrawlers = 0;
        }

        currentWaveSpawnOrder = new ArrayList<>(count);
        for (int j = 0; j < numCrawlers; j++) currentWaveSpawnOrder.add(true);
        for (int j = 0; j < count - numCrawlers; j++) currentWaveSpawnOrder.add(false);
        Collections.shuffle(currentWaveSpawnOrder, rand);

        double rawDelay = 2500.0 * Math.pow(0.90, currentWave - 1);
        currentSpawnInterval = Math.max(10, (int)(rawDelay / 50.0));
        
        if (currentWave >= 5) {
            String intensity = config.getSpawnIntensity();
            if ("flood".equals(intensity)) {
                currentSpawnInterval = Math.max(5, currentSpawnInterval / 2);
            } else if ("chaos".equals(intensity)) {
                currentSpawnInterval = Math.max(2, currentSpawnInterval / 4);
            }
        }

        spawnTimer = currentSpawnInterval;
        currentState = WaveState.SPAWNING_NORMAL;
    }

    private static void spawnOneRegularMob(ServerLevel level) {
        final int idx = currentWaveSpawnOrder.size() - 1;
        if (idx < 0) return;

        WorldConfig config = WorldConfig.get(level);
        Random rand = ThreadLocalRandom.current();

        if (config.isHellhoundsInNormalWaves() && currentWave >= config.getHellhoundsInNormalWavesStart()) {
            if (rand.nextDouble() < 0.05) {
                Optional<BlockPos> opt = getRandomSpawnerPosition(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND);
                if (opt.isPresent()) {
                    HellhoundEntity h = ZombieroolModEntities.HELLHOUND.get().create(level);
                    if (h != null) {
                        applyHealthScaling(h, currentWave, level);
                        h.setCustomSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin("hellhound"));
                        Vec3 v = Vec3.atBottomCenterOf(opt.get());
                        h.moveTo(v.x, v.y, v.z, rand.nextFloat() * 360F, 0);
                        level.addFreshEntity(h);
                        activeMobs.add(h.getUUID());
                        mobsToSpawnInThisWave--;
                        currentWaveSpawnOrder.remove(idx);
                        return;
                    }
                }
            }
        }

        final boolean wantedCrawler = currentWaveSpawnOrder.remove(idx);
        mobsToSpawnInThisWave--;

        boolean hasCrawlerSpawners = SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.CRAWLER);
        UniversalSpawnerSystem.SpawnerMobType spawnType = (wantedCrawler && hasCrawlerSpawners) ? UniversalSpawnerSystem.SpawnerMobType.CRAWLER : UniversalSpawnerSystem.SpawnerMobType.ZOMBIE;

        Optional<BlockPos> optPos = getRandomSpawnerPosition(level, spawnType);
        if (optPos.isEmpty()) return;

        Vec3 v = Vec3.atBottomCenterOf(optPos.get());
        Mob mob;
        if (spawnType == UniversalSpawnerSystem.SpawnerMobType.CRAWLER) {
            CrawlerEntity c = new CrawlerEntity(ZombieroolModEntities.CRAWLER.get(), level);
            applyHealthScaling(c, currentWave, level);
            c.setCustomSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin("crawler"));
            mob = c;
        } else {
            ZombieEntity z = new ZombieEntity(ZombieroolModEntities.ZOMBIE.get(), level);
            applyHealthScaling(z, currentWave, level);
            applySpeedScaling(z, currentWave, rand, level);
            z.setCustomSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin("zombie"));
            mob = z;
        }

        mob.moveTo(v.x, v.y, v.z, rand.nextFloat() * 360F, 0);
        level.addFreshEntity(mob);
        activeMobs.add(mob.getUUID());
    }

    private static void spawnSpecialWave(ServerLevel level) {
        currentWave++;
        sendWaveUpdateToClients(level);

        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            endGame(level, Component.translatable("message.zombierool.game_over.no_players"));
            return;
        }

        respawnAllSpectatorPlayers(level);
        LuaScriptManager.callEvent("OnWaveStart", currentWave);

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                if (cap.getLethalCount() < 5) {
                    cap.setLethalCount(cap.getLethalCount() + 1);
                    cap.sync(p);
                }
            });
        }

        specialWaveTotal.set((4 + 2 * players.size()) * 2);
        specialWaveKilled.set(0);
        hellhoundsCurrentlyActive.set(0);
        specialSpawnedCount.set(0);

        currentState = WaveState.SPAWNING_SPECIAL;
        spawnTimer = 100;
    }

    private static void spawnOneHellhound(ServerLevel level) {
        Optional<BlockPos> opt = getRandomSpawnerPosition(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND);
        if (opt.isEmpty()) {
            specialSpawnedCount.incrementAndGet();
            return;
        }

        HellhoundEntity h = ZombieroolModEntities.HELLHOUND.get().create(level);
        if (h == null) return;

        applyHealthScaling(h, currentWave, level);
        h.setCustomSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin("hellhound"));

        Vec3 v = Vec3.atBottomCenterOf(opt.get());
        h.moveTo(v.x, v.y, v.z, level.getRandom().nextFloat() * 360f, 0);
        level.addFreshEntity(h);
        activeMobs.add(h.getUUID());

        specialSpawnedCount.incrementAndGet();
        hellhoundsCurrentlyActive.incrementAndGet();
    }

    private static void applyHealthScaling(Mob mob, int wave, ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        float baseHealth;
        float maxCap;

        if (mob instanceof ZombieEntity) {
            baseHealth = config.getZombieBaseHealth();
            maxCap = config.getZombieMaxHealth();
            if (wave < 2) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth);
                mob.setHealth(baseHealth);
                return;
            }
        } else if (mob instanceof HellhoundEntity) {
            if (wave < 6) return;
            baseHealth = config.getHellhoundBaseHealth();
            maxCap = config.getHellhoundMaxHealth();
        } else if (mob instanceof CrawlerEntity) {
            baseHealth = config.getCrawlerBaseHealth();
            maxCap = config.getCrawlerMaxHealth();
            if (wave < 2) {
                mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(baseHealth);
                mob.setHealth(baseHealth);
                return;
            }
        } else {
            baseHealth = 20f; maxCap = 40f;
        }

        float newMaxHealth = baseHealth;
        if (wave <= 9) {
            newMaxHealth += wave * 4.0f;
        } else {
            newMaxHealth = baseHealth + (9 * 4.0f);
            newMaxHealth *= (float) Math.pow(1.15, wave - 9); 
        }
        newMaxHealth = Math.min(newMaxHealth, maxCap);

        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(newMaxHealth);
        mob.setHealth(newMaxHealth);
    }

    private static void applySpeedScaling(ZombieEntity zombie, int wave, Random rand, ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        boolean isSuperSprinter = false;

        if (config.areSuperSprintersEnabled() && wave >= config.getSuperSprinterActivationWave()) {
            double baseSuperChance = config.getSuperSprinterChance();
            double scaledSuperChance = baseSuperChance + (wave - config.getSuperSprinterActivationWave()) * 0.015;
            double finalSuperChance = Math.min(0.25, scaledSuperChance); 

            if (rand.nextDouble() < finalSuperChance) {
                zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.getSuperSprinterSpeed());
                isSuperSprinter = true;
                zombie.getAttribute(Attributes.ATTACK_DAMAGE).removeModifier(UUID.fromString("9381c8b3-3a56-42d4-a1f9-0c6a51d4e0e5"));
                zombie.getAttribute(Attributes.ATTACK_DAMAGE).addTransientModifier(new AttributeModifier(UUID.fromString("9381c8b3-3a56-42d4-a1f9-0c6a51d4e0e5"), "Super Sprinter Damage", (zombie.getAttribute(Attributes.ATTACK_DAMAGE).getBaseValue() * (SUPER_SPRINTER_DAMAGE_MULTIPLIER - 1.0)), AttributeModifier.Operation.ADDITION));
            }
        }

        zombie.setSuperSprinter(isSuperSprinter);

        if (!isSuperSprinter && config.isZombiesCanSprint() && wave >= config.getZombieSprintWave()) {
            double sprintChance = Math.min(1.0, config.getZombieSprintChance() + (wave - config.getZombieSprintWave()) * 0.05);
            if (rand.nextDouble() <= sprintChance) {
                zombie.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(config.getZombieSprintSpeed());
            }
        }
    }

    public static void recycleMob(Mob mob, ServerLevel level) {
        UUID id = mob.getUUID();
        if (activeMobs.contains(id)) {
            activeMobs.remove(id);
            PlayerStatsManager.cleanupEntity(mob.getId()); 
            mob.discard();

            if (mob instanceof HellhoundEntity) {
                hellhoundsCurrentlyActive.decrementAndGet();
                if (isSpecialWave) specialSpawnedCount.decrementAndGet();
            } else {
                mobsToSpawnInThisWave++;
                currentWaveSpawnOrder.add(mob instanceof CrawlerEntity);
            }
        }
    }

    private static void clearAllActiveMobs(ServerLevel level) {
        for (UUID mobId : new HashSet<>(activeMobs)) {
            Entity mob = level.getEntity(mobId);
            if (mob != null) {
                PlayerStatsManager.cleanupEntity(mob.getId()); 
                mob.discard();
            }
        }
        activeMobs.clear();
        processedDeaths.clear();
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (!gameRunning || isPausedByPlayer || event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof Mob mob && (mob instanceof ZombieEntity || mob instanceof CrawlerEntity || mob instanceof HellhoundEntity)) {
            onMobDeath(mob, (ServerLevel) mob.level());
        }
    }

    public static void onMobDeath(Mob mob, ServerLevel level) {
        if (!gameRunning || isPausedByPlayer) return;

        UUID id = mob.getUUID();
        if (!activeMobs.contains(id) || !processedDeaths.add(id)) return;

        activeMobs.remove(id);

        if (mob instanceof HellhoundEntity) {
            hellhoundsCurrentlyActive.decrementAndGet();
            if (isSpecialWave) {
                if (specialWaveKilled.incrementAndGet() >= specialWaveTotal.get() && activeMobs.stream().noneMatch(uuid -> level.getEntity(uuid) instanceof HellhoundEntity)) {
                    triggerSpecialWaveEnd(level, mob.position());
                }
            } else {
                zombiesToKill--;
                checkEndNormalWave(level);
            }
        } else {
            zombiesKilledSinceLastBonus++;
            zombiesToKill--;
            checkEndNormalWave(level);
        }
    }

    private static void triggerSpecialWaveEnd(ServerLevel level, Vec3 lastMobPos) {
        if (currentState == WaveState.DELAY_NEXT || currentState == WaveState.DELAY_SPECIAL_SOUND) return;

        if (!BonusManager.bonusSpawnDisabledByNuke && WorldConfig.get(level).isBonusDropsEnabled()) {
            BonusManager.Bonus maxAmmo = BonusManager.getBonus("max_ammo");
            if (maxAmmo != null && !WorldConfig.get(level).isBonusDisabled("max_ammo")) {
                Vec3 spawnPos = lastMobPos != null ? lastMobPos : (level.getRandomPlayer() != null ? level.getRandomPlayer().position() : null);
                if (spawnPos != null) BonusManager.spawnBonus(maxAmmo, level, spawnPos);
            }
        }

        LuaScriptManager.callEvent("OnWaveEnd", currentWave);
        transitionToNextWave(level);
    }

    private static void triggerNormalWaveEnd(ServerLevel level) {
        if (currentState == WaveState.DELAY_NEXT || currentState == WaveState.DELAY_SPECIAL_SOUND) return;
        LuaScriptManager.callEvent("OnWaveEnd", currentWave);
        transitionToNextWave(level);
    }

    private static void transitionToNextWave(ServerLevel level) {
        int nextWave = currentWave + 1;
        WorldConfig config = WorldConfig.get(level);

        boolean isNextWaveSpecial = config.isSpecialWavesEnabled() && nextWave >= config.getSpecialWaveStart() && (nextWave - config.getSpecialWaveStart()) % config.getSpecialWaveInterval() == 0;
        if (isNextWaveSpecial && !SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND)) {
            isNextWaveSpecial = false;
        }

        triggerWaveChange(level, currentWave, nextWave);
        isSpecialWave = false;
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(false));

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (isGameRunning() && !p.isCreative() && !p.isSpectator()) {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new me.cryo.zombierool.network.packet.S2CProgressChallengePacket("WAVES", 1));
            }
        }

        if (isNextWaveSpecial) {
            currentState = WaveState.DELAY_SPECIAL_SOUND;
            stateTimer = 240; 
        } else {
            currentState = WaveState.DELAY_NEXT;
            stateTimer = 240;
        }
    }

    private static void checkEndNormalWave(ServerLevel level) {
        if (zombiesToKill <= 0 && activeMobs.stream().noneMatch(uuid -> level.getEntity(uuid) instanceof Mob)) {
            LuaScriptManager.callEvent("OnAllZombiesDead");
            triggerNormalWaveEnd(level);
        }
    }

    public static synchronized void endGame(ServerLevel level, Component message) {
        if (!gameRunning) return;

        currentState = WaveState.OFF;
        gameRunning = false;
        isSpecialWave = false;

        UNLOCKED_CHANNELS.clear();
        LOCKED_CHANNELS.clear();
        PickableManager.reset(level); 
        me.cryo.zombierool.block.system.BlindBuySystem.resetAllCabinets(level);
        me.cryo.zombierool.handlers.ServerInteractionHandler.resetAll();

        currentSessionMusic = WorldConfig.get(level).getMusicPreset();
        if (currentSessionMusic == null || currentSessionMusic.isEmpty()) {
            currentSessionMusic = "default";
        }
        me.cryo.zombierool.ZombieroolMod.queueServerWork(30, () -> {
            level.getServer().getPlayerList().getPlayers().forEach(p -> {
                p.sendSystemMessage(Component.literal("ZOMBIEROOL_MUSIC_PRESET:" + currentSessionMusic));
            });
        });

        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(false));

        clearAllActiveMobs(level);

        int wavesSurvived = currentWave;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                int k = cap.getKills();
                int h = cap.getHeadshots();
                int a = cap.getAssists();
                int d = cap.getDowns();
                int tp = cap.getTotalPoints();
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), new S2CMatchRecapPacket(wavesSurvived, k, h, a, d, tp));
            });
        }

        currentWave = 0;
        sendWaveUpdateToClients(level);

        level.getServer().getWorldData().setDifficulty(Difficulty.PEACEFUL);

        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.isAlive()) {
                if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR || PlayerDownManager.isPlayerDown(p.getUUID())) {
                    p.setGameMode(GameType.ADVENTURE);
                    p.setHealth(p.getMaxHealth());
                }
            }
            p.removeAllEffects();
            p.getPersistentData().remove("zr_has_bowie_knife"); 
            p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                cap.resetStats();
                cap.resetPerkPurchases();
                cap.resetTotalPoints();
                cap.setLethalType(WorldConfig.get(level).getStartingLethal()); 
                cap.setLethalCount(5); 
                cap.sync(p);
            });
            if (p.isAlive()) {
                me.cryo.zombierool.PointManager.setScore(p, 500);
            }
        }

        PlayerStatsManager.syncAll(level);

        broadcast(level, message);
    }

    public static int getZombiesKilledSinceLastBonus(ServerLevel level) { return zombiesKilledSinceLastBonus; }
    public static void resetZombiesKilledSinceLastBonus(ServerLevel level) { zombiesKilledSinceLastBonus = 0; }

    public static void sendWaveUpdateToClients(ServerLevel level) { NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CWaveUpdatePacket(currentWave)); }
    private static void playGlobalSound(ServerLevel level, ResourceLocation soundRes) { NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayGlobalSoundPacket(soundRes)); }
    private static void broadcast(ServerLevel level, Component msg) { for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) p.sendSystemMessage(msg); }
    private static void broadcast(ServerLevel level, String msg) { broadcast(level, Component.literal(msg)); }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) { 
        if (event.getServer().isSingleplayer()) setGamePaused(true); 
        ServerLevel overworld = event.getServer().overworld();
        if (overworld != null) {
            me.cryo.zombierool.block.system.BlindBuySystem.resetAllCabinets(overworld);
        }
        gameRunning = false;
        currentState = WaveState.OFF;
        isSpecialWave = false;
        currentWave = 0;

        List<Entity> toRemove = new ArrayList<>();
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            for (Entity e : sl.getAllEntities()) {
                if (e instanceof ZombieEntity || 
                    e instanceof CrawlerEntity || 
                    e instanceof HellhoundEntity || 
                    e instanceof me.cryo.zombierool.entity.WhiteKnightEntity || 
                    e instanceof net.minecraft.world.entity.projectile.Projectile) {
                    toRemove.add(e);
                }
            }
        }
        toRemove.forEach(Entity::discard);

        activeMobs.clear();
        processedDeaths.clear();
        UNLOCKED_CHANNELS.clear();
        LOCKED_CHANNELS.clear();
        UNLOCKED_ZONES.clear();
        PLAYER_RESPAWN_POINTS.clear();
        zombiesToKill = 0;
        zombiesKilledSinceLastBonus = 0;
        specialWaveTotal.set(0);
        specialWaveKilled.set(0);
        specialSpawnedCount.set(0);
        hellhoundsCurrentlyActive.set(0);
        PlayerDownManager.resetAll();
        me.cryo.zombierool.handlers.ServerInteractionHandler.resetAll();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) { if (event.getServer().isSingleplayer()) setGamePaused(false); }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) { if (event.getEntity() instanceof ServerPlayer && event.getEntity().getServer().isSingleplayer()) setGamePaused(false); }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) { if (event.getEntity() instanceof ServerPlayer && event.getEntity().getServer().isSingleplayer()) setGamePaused(true); }

    @Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientLogout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
            clientCurrentWave = 0;
            clientIsSpecialWave = false;
            clientGameRunning = false;
        }
    }
}