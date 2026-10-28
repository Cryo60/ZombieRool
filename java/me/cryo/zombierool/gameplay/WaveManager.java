package me.cryo.zombierool.gameplay;
import me.cryo.zombierool.config.WorldConfig;
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
import me.cryo.zombierool.network.packet.S2CMatchRecapPacket;
import me.cryo.zombierool.network.packet.S2CWaveUpdatePacket;
import me.cryo.zombierool.network.packet.S2CSpecialWavePacket;
import me.cryo.zombierool.entity.ZombieEntity;
import me.cryo.zombierool.entity.CrawlerEntity;
import me.cryo.zombierool.entity.HellhoundEntity;
import me.cryo.zombierool.init.ZombieroolModEntities;
import me.cryo.zombierool.gameplay.wave.WaveMobScaling;
import me.cryo.zombierool.util.PlayerVoiceManager;
import me.cryo.zombierool.util.ZrNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
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
    private static final me.cryo.zombierool.gameplay.wave.WaveBackgroundAudio BACKGROUND_AUDIO=new me.cryo.zombierool.gameplay.wave.WaveBackgroundAudio();
    public static final Set<Integer> UNLOCKED_CHANNELS = ConcurrentHashMap.newKeySet();
    public static final Set<Integer> LOCKED_CHANNELS = ConcurrentHashMap.newKeySet();
    public static final Set<String> UNLOCKED_ZONES = ConcurrentHashMap.newKeySet();
    private static int currentWave = 0;
    private static boolean gameRunning = false;
    private static boolean isSpecialWave = false;
    private static volatile boolean isPausedByPlayer = false;
    private static boolean cheatsUsed = false;
    private static final Set<UUID> activeMobs = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> processedDeaths = ConcurrentHashMap.newKeySet();
    private static int clientCurrentWave = 0;
    private static boolean clientIsSpecialWave = false;
    private static boolean clientGameRunning = false;
    public static final Map<UUID, BlockPos> PLAYER_RESPAWN_POINTS = new ConcurrentHashMap<>();
    private static final ResourceLocation START_SOUND = new ResourceLocation("zombierool", "start_zombie");
    private static final ResourceLocation NEXT_WAVE_SOUND = new ResourceLocation("zombierool", "next_wave_zombie");
    private static boolean forcedSpecialNumberReserved;
    private static final AtomicInteger specialWaveTotal = new AtomicInteger(0);
    private static final AtomicInteger specialWaveKilled = new AtomicInteger(0);
    private static final AtomicInteger specialSpawnedCount = new AtomicInteger(0);
    private static final AtomicInteger hellhoundsCurrentlyActive = new AtomicInteger(0);
    private static int zombiesToKill = 0;
    private static int zombiesKilledSinceLastBonus = 0;
    private static int introTicks;
    private static long matchElapsedTicks;
    private static int introExitTicks=-1;
    private record SuspendedRound(WaveState state,int timer,int spawn,boolean special,int kills,List<net.minecraft.nbt.CompoundTag> mobs,Runnable restoreQueue){}
    private static SuspendedRound suspendedRound;
    public static long getMatchElapsedTicks(){return matchElapsedTicks;}
    public static void finishIntroAfterTeleport(){if(gameRunning&&currentState==WaveState.INTRO&&introExitTicks<0)introExitTicks=TeleporterPlayerLock.TUNNEL_TICKS+TeleporterPlayerLock.DAZE_TICKS+20;}

    private static boolean clientIntroPhase;
    private enum WaveState { OFF, INTRO, DELAY_FIRST, DELAY_NEXT, DELAY_SPECIAL_SOUND, DELAY_SPECIAL_SPAWN, SPAWNING_NORMAL, SPAWNING_SPECIAL, WAITING_CLEAR }
    private static WaveState currentState = WaveState.OFF;
    private static int stateTimer = 0;
    private static int spawnTimer = 0;
    private static int currentSpawnInterval = 0;
    private static List<Boolean> currentWaveSpawnOrder = new ArrayList<>();
    private static int mobsToSpawnInThisWave = 0;
    public static String currentSessionMusic = "default";
    public static int perkCoinEpoch;
    public static void resetPerkEgg(ServerLevel level) {
        perkCoinEpoch++;
        MapPower.resetPerkCoins(level);
    }
    public static boolean isGameRunning() {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            if (Minecraft.getInstance().isSameThread() && Minecraft.getInstance().level != null) return clientGameRunning;
        }
        return gameRunning;
    }
    public static void setClientGameRunning(boolean running) {
        clientGameRunning = running;
        if(!running)clientIsSpecialWave=false;
    }
    public static int remainingEnemies(){
        if(!gameRunning||isIntroPhase())return 0;int budget=Math.max(0,isSpecialWave?specialWaveTotal.get()-specialWaveKilled.get():zombiesToKill);
        if(currentState!=WaveState.SPAWNING_NORMAL&&currentState!=WaveState.SPAWNING_SPECIAL&&currentState!=WaveState.WAITING_CLEAR)return budget;
        var server=net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();if(server==null)return budget;var level=server.overworld();int living=0,bosses=0;
        for(var id:activeMobs){var e=level.getEntity(id);if(e instanceof Mob&&e.isAlive()&&!EncounterRules.roundIndependent(e))living++;}
        for(var e:level.getAllEntities())if(e instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&boss.isBoss()&&boss.isAlive()&&!EncounterRules.roundIndependent(boss)&&!activeMobs.contains(boss.getUUID()))bosses++;
        int unspawned=isSpecialWave?Math.max(0,specialWaveTotal.get()-specialSpawnedCount.get()):Math.max(0,mobsToSpawnInThisWave);
        return Math.max(budget,unspawned+living+SpawnRedistribution.pendingCount(level))+bosses;
    }
    public static boolean isSpecialWave() { return isSpecialWave; }
    public static boolean isCombatWave(){return currentState==WaveState.INTRO||currentState==WaveState.SPAWNING_NORMAL||currentState==WaveState.SPAWNING_SPECIAL||currentState==WaveState.WAITING_CLEAR;}
    public static boolean isPausedByPlayer() { return isPausedByPlayer; }
    public static boolean areCheatsUsed() { return cheatsUsed; }
    public static void setCheatsUsed(boolean used) { cheatsUsed = used; }
    public static int getCurrentWave() {
        if (FMLLoader.getDist() == Dist.CLIENT) {
            if (Minecraft.getInstance().isSameThread() && Minecraft.getInstance().level != null) return clientCurrentWave;
        }
        return currentWave;
    }
    public static void setClientWave(int wave) { clientCurrentWave = wave; }
    public static boolean isClientSpecialWave() { return clientIsSpecialWave; }
    private static boolean clientHellhoundFog;
    public static boolean isClientHellhoundFog(){return clientHellhoundFog;}
    public static void setClientHellhoundFog(boolean fog){clientHellhoundFog=fog;}
    public static void setClientSpecialWave(boolean special) { clientIsSpecialWave = special;if(!special)clientHellhoundFog=false; }
    public static void unlockChannel(int channel) {
        SpawnChannelActions.external(channel);
        if (channel > 0) {
            UNLOCKED_CHANNELS.add(channel);
            LOCKED_CHANNELS.remove(channel);
        }
    }
    public static void lockChannel(int channel) {
        SpawnChannelActions.external(channel);
        UNLOCKED_CHANNELS.remove(channel);
        LOCKED_CHANNELS.add(channel);
    }
    private static void triggerWaveChange(ServerLevel level, int prevWave, int nextWave) {
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CWaveChangeAnimationPacket(prevWave, nextWave));
        ZrNetwork.playGlobalSound(NEXT_WAVE_SOUND);
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
        // Defeat returns the world to Peaceful; every entry point must restore hostile spawning.
        if(level.getDifficulty()==Difficulty.PEACEFUL)level.getServer().setDifficulty(Difficulty.NORMAL,true);
        long transitionStart=System.nanoTime();
        isPausedByPlayer = false;
        SpecialEnemySpawns.reset(level);
        MiniBossTransitions.reset(level);BrutusMachineLocks.reset(level);
        clearDroppedItems(level);
        me.cryo.zombierool.core.manager.GoreManager.clearWorld(level);
        me.cryo.zombierool.block.system.MapDeviceSystem.reset(level);
        me.cryo.zombierool.block.system.MachineDeliverySystem.reset(level);
        me.cryo.zombierool.block.system.TeleporterSystem.reset(level);
        for(var world:level.getServer().getAllLevels())MapAtmosphere.clearPlayerOverrides(world);
        TraversalMotion.reset(level);
        SpawnChannelActions.reset();
        me.cryo.zombierool.block.system.SoulChestSystem.reset(level);
        MrChiefEasterEgg.reset(level);
        MapPower.resetSignals(level);
        for (var buildLevel : level.getServer().getAllLevels()) BuildableSystem.get(buildLevel).reset(buildLevel);
        MatchWorldJournal.restore(level);
        QuestBlockSnapshots.get(level).restoreAll(level);
        me.cryo.zombierool.block.system.BlindBuySystem.resetAllCabinets(level);
        WorldConfig.get(level).migrateEncounterLabHorde();
        MatchConfigSnapshot.begin(level);
        MapPower.refresh(level);
        resetPerkEgg(level);
        MapActors.begin(level);
        gameRunning = true;
        matchElapsedTicks=0;suspendedRound=null;introExitTicks=-1;forcedSpecialNumberReserved=false;
        currentWave = 0;
        cheatsUsed = false;
        activeMobs.clear();
        SpawnRedistribution.reset();
        zombiesKilledSinceLastBonus = 0;
        PlayerStatsManager.reset(level);
        PickableManager.initGame(level);
        PlayerDownManager.resetAll();
        me.cryo.zombierool.gameplay.AmmoCrateManager.get(level).resetAllData();
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
                ube.getMobType() == UniversalSpawnerSystem.SpawnerMobType.PLAYER && ube.isActive(level)) {
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
                String currentZoneKey = zoneKeys.get(zoneIndex % zoneKeys.size());
                List<UniversalSpawnerSystem.UniversalSpawnerBlockEntity> currentZoneSpawners = zones.get(currentZoneKey);
                UniversalSpawnerSystem.UniversalSpawnerBlockEntity chosenSpawner = currentZoneSpawners.get(ThreadLocalRandom.current().nextInt(currentZoneSpawners.size()));
                BlockPos spawnPos = chosenSpawner.getBlockPos();
                PLAYER_RESPAWN_POINTS.put(player.getUUID(), spawnPos.immutable());
                MatchPlayerReset.applyStartLoadout(player, worldConfig, charId);
                giveStarterIfMissing(player, worldConfig);
                player.inventoryMenu.broadcastChanges();
                charId = MatchPlayerReset.nextCharacterId(charId);
                UNLOCKED_ZONES.add(currentZoneKey);
                zoneIndex++;
            }
        } else {
            UNLOCKED_ZONES.add("DEFAULT");
            int charId = 1;
            for (ServerPlayer player : players) {
                MatchPlayerReset.applyStartLoadout(player, worldConfig, charId);
                giveStarterIfMissing(player, worldConfig);
                charId = MatchPlayerReset.nextCharacterId(charId);
            }
        }
        long resetFinished=System.nanoTime();
        PlayerStatsManager.syncAll(level);
        LuaScriptManager.loadScripts(level);
        for (var buildLevel : level.getServer().getAllLevels()) BuildableSystem.get(buildLevel).start(buildLevel);
        for(ServerPlayer player:players){player.inventoryMenu.broadcastChanges();placeOnAssignedSpawner(player);}
        me.cryo.zombierool.block.system.MusicVolume.resetAll(level);
        LuaScriptManager.callEvent("OnGameStart");
        Set<BlockPos> mysteryBoxPositions = worldConfig.getMysteryBoxPositions();
        if (!mysteryBoxPositions.isEmpty()) {
            me.cryo.zombierool.gameplay.MysteryBoxManager mysteryBoxManager = me.cryo.zombierool.gameplay.MysteryBoxManager.get(level);
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
        if(IntroPhase.settings(level).getBoolean("enabled")||isIntroPhase()){
            if(!isIntroPhase())startIntroPhase(level);
        }else{
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CStartGameAnimationPacket(1, currentSessionMusic));
            ZrNetwork.playGlobalSound(START_SOUND);
            currentState=WaveState.DELAY_FIRST;stateTimer=240;
        }
        org.slf4j.LoggerFactory.getLogger("ZombieRool").info("Match start: reset/loadout={} ms, scripts/machines={} ms, total={} ms",(resetFinished-transitionStart)/1_000_000,(System.nanoTime()-resetFinished)/1_000_000,(System.nanoTime()-transitionStart)/1_000_000);
    }
    public static boolean isIntroPhase(){
        if(FMLLoader.getDist()==Dist.CLIENT&&Minecraft.getInstance().isSameThread()&&Minecraft.getInstance().level!=null)return clientIntroPhase;
        return gameRunning&&currentState==WaveState.INTRO;
    }
    public static void setClientIntroPhase(boolean intro){clientIntroPhase=intro;}
    public static int getIntroElapsedTicks(){return isIntroPhase()?introTicks:0;}
    public static boolean startIntroPhase(ServerLevel level){
        if(!gameRunning||currentState==WaveState.INTRO)return false;
        if(currentWave>0){
            var saved=new ArrayList<net.minecraft.nbt.CompoundTag>();
            for(UUID id:new HashSet<>(activeMobs))if(level.getEntity(id) instanceof Mob mob){TraversalMotion.cancel(mob);var tag=new net.minecraft.nbt.CompoundTag();if(mob.save(tag))saved.add(tag);mob.discard();}
            activeMobs.clear();suspendedRound=new SuspendedRound(currentState,stateTimer,spawnTimer,isSpecialWave,zombiesKilledSinceLastBonus,saved,SpawnRedistribution.suspend(level));
        }else{clearAllActiveMobs(level);suspendedRound=null;zombiesToKill=0;mobsToSpawnInThisWave=0;currentWaveSpawnOrder.clear();}
        isSpecialWave=false;currentState=WaveState.INTRO;introTicks=0;spawnTimer=20;introExitTicks=-1;
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CSpecialWavePacket(false));sendWaveUpdateToClients(level);
        LuaScriptManager.callEvent("OnIntroStart");return true;
    }
    public static boolean finishIntroPhase(ServerLevel level){
        if(!isIntroPhase())return false;
        LuaScriptManager.callEvent("OnIntroEnd",introTicks/20.0);introExitTicks=-1;
        if(suspendedRound!=null){
            var saved=suspendedRound;suspendedRound=null;
            for(UUID id:new HashSet<>(activeMobs)){var mob=level.getEntity(id);if(mob!=null)mob.discard();}activeMobs.clear();SpawnRedistribution.reset();
            currentState=saved.state;stateTimer=saved.timer;spawnTimer=saved.spawn;isSpecialWave=saved.special;zombiesKilledSinceLastBonus=saved.kills;
            for(var tag:saved.mobs)net.minecraft.world.entity.EntityType.loadEntityRecursive(tag,level,e->{level.getChunkAt(e.blockPosition());if(level.addFreshEntity(e))activeMobs.add(e.getUUID());return e;});
            saved.restoreQueue.run();NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(),new S2CSpecialWavePacket(isSpecialWave,isSpecialWave&&!EncounterRules.thief(level)));sendWaveUpdateToClients(level);return true;
        }
        boolean changed=forceSetWave(level,1);if(changed&&currentState==WaveState.DELAY_NEXT)spawnNextWave(level);sendWaveUpdateToClients(level);return changed;
    }
    private static void tickIntro(ServerLevel level){
        if(introExitTicks>=0){if(--introExitTicks<=0)finishIntroPhase(level);return;}
        introTicks++;
        activeMobs.removeIf(id->{var entity=level.getEntity(id);return entity==null||!entity.isAlive()||EncounterRules.roundIndependent(entity);});
        if(introTicks%20==0)for(UUID id:activeMobs)if(level.getEntity(id) instanceof Mob mob&&mob.getPersistentData().getBoolean("zr_intro_enemy"))IntroPhase.updateMob(mob,level,introTicks);
        if(--spawnTimer>0)return;
        spawnTimer=IntroPhase.interval(level,introTicks);
        int players=(int)level.players().stream().filter(p->p.isAlive()&&!p.isCreative()&&!p.isSpectator()).count();
        int cap=Math.min(IntroPhase.cap(level,introTicks),WorldConfig.get(level).getMaxActiveMobsPerPlayer())*players;
        if(players==0||activeMobs.size()>=cap)return;
        var settings=IntroPhase.settings(level);
        boolean hound=introTicks>=Math.max(0,settings.getInt("houndStartSeconds"))*20L&&level.random.nextDouble()<Math.max(0,Math.min(1,settings.getDouble("houndChance")));
        var type=hound?UniversalSpawnerSystem.SpawnerMobType.HELLHOUND:UniversalSpawnerSystem.SpawnerMobType.ZOMBIE;
        var origin=IntroPhase.spawner(level,type);
        if(origin.isEmpty()&&hound){hound=false;origin=IntroPhase.spawner(level,UniversalSpawnerSystem.SpawnerMobType.ZOMBIE);}
        if(origin.isEmpty())return;
        Mob mob=hound?ZombieroolModEntities.HELLHOUND.get().create(level):ZombieroolModEntities.ZOMBIE.get().create(level);
        if(mob==null)return;
        IntroPhase.updateMob(mob,level,introTicks);
        if(mob instanceof ZombieEntity zombie){zombie.assignRolledSkins("zombie");zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(IntroPhase.speed(level,introTicks));}
        if(mob instanceof HellhoundEntity dog)dog.assignRolledSkins("hellhound");
        Vec3 pos=Vec3.atBottomCenterOf(origin.get());mob.moveTo(pos.x,pos.y,pos.z,level.random.nextFloat()*360,0);mob.getPersistentData().putBoolean("zr_intro_enemy",true);SpawnRedistribution.track(mob,level,origin.get());
        if(level.addFreshEntity(mob))activeMobs.add(mob.getUUID());
    }
    public static boolean isServerGameRunning() { return gameRunning; }
    public static boolean isCountedEnemy(Mob mob){return mob.isAlive()&&!EncounterRules.roundIndependent(mob)&&activeMobs.contains(mob.getUUID());}
    public static boolean forceSetWave(ServerLevel level, int targetWave) {
        if (targetWave < 1) throw new IllegalArgumentException("Wave must be at least 1");
        me.cryo.zombierool.block.system.MusicVolume.resetAll(level);
        if (!gameRunning) {
            currentWave = 0;
            currentState = WaveState.OFF;
            sendWaveUpdateToClients(level);
            ZrNetwork.broadcast(level, Component.translatable("message.zombierool.wave.start_required").withStyle(ChatFormatting.RED));
            return false;
        }
        WorldConfig config = WorldConfig.get(level);
        clearAllActiveMobs(level);
        int prevWave = currentWave;
        currentWave = targetWave - 1;forcedSpecialNumberReserved=false;
        zombiesToKill = 0;
        specialWaveKilled.set(0);
        specialWaveTotal.set(0);
        specialSpawnedCount.set(0);
        hellhoundsCurrentlyActive.set(0);
        zombiesKilledSinceLastBonus = 0;
        respawnAllSpectatorPlayers(level);
        boolean nextIsSpecial = config.isSpecialWavesEnabled() && targetWave >= config.getSpecialWaveStart() && (targetWave - config.getSpecialWaveStart()) % config.getSpecialWaveInterval() == 0;
        if (nextIsSpecial && !(SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND)||EncounterRules.thief(level)&&SpawnerRegistry.hasActiveSpawnerOfType(level,UniversalSpawnerSystem.SpawnerMobType.ZOMBIE))) {
            nextIsSpecial = false;
        }
        if (nextIsSpecial) {
            currentWave=targetWave;forcedSpecialNumberReserved=true;triggerWaveChange(level,prevWave,targetWave);sendWaveUpdateToClients(level);
            isSpecialWave = true;
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(true,!EncounterRules.thief(level)));
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                PlayerVoiceManager.playSpecialStart(player, level);
            }
            ZrNetwork.playGlobalSound(new ResourceLocation("zombierool", EncounterRules.thief(level)?"thief_round":"fetch_me_their_souls"));
            currentState = WaveState.DELAY_SPECIAL_SPAWN;
            stateTimer = 160;
        } else {
            isSpecialWave = false;
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(false));
            triggerWaveChange(level, prevWave, targetWave);
            currentState = WaveState.DELAY_NEXT;
            stateTimer = 10;
        }
        return true;
    }
    private static int calculateSpawnInterval(int wave) {
        if (wave == 1) return 120;
        if (wave == 2) return 100;
        if (wave == 3) return 80;
        if (wave == 4) return 60;
        double delay = 50.0 * Math.pow(0.85, wave - 5);
        return Math.max(10, (int) delay);
    }
    public static void recalculateSpawnInterval(ServerLevel level) {
        WorldConfig config = WorldConfig.get(level);
        currentSpawnInterval = calculateSpawnInterval(currentWave);
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
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !gameRunning || isPausedByPlayer) return;
        matchElapsedTicks++;
        ServerLevel level = event.getServer().overworld();
        if(introExitTicks<0)for(ServerLevel world:event.getServer().getAllLevels())SpawnRedistribution.tick(world);
        activeMobs.removeIf(id->{var e=level.getEntity(id);return e==null||!e.isAlive()||EncounterRules.roundIndependent(e);});
        if(!isIntroPhase())BACKGROUND_AUDIO.tick(level,currentWave,isSpecialWave,activeMobs.size());
        if((currentState==WaveState.SPAWNING_NORMAL||currentState==WaveState.WAITING_CLEAR)&&!isSpecialWave&&mobsToSpawnInThisWave<=0){
            activeMobs.removeIf(id->{var entity=level.getEntity(id);return entity==null||!entity.isAlive()||EncounterRules.roundIndependent(entity);});
            if(activeMobs.isEmpty()&&!SpawnRedistribution.pending(level)&&!SpecialEnemySpawns.livingBoss(level)){zombiesToKill=0;LuaScriptManager.callEvent("OnAllZombiesDead");triggerNormalWaveEnd(level);return;}
        }
        switch (currentState) {
            case INTRO:
                tickIntro(level);
                break;
            case DELAY_FIRST:
            case DELAY_NEXT:
                if (--stateTimer <= 0) spawnNextWave(level);
                break;
            case DELAY_SPECIAL_SOUND:
                if (--stateTimer <= 0) {
                    isSpecialWave = true;
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(true,!EncounterRules.thief(level)));
                    for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                        PlayerVoiceManager.playSpecialStart(player, level);
                    }
                    ZrNetwork.playGlobalSound(new ResourceLocation("zombierool", EncounterRules.thief(level)?"thief_round":"fetch_me_their_souls"));
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
                    if (activeMobs.size() < maxActive * level.getServer().getPlayerList().getPlayers().size()) {
                        spawnOneRegularMob(level);
                        spawnTimer = SpawnRedistribution.pending(level)?Math.max(2,currentSpawnInterval/2):currentSpawnInterval;
                    } else {
                        spawnTimer = 10;
                    }
                }
                if (mobsToSpawnInThisWave <= 0) currentState = WaveState.WAITING_CLEAR;
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
                    } else if (isSpecialWave && EncounterRules.special(e)) {
                        hasActiveEnemies = true;
                    } else if (!isSpecialWave && (e instanceof ZombieEntity || e instanceof CrawlerEntity || EncounterRules.special(e) || e instanceof me.cryo.zombierool.entity.SpecialEnemyEntity encounter&&encounter.kind().equals("geist"))) {
                        hasActiveEnemies = true;
                    }
                }
                if (!hasActiveEnemies && !SpawnRedistribution.pending(level) && !SpecialEnemySpawns.livingBoss(level)) {
                    if (isSpecialWave) triggerSpecialWaveEnd(level, null);
                    else {
                        LuaScriptManager.callEvent("OnAllZombiesDead");
                        triggerNormalWaveEnd(level);
                    }
                }
                break;
        }
    }
    private static void giveStarterIfMissing(ServerPlayer player, WorldConfig config) {
        if (player.isSpectator()) return;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (me.cryo.zombierool.core.system.WeaponFacade.isWeapon(player.getInventory().getItem(slot))) return;
        }
        ItemStack starter = me.cryo.zombierool.core.system.WeaponFacade.createStarterStack(player, config);
        if (starter.isEmpty()) return;
        if (!player.getInventory().add(starter)) player.drop(starter, false);
    }
    public static boolean placeOnAssignedSpawner(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = PLAYER_RESPAWN_POINTS.get(player.getUUID());
        if (pos == null) {
            for (BlockPos candidate : WorldConfig.get(level).getPlayerSpawnerPositions()) {
                if (level.getBlockEntity(candidate) instanceof UniversalSpawnerSystem.UniversalSpawnerBlockEntity ube
                        && ube.getMobType() == UniversalSpawnerSystem.SpawnerMobType.PLAYER && ube.isActive(level)) {
                    pos = candidate.immutable();
                    PLAYER_RESPAWN_POINTS.put(player.getUUID(), pos);
                    break;
                }
            }
        }
        if (pos == null) return false;
        player.setRespawnPosition(level.dimension(), pos, player.getYRot(), true, false);
        player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
        return true;
    }
    public static void respawnAllSpectatorPlayers(ServerLevel level) {
        String penalty = WorldConfig.get(level).getDeathPenalty();
        if ("spectator".equalsIgnoreCase(penalty) || "kick".equalsIgnoreCase(penalty)) return;
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR) {
                p.setGameMode(GameType.ADVENTURE);
                p.setHealth(p.getMaxHealth());
                p.removeAllEffects();
                if (placeOnAssignedSpawner(p)) {
                    me.cryo.zombierool.gameplay.PointManager.modifyScore(p, 500);
                    ItemStack pistol = me.cryo.zombierool.core.system.WeaponFacade.createStarterStack(p, WorldConfig.get(level));
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
        MrChiefEasterEgg.onWaveStart(level,currentWave);
        EquipmentSystem.onWaveStart(level,currentWave);
        me.cryo.zombierool.block.system.MachineDeliverySystem.onWaveStart(level,currentWave);
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
        var crawlerSettings=me.cryo.zombierool.config.SpecialEnemyConfig.get(level);
        int minC = Math.max(crawlerSettings.getDouble("crawlerFractionMin")>0?1:0, (int) (count * crawlerSettings.getDouble("crawlerFractionMin")));
        int maxC = Math.max(minC, (int) (count * crawlerSettings.getDouble("crawlerFractionMax")));
        int numCrawlers = rand.nextInt(maxC - minC + 1) + minC;
        if (!SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.CRAWLER)) {
            numCrawlers = 0;
        }
        currentWaveSpawnOrder = new ArrayList<>(count);
        for (int j = 0; j < numCrawlers; j++) currentWaveSpawnOrder.add(true);
        for (int j = 0; j < count - numCrawlers; j++) currentWaveSpawnOrder.add(false);
        Collections.shuffle(currentWaveSpawnOrder, rand);
        currentSpawnInterval = calculateSpawnInterval(currentWave);
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
        if (!EncounterRules.thief(level) && config.isHellhoundsInNormalWaves() && currentWave >= config.getHellhoundsInNormalWavesStart()) {
            if (rand.nextDouble() < 0.05) {
                Optional<BlockPos> opt = getRandomSpawnerPosition(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND);
                if (opt.isPresent()) {
                    HellhoundEntity h = ZombieroolModEntities.HELLHOUND.get().create(level);
                    if (h != null) {
                        WaveMobScaling.applyHealth(h, currentWave, level);
                        h.assignRolledSkins("hellhound");
                        Vec3 v = Vec3.atBottomCenterOf(opt.get());
                        h.moveTo(v.x, v.y, v.z, rand.nextFloat() * 360F, 0);
                        SpawnRedistribution.track(h,level,opt.get());
                        level.addFreshEntity(h);
                        activeMobs.add(h.getUUID());
                        mobsToSpawnInThisWave--;
                        currentWaveSpawnOrder.remove(idx);
                        return;
                    }
                }
            }
        }
        final boolean wantedCrawler = currentWaveSpawnOrder.get(idx);
        boolean hasCrawlerSpawners = SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.CRAWLER);
        UniversalSpawnerSystem.SpawnerMobType spawnType = (wantedCrawler && hasCrawlerSpawners) ? UniversalSpawnerSystem.SpawnerMobType.CRAWLER : UniversalSpawnerSystem.SpawnerMobType.ZOMBIE;
        Optional<BlockPos> optPos = getRandomSpawnerPosition(level, spawnType);
        if (optPos.isEmpty()) return;
        Vec3 v = Vec3.atBottomCenterOf(optPos.get());
        Mob mob;
        if (spawnType == UniversalSpawnerSystem.SpawnerMobType.CRAWLER) {
            if(EncounterRules.geist(level)){var g=ZombieroolModEntities.GEIST.get().create(level);if(g==null)return;WaveMobScaling.applyHealth(g,currentWave,level);g.getPersistentData().putBoolean("zr_special_spawner",true);mob=g;}else{
            CrawlerEntity c = new CrawlerEntity(ZombieroolModEntities.CRAWLER.get(), level);
            WaveMobScaling.applyHealth(c, currentWave, level);
            c.setCustomSkin(me.cryo.zombierool.core.manager.DynamicResourceManager.getRandomSkin("crawler"));
            mob = c;}
        } else {
            ZombieEntity z = new ZombieEntity(ZombieroolModEntities.ZOMBIE.get(), level);
            WaveMobScaling.applyHealth(z, currentWave, level);
            WaveMobScaling.applySpeed(z, currentWave, rand, level);
            z.assignRolledSkins("zombie");
            mob = z;
        }
        mob.moveTo(v.x, v.y, v.z, rand.nextFloat() * 360F, 0);
        SpawnRedistribution.track(mob,level,optPos.get());
        if(!level.addFreshEntity(mob))return;
        activeMobs.add(mob.getUUID());
        currentWaveSpawnOrder.remove(idx);
        mobsToSpawnInThisWave--;
    }
    private static void spawnSpecialWave(ServerLevel level) {
        if(!forcedSpecialNumberReserved)currentWave++;forcedSpecialNumberReserved=false;
        sendWaveUpdateToClients(level);
        List<ServerPlayer> players = level.getServer().getPlayerList().getPlayers();
        if (players.isEmpty()) {
            endGame(level, Component.translatable("message.zombierool.game_over.no_players"));
            return;
        }
        respawnAllSpectatorPlayers(level);
        LuaScriptManager.callEvent("OnWaveStart", currentWave);
        MrChiefEasterEgg.onWaveStart(level,currentWave);
        EquipmentSystem.onWaveStart(level,currentWave);
        me.cryo.zombierool.block.system.MachineDeliverySystem.onWaveStart(level,currentWave);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.getCapability(me.cryo.zombierool.core.capability.ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
                if (cap.getLethalCount() < 5) {
                    cap.setLethalCount(cap.getLethalCount() + 1);
                    cap.sync(p);
                }
            });
        }
        specialWaveTotal.set(EncounterRules.thief(level)?1:(4 + 2 * players.size()) * 2);
        specialWaveKilled.set(0);
        hellhoundsCurrentlyActive.set(0);
        specialSpawnedCount.set(0);
        currentState = WaveState.SPAWNING_SPECIAL;
        spawnTimer = 100;
    }
    private static void spawnOneHellhound(ServerLevel level) {
        if(EncounterRules.thief(level)){spawnThief(level);return;}
        Optional<BlockPos> opt = getRandomSpawnerPosition(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND);
        if (opt.isEmpty()) {
            return;
        }
        HellhoundEntity h = ZombieroolModEntities.HELLHOUND.get().create(level);
        if (h == null) return;
        WaveMobScaling.applyHealth(h, currentWave, level);
        h.assignRolledSkins("hellhound");
        Vec3 v = Vec3.atBottomCenterOf(opt.get());
        h.moveTo(v.x, v.y, v.z, level.getRandom().nextFloat() * 360f, 0);
        SpawnRedistribution.track(h,level,opt.get());
        level.addFreshEntity(h);
        activeMobs.add(h.getUUID());
        specialSpawnedCount.incrementAndGet();
        hellhoundsCurrentlyActive.incrementAndGet();
    }
    private static void spawnThief(ServerLevel level){
        var pos=getRandomSpawnerPosition(level,UniversalSpawnerSystem.SpawnerMobType.HELLHOUND);
        if(pos.isEmpty())pos=getRandomSpawnerPosition(level,UniversalSpawnerSystem.SpawnerMobType.ZOMBIE);
        if(pos.isEmpty())return;var thief=ZombieroolModEntities.PENTAGON_THIEF.get().create(level);if(thief==null)return;
        double health=350*Math.max(1,level.players().size())*(1+currentWave*.1);thief.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(health);thief.setHealth((float)health);
        thief.getPersistentData().putBoolean("zr_special_spawner",true);var at=Vec3.atBottomCenterOf(pos.get());thief.moveTo(at.x,at.y,at.z,0,0);if(!level.addFreshEntity(thief))return;activeMobs.add(thief.getUUID());specialSpawnedCount.incrementAndGet();hellhoundsCurrentlyActive.incrementAndGet();
    }
    public static void registerMapActor(net.minecraft.world.entity.Mob actor) {
        if(!MapActors.countsForWave(actor))return;
        if(activeMobs.add(actor.getUUID()))zombiesToKill++;
    }
    public static boolean detachForRedistribution(Mob mob){
        if(!activeMobs.remove(mob.getUUID()))return false;
        if(EncounterRules.special(mob) && isSpecialWave)hellhoundsCurrentlyActive.updateAndGet(n->Math.max(0,n-1));
        return true;
    }
    public static void attachAfterRedistribution(Mob mob){
        if(EncounterRules.roundIndependent(mob))return;
        activeMobs.add(mob.getUUID());
        if(EncounterRules.special(mob) && isSpecialWave)hellhoundsCurrentlyActive.incrementAndGet();
    }
    public static boolean hasRedistributionCapacity(ServerLevel level){
        int players=Math.max(1,level.getServer().getPlayerList().getPlayers().size());
        if(isSpecialWave)return hellhoundsCurrentlyActive.get()<6*players;
        int max=WorldConfig.get(level).getMaxActiveMobsPerPlayer();

        return activeMobs.size()<max*players;
    }
    public static void recycleMob(Mob mob, ServerLevel level) {
        UUID id = mob.getUUID();
        if (activeMobs.contains(id)) {
            activeMobs.remove(id);
            PlayerStatsManager.cleanupEntity(mob.getId());
            mob.discard();
            if(isIntroPhase())return;
            if (EncounterRules.special(mob)) {
                hellhoundsCurrentlyActive.decrementAndGet();
                if (isSpecialWave) specialSpawnedCount.decrementAndGet();
            } else {
                mobsToSpawnInThisWave++;
                currentWaveSpawnOrder.add(mob instanceof CrawlerEntity);
            }
        }
    }
    public static void clearDroppedItems(ServerLevel level) {
        DroppedItemCleanup.clear(level);
    }
    private static void clearAllActiveMobs(ServerLevel level) {
        SpawnRedistribution.reset();
        SpecialEnemySpawns.reset(level);
        MiniBossTransitions.reset(level);BrutusMachineLocks.reset(level);
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
        if (event.getEntity() instanceof Mob mob && (mob instanceof ZombieEntity || mob instanceof CrawlerEntity || mob instanceof HellhoundEntity || mob instanceof me.cryo.zombierool.entity.SpecialEnemyEntity encounter&&encounter.isEncounter()&&!encounter.isBoss())) {
            onMobDeath(mob, (ServerLevel) mob.level());
        }
    }
    public static void onMobDeath(Mob mob, ServerLevel level) {
        if (!gameRunning || isPausedByPlayer) return;
        UUID id = mob.getUUID();
        if (!activeMobs.contains(id) || !processedDeaths.add(id)) return;
        activeMobs.remove(id);
        if(isIntroPhase()){if(!(mob instanceof HellhoundEntity))zombiesKilledSinceLastBonus++;return;}
        if (EncounterRules.special(mob)) {
            hellhoundsCurrentlyActive.decrementAndGet();
            if (isSpecialWave) {
                if (specialWaveKilled.incrementAndGet() >= specialWaveTotal.get() && activeMobs.stream().noneMatch(uuid -> EncounterRules.special(level.getEntity(uuid)))) {
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
        if(SpawnRedistribution.pending(level))return;
        if(SpecialEnemySpawns.livingBoss(level))return;
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
        // Survivors keep their spawner alive cap and finish naturally between waves.
        int nextWave = currentWave + 1;
        WorldConfig config = WorldConfig.get(level);
        boolean isNextWaveSpecial = config.isSpecialWavesEnabled() && nextWave >= config.getSpecialWaveStart() && (nextWave - config.getSpecialWaveStart()) % config.getSpecialWaveInterval() == 0;
        if (isNextWaveSpecial && !(SpawnerRegistry.hasActiveSpawnerOfType(level, UniversalSpawnerSystem.SpawnerMobType.HELLHOUND)||EncounterRules.thief(level)&&SpawnerRegistry.hasActiveSpawnerOfType(level,UniversalSpawnerSystem.SpawnerMobType.ZOMBIE))) {
            isNextWaveSpecial = false;
        }
        triggerWaveChange(level, currentWave, nextWave);
        isSpecialWave = false;
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSpecialWavePacket(false));
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            if (isGameRunning() && !p.isCreative() && !p.isSpectator() && !areCheatsUsed()) {
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
        if (zombiesToKill <= 0 && !SpawnRedistribution.pending(level) && !SpecialEnemySpawns.livingBoss(level) && activeMobs.stream().noneMatch(uuid -> level.getEntity(uuid) instanceof Mob)) {
            LuaScriptManager.callEvent("OnAllZombiesDead");
            triggerNormalWaveEnd(level);
        }
    }
    public static synchronized void endGame(ServerLevel level, Component message) {
        endMatch(level, message, true);
    }
    public static synchronized void restartLevel(ServerLevel level) {
        if (gameRunning) {
            endMatch(level, null, false);
        }
        level.getServer().getWorldData().setDifficulty(Difficulty.NORMAL);
        startGame(level);
    }
    private static void endMatch(ServerLevel level, Component message, boolean recap) {
        long transitionStart=System.nanoTime();
        suspendedRound=null;introExitTicks=-1;forcedSpecialNumberReserved=false;
        for(var player:level.getServer().getPlayerList().getPlayers()) LunarEquipment.revoke(player);
        isPausedByPlayer = false;
        me.cryo.zombierool.block.system.MusicVolume.resetAll(level);
        if (!gameRunning) {
            currentWave = 0; currentState = WaveState.OFF;
            PlayerDownManager.resetAll();
            for(var player:level.players())if(player.isSpectator()){
                player.setGameMode(GameType.ADVENTURE);player.setHealth(player.getMaxHealth());
            }
            sendWaveUpdateToClients(level);
            return;
        }
        currentState = WaveState.OFF;
        gameRunning = false;
        PlayerDownManager.resetAll();
        MapActors.end(level);
        isSpecialWave = false;
        cheatsUsed = false;
        UNLOCKED_CHANNELS.clear();
        LOCKED_CHANNELS.clear();
        PickableManager.reset(level);
        me.cryo.zombierool.block.system.BlindBuySystem.resetAllCabinets(level);
        me.cryo.zombierool.handlers.ServerInteractionHandler.resetAll();
        MatchConfigSnapshot.restore(level);
        me.cryo.zombierool.block.system.MusicVolume.resetAll(level);
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
        clearDroppedItems(level);
        me.cryo.zombierool.core.manager.GoreManager.clearWorld(level);
        me.cryo.zombierool.block.system.MapDeviceSystem.reset(level);
        me.cryo.zombierool.block.system.MachineDeliverySystem.reset(level);
        me.cryo.zombierool.block.system.TeleporterSystem.reset(level);
        for(var world:level.getServer().getAllLevels())MapAtmosphere.clearPlayerOverrides(world);
        TraversalMotion.reset(level);
        SpawnChannelActions.reset();
        me.cryo.zombierool.block.system.SoulChestSystem.reset(level);
        MrChiefEasterEgg.reset(level);
        int wavesSurvived = currentWave;
        if (recap) {
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
        }
        currentWave = 0;
        sendWaveUpdateToClients(level);
        level.getServer().getWorldData().setDifficulty(Difficulty.PEACEFUL);
        MapPower.resetSignals(level);
        for (var buildLevel : level.getServer().getAllLevels()) BuildableSystem.get(buildLevel).reset(buildLevel);
        MatchWorldJournal.restore(level);
        QuestBlockSnapshots.get(level).restoreAll(level);
        MapPower.refresh(level);
        resetPerkEgg(level);
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            MatchPlayerReset.applyEndLoadout(p, level);
            p.inventoryMenu.broadcastChanges();
            if (!p.isCreative()) {
                p.setGameMode(GameType.ADVENTURE);
                p.setHealth(p.getMaxHealth());
                placeOnAssignedSpawner(p);
            }
        }
        org.slf4j.LoggerFactory.getLogger("ZombieRool").info("Match end: restoration/reset={} ms",(System.nanoTime()-transitionStart)/1_000_000);
        PlayerStatsManager.syncAll(level);
        if (message != null) {
            ZrNetwork.broadcast(level, message);
        }
    }
    public static int getZombiesKilledSinceLastBonus(ServerLevel level) { return zombiesKilledSinceLastBonus; }
    public static void resetZombiesKilledSinceLastBonus(ServerLevel level) { zombiesKilledSinceLastBonus = 0; }
    public static void sendWaveUpdateToClients(ServerLevel level) { ZrNetwork.toAll(new S2CWaveUpdatePacket(currentWave)); }
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        if (event.getServer().isSingleplayer()) setGamePaused(true);
        boolean matchWasRunning = gameRunning;
        ServerLevel overworld = event.getServer().overworld();
        for(var sl:event.getServer().getAllLevels()){SpecialEnemySpawns.reset(sl);MiniBossTransitions.reset(sl);BrutusMachineLocks.reset(sl);me.cryo.zombierool.block.system.MachineDeliverySystem.reset(sl);}
        if (overworld != null) {
            me.cryo.zombierool.block.system.BlindBuySystem.resetAllCabinets(overworld);
            if(matchWasRunning)MapPower.resetSignals(overworld);
            for (var buildLevel : event.getServer().getAllLevels()) BuildableSystem.get(buildLevel).reset(buildLevel);
            MatchWorldJournal.restore(overworld);
            QuestBlockSnapshots.get(overworld).restoreAll(overworld);
            MatchConfigSnapshot.restore(overworld);
            if (matchWasRunning) {
                MapPower.refresh(overworld);
                resetPerkEgg(overworld);
            }
        }
        gameRunning = false;
        currentState = WaveState.OFF;
        isSpecialWave = false;
        cheatsUsed = false;
        currentWave = 0;
        List<Entity> toRemove = new ArrayList<>();
        for (ServerLevel sl : event.getServer().getAllLevels()) {
            for (Entity e : sl.getAllEntities()) {
                // Deliberately placed display mobs belong to the saved gallery, not a match.
                if (e instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity display
                        && display.getPersistentData().getBoolean("zr_skin_gallery")
                        && display.isNoAi() && display.isInvulnerable()) continue;
                if (e instanceof ZombieEntity ||
                    e instanceof CrawlerEntity ||
                    EncounterRules.special(e) ||
                    e instanceof me.cryo.zombierool.entity.WhiteKnightEntity ||
                    e instanceof me.cryo.zombierool.entity.BloodDecalEntity ||
                    e instanceof me.cryo.zombierool.entity.GorePieceEntity ||
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
