package me.cryo.zombierool.player;
import me.cryo.zombierool.WaveManager;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem.PlayerStatsManager;
import me.cryo.zombierool.core.system.WeaponFacade;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.S2CPlayerDownPacket;
import me.cryo.zombierool.network.S2CPlayerPosePacket;
import me.cryo.zombierool.network.S2CPlayerRevivePacket;
import me.cryo.zombierool.scripting.LuaScriptManager;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.util.PlayerVoiceManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.ChatFormatting;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDownManager {
    private static final long BASE_DOWN_DURATION_TICKS = 60 * 20; 
    public static final long BASE_REVIVE_DURATION_TICKS = 6 * 20;  
    private static final long SOLO_QUICK_REVIVE_DURATION_TICKS = 33 * 20; 
    private static final int POINTS_LOST_ON_DOWN_PERCENTAGE = 20; 
    private static final int POINTS_GAINED_ON_REVIVE = 100; 
    private static final ResourceLocation ANN_DOWN_SOUND_RES = new ResourceLocation("zombierool", "ann_down");
    private static SoundEvent getVineBoomSound() {
        return ZombieroolModSounds.VINE_BOOM.get(); 
    }
    private static final Random STATIC_RANDOM = new Random(); 
    private static final Map<UUID, Long> playersDown = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> reviverToDownPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> reviveStartTicks = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> soloQuickRevivePlayers = new ConcurrentHashMap<>();
    private static final Map<Long, List<BlockPos>> scheduledVineBooms = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ItemStack>> savedPlayerInventories = new ConcurrentHashMap<>();
    private static final Map<UUID, ItemStack> lastKnownHandgun = new ConcurrentHashMap<>();
    public static void resetAll() {
        playersDown.clear();
        reviverToDownPlayer.clear();
        reviveStartTicks.clear();
        soloQuickRevivePlayers.clear();
        scheduledVineBooms.clear();
        savedPlayerInventories.clear();
        lastKnownHandgun.clear();
    }
    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            playersDown.remove(player.getUUID());
            soloQuickRevivePlayers.remove(player.getUUID());
            savedPlayerInventories.remove(player.getUUID());
            lastKnownHandgun.remove(player.getUUID());
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerDownPacket(false, player.getUUID()));
            checkAndEndGame(player.serverLevel());
        }
    }
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            if (player.getPersistentData().getBoolean("zr_real_death")) {
                player.getInventory().clearContent(); 
                player.getPersistentData().remove("zr_real_death");
                handlePlayerPermanentDeath(player, level);
                return; 
            }
            if (!WaveManager.isGameRunning()) {
                handlePlayerPermanentDeath(player, level); 
                return;
            }
            if (playersDown.containsKey(player.getUUID())) {
                player.getPersistentData().remove("zr_real_death");
                player.getInventory().clearContent();
                handlePlayerPermanentDeath(player, level);
                return;
            }
            ItemStack currentMainHandItem = player.getMainHandItem();
            if (WeaponFacade.isHandgun(currentMainHandItem)) {
                lastKnownHandgun.put(player.getUUID(), currentMainHandItem.copy()); 
            }
            long otherActivePlayersCount = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.isAlive())
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .filter(p -> !isPlayerDown(p.getUUID()))
                .filter(p -> !p.getUUID().equals(player.getUUID()))
                .count();
            boolean isSoloPlayer = level.getServer().getPlayerList().getPlayers().size() == 1;
            if (isSoloPlayer) {
                if (player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get())) {
                    event.setCanceled(true); 
                    player.setHealth(1.0f); 
                    player.removeEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get());
                    player.sendSystemMessage(Component.translatable("message.zombierool.down.quick_revive_saved"));
                    markPlayerDown(player, level);
                    soloQuickRevivePlayers.put(player.getUUID(), level.getGameTime()); 
                    broadcast(level, Component.translatable("message.zombierool.down.announce_downed", player.getName()));
                    playGlobalSound(level, ANN_DOWN_SOUND_RES, player.blockPosition());
                    saveAndEquipHandgunForDownedPlayer(player);
                } else {
                    player.getInventory().clearContent();
                    handlePlayerPermanentDeath(player, level);
                    return; 
                }
            } else {
                if (otherActivePlayersCount > 0) {
                    event.setCanceled(true); 
                    player.setHealth(1.0f); 
                    markPlayerDown(player, level); 
                    PlayerStatsManager.recordDeath(player);
                    broadcast(level, Component.translatable("message.zombierool.down.announce_downed", player.getName()));
                    playGlobalSound(level, ANN_DOWN_SOUND_RES, player.blockPosition());
                    int currentPoints = PointManager.getScore(player);
                    int rawPointsLost = (int) (currentPoints * (POINTS_LOST_ON_DOWN_PERCENTAGE / 100.0));
                    int pointsLost = (int) (Math.round(rawPointsLost / 10.0) * 10);
                    if (rawPointsLost > 0 && pointsLost == 0) {
                        pointsLost = 10;
                    }
                    if (pointsLost > currentPoints) {
                        pointsLost = currentPoints;
                    }
                    PointManager.modifyScore(player, -pointsLost);
                    player.sendSystemMessage(Component.translatable("message.zombierool.down.points_lost", pointsLost));
                    saveAndEquipHandgunForDownedPlayer(player);
                } else {
                    player.getInventory().clearContent();
                    handlePlayerPermanentDeath(player, level);
                    return; 
                }
            }
        }
    }
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();
            if (WaveManager.isGameRunning()) {
                String penalty = WorldConfig.get(level).getDeathPenalty();
                if ("kick".equalsIgnoreCase(penalty)) {
                    player.connection.disconnect(Component.literal("GAME OVER: Vous êtes mort de façon permanente."));
                    return;
                } else {
                    player.setGameMode(GameType.SPECTATOR);
                    player.sendSystemMessage(Component.translatable("message.zombierool.down.spectator_mode_until_next_wave"));
                }
                player.getInventory().clearContent();
                ResourceLocation starterItemId = WorldConfig.get(level).getStarterItem();
                ItemStack pistol = WeaponFacade.createWeaponStack(starterItemId.toString(), false, player);
                if (pistol.isEmpty()) {
                    net.minecraft.world.item.Item starterItem = ForgeRegistries.ITEMS.getValue(starterItemId);
                    if (starterItem == null) starterItem = net.minecraft.world.item.Items.WOODEN_SWORD;
                    pistol = new ItemStack(starterItem);
                }
                if (!player.getInventory().add(pistol.copy())) {
                    player.drop(pistol.copy(), false);
                }
            } else {
                player.setGameMode(GameType.ADVENTURE);
                player.setHealth(player.getMaxHealth());
                PointManager.setScore(player, 500);
            }
        }
    }
    private static void markPlayerDown(ServerPlayer player, ServerLevel level) {
        playersDown.put(player.getUUID(), level.getGameTime());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerDownPacket(true, player.getUUID()));
        List<MobEffectInstance> effectsToRemove = player.getActiveEffects().stream()
            .filter(effect -> effect.getEffect() != MobEffects.GLOWING)
            .collect(Collectors.toList());
        for (MobEffectInstance effect : effectsToRemove) {
            player.removeEffect(effect.getEffect());
        }
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (BASE_DOWN_DURATION_TICKS + 40), 0, false, false, true));
        int slownessLevel = WorldConfig.get(level).isAllowDownMovement() ? 3 : 250;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (BASE_DOWN_DURATION_TICKS + 40), slownessLevel, false, false, false));
        player.setPose(Pose.SWIMMING);
        player.refreshDimensions();
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerPosePacket(player.getUUID(), Pose.SWIMMING));
        LuaScriptManager.callEvent("OnPlayerDown", player.getUUID().toString());
    }
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel level = event.getServer().overworld();
            long currentTime = level.getGameTime();
            if (level.getServer().getPlayerList().getPlayers().isEmpty() && WaveManager.isGameRunning()) {
                WaveManager.endGame(level, Component.translatable("message.zombierool.game_over.host_left"));
                return;
            }
            SoundEvent vineBoomSound = getVineBoomSound();
            Iterator<Map.Entry<Long, List<BlockPos>>> vineBoomIterator = scheduledVineBooms.entrySet().iterator();
            while (vineBoomIterator.hasNext()) {
                Map.Entry<Long, List<BlockPos>> entry = vineBoomIterator.next();
                if (currentTime >= entry.getKey()) {
                    if (vineBoomSound != null) {
                        for (BlockPos pos : entry.getValue()) {
                            level.getServer().getPlayerList().getPlayers().forEach(p ->
                                level.playSound(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, vineBoomSound, SoundSource.MASTER, 1.0F, 1.0F)
                            );
                        }
                    }
                    vineBoomIterator.remove();
                }
            }
            Iterator<Map.Entry<UUID, Long>> playersDownIterator = playersDown.entrySet().iterator();
            while (playersDownIterator.hasNext()) {
                Map.Entry<UUID, Long> entry = playersDownIterator.next();
                UUID uuid = entry.getKey();
                long downTime = entry.getValue();
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(uuid);
                if (player != null) {
                    long remainingDownTime = BASE_DOWN_DURATION_TICKS - (currentTime - downTime);
                    if (remainingDownTime > 0) {
                        int slownessLevel = WorldConfig.get(level).isAllowDownMovement() ? 3 : 250;
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) remainingDownTime + 20, 0, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) remainingDownTime + 20, slownessLevel, false, false, false));
                    }
                    boolean isSoloPlayer = level.getServer().getPlayerList().getPlayers().size() == 1;
                    if (soloQuickRevivePlayers.containsKey(uuid) && isSoloPlayer) {
                        long soloReviveStartTime = soloQuickRevivePlayers.get(uuid);
                        if (currentTime - soloReviveStartTime < SOLO_QUICK_REVIVE_DURATION_TICKS) {
                            player.sendSystemMessage(Component.translatable("message.zombierool.down.reviving_progress"), true);
                        } else {
                            completeSoloQuickRevive(player, level);
                            soloQuickRevivePlayers.remove(uuid);
                            playersDownIterator.remove();
                        }
                    } else if (currentTime - downTime >= BASE_DOWN_DURATION_TICKS) {
                        playersDownIterator.remove(); 
                        player.getPersistentData().putBoolean("zr_real_death", true);
                        player.hurt(level.damageSources().generic(), Float.MAX_VALUE); 
                    }
                } else {
                    playersDownIterator.remove();
                    soloQuickRevivePlayers.remove(uuid);
                    checkAndEndGame(level); 
                }
            }
            Iterator<Map.Entry<UUID, UUID>> reviverIterator = reviverToDownPlayer.entrySet().iterator();
            while (reviverIterator.hasNext()) {
                Map.Entry<UUID, UUID> entry = reviverIterator.next();
                UUID reviverUUID = entry.getKey();
                UUID downPlayerUUID = entry.getValue();
                ServerPlayer reviver = level.getServer().getPlayerList().getPlayer(reviverUUID);
                ServerPlayer downPlayer = level.getServer().getPlayerList().getPlayer(downPlayerUUID);
                if (reviver != null && downPlayer != null) {
                    Long reviveStartTime = reviveStartTicks.get(reviverUUID);
                    if (reviveStartTime == null) {
                        reviverIterator.remove();
                        reviveStartTicks.remove(reviverUUID);
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new S2CPlayerRevivePacket(downPlayerUUID, -1, 0));
                        continue;
                    }
                    long effectiveDuration = BASE_REVIVE_DURATION_TICKS;
                    if (reviver.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get())) {
                        effectiveDuration /= 2;
                    }
                    if (currentTime - reviveStartTime >= effectiveDuration) {
                        completeRevive(downPlayer, reviver, level);
                        reviverIterator.remove(); 
                        reviveStartTicks.remove(reviverUUID);
                    }
                } else {
                    reviverIterator.remove();
                    reviveStartTicks.remove(reviverUUID);
                    if (reviver != null) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new S2CPlayerRevivePacket(downPlayerUUID, -1, 0));
                    }
                }
            }
            checkAndEndGame(level); 
        }
    }
    private static void checkAndEndGame(ServerLevel level) {
        if (!WaveManager.isGameRunning()) {
            return;
        }
        long totalPlayers = level.getServer().getPlayerList().getPlayers().size();
        long trulyActivePlayers = level.getServer().getPlayerList().getPlayers().stream()
            .filter(p -> p.isAlive()) 
            .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
            .filter(p -> !isPlayerDown(p.getUUID()) || soloQuickRevivePlayers.containsKey(p.getUUID()))
            .count();
        if (totalPlayers > 0 && trulyActivePlayers == 0) {
            WaveManager.endGame(level, Component.translatable("message.zombierool.game_over.all_down"));
        }
    }
    private static void completeSoloQuickRevive(ServerPlayer player, ServerLevel level) {
        playersDown.remove(player.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerDownPacket(false, player.getUUID()));
        restorePlayerInventory(player); 
        resetPlayerState(player); 
        player.sendSystemMessage(Component.translatable("message.zombierool.down.self_revived"));
        PlayerVoiceManager.playWasRevived(player, level);
        LuaScriptManager.callEvent("OnPlayerRevive", player.getUUID().toString(), player.getUUID().toString());
    }
    public static void handlePlayerPermanentDeath(ServerPlayer player, ServerLevel level) {
        playersDown.remove(player.getUUID());
        soloQuickRevivePlayers.remove(player.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerDownPacket(false, player.getUUID()));
        savedPlayerInventories.remove(player.getUUID());
        lastKnownHandgun.remove(player.getUUID()); 
        player.removeAllEffects();
        player.getPersistentData().remove("zr_has_bowie_knife");
        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            cap.setLethalType(WorldConfig.get(level).getStartingLethal());
            cap.setLethalCount(5);
            cap.sync(player);
        });
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerPosePacket(player.getUUID(), Pose.STANDING));
        if (WaveManager.isGameRunning()) {
            broadcast(level, Component.translatable("message.zombierool.down.announce_permanent_death", player.getName()).withStyle(ChatFormatting.RED));
        }
        LuaScriptManager.callEvent("OnPlayerDeath", player.getUUID().toString());
        checkAndEndGame(level);
    }
    private static void resetPlayerState(ServerPlayer player) {
        player.removeAllEffects(); 
        player.setHealth(player.getMaxHealth());
        player.setPose(Pose.STANDING);
        player.refreshDimensions();
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerPosePacket(player.getUUID(), Pose.STANDING));
    }
    public static void startRevive(ServerPlayer reviver, ServerPlayer downPlayer, ServerLevel level) {
        if (!playersDown.containsKey(downPlayer.getUUID())) {
            reviver.sendSystemMessage(Component.translatable("message.zombierool.down.target_not_down"));
            return;
        }
        boolean isSoloPlayer = level.getServer().getPlayerList().getPlayers().size() == 1;
        if (soloQuickRevivePlayers.containsKey(downPlayer.getUUID()) && !isSoloPlayer) {
            reviver.sendSystemMessage(Component.translatable("message.zombierool.down.target_solo_reviving"));
            return;
        }
        if (reviverToDownPlayer.containsKey(reviver.getUUID())) {
            return;
        }
        if (isPlayerDown(reviver.getUUID())) {
            reviver.sendSystemMessage(Component.translatable("message.zombierool.down.you_are_down_cannot_revive"));
            return;
        }
        if (!(reviver.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || reviver.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)) {
            reviver.sendSystemMessage(Component.translatable("message.zombierool.down.not_survival_adventure"));
            return;
        }
        reviverToDownPlayer.put(reviver.getUUID(), downPlayer.getUUID());
        reviveStartTicks.put(reviver.getUUID(), level.getGameTime());
        broadcast(level, Component.translatable("message.zombierool.down.announce_revive_attempt", reviver.getName(), downPlayer.getName()));
        long effectiveDuration = BASE_REVIVE_DURATION_TICKS;
        if (reviver.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get())) {
            effectiveDuration /= 2;
        }
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver),
                new S2CPlayerRevivePacket(downPlayer.getUUID(), level.getGameTime(), effectiveDuration));
        PlayerVoiceManager.playIsReviving(reviver, level);
    }
    public static void cancelRevive(ServerLevel level, UUID reviverUUID) {
        ServerPlayer reviver = level.getServer().getPlayerList().getPlayer(reviverUUID);
        UUID downPlayerUUID = reviverToDownPlayer.get(reviverUUID); 
        ServerPlayer downPlayer = null;
        if (downPlayerUUID != null) {
            downPlayer = level.getServer().getPlayerList().getPlayer(downPlayerUUID);
        }
        if (reviver != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new S2CPlayerRevivePacket(downPlayerUUID, -1, 0));
            reviver.sendSystemMessage(Component.translatable("message.zombierool.down.revive_canceled_reviver_moved"));
        }
        if (downPlayer != null) {
            broadcast(level, Component.translatable("message.zombierool.down.revive_canceled_target_invalid"));
        }
        reviverToDownPlayer.remove(reviverUUID);
        reviveStartTicks.remove(reviverUUID);
    }
    private static void completeRevive(ServerPlayer downPlayer, ServerPlayer reviver, ServerLevel level) {
        playersDown.remove(downPlayer.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerDownPacket(false, downPlayer.getUUID()));
        restorePlayerInventory(downPlayer); 
        resetPlayerState(downPlayer); 
        broadcast(level, Component.translatable("message.zombierool.down.announce_revived", downPlayer.getName(), reviver.getName()));
        PointManager.modifyScore(reviver, POINTS_GAINED_ON_REVIVE);
        reviver.sendSystemMessage(Component.translatable("message.zombierool.down.points_gained", downPlayer.getName().getString(), POINTS_GAINED_ON_REVIVE).withStyle(ChatFormatting.GREEN));
        PlayerVoiceManager.playWasRevived(downPlayer, level);
        PlayerVoiceManager.playHasRevived(reviver, level);
        LuaScriptManager.callEvent("OnPlayerRevive", reviver.getUUID().toString(), downPlayer.getUUID().toString());
    }
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            ItemStack currentMainHandItem = player.getMainHandItem();
            if (WeaponFacade.isHandgun(currentMainHandItem)) {
                lastKnownHandgun.put(player.getUUID(), currentMainHandItem.copy()); 
            }
            if (playersDown.containsKey(player.getUUID())) {
                int slownessLevel = WorldConfig.get((ServerLevel) player.level()).isAllowDownMovement() ? 3 : 250;
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, slownessLevel, false, false, false));
                player.setPose(Pose.SWIMMING);
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CPlayerPosePacket(player.getUUID(), Pose.SWIMMING));
            }
        }
    }
    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;
        if (event.getEntity() instanceof ServerPlayer player) {
            if (playersDown.containsKey(player.getUUID())) {
                event.setCanceled(true); 
            }
        }
    }
    private static void playGlobalSound(ServerLevel level, ResourceLocation soundRes, BlockPos soundOriginPos) {
        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(soundRes);
        if (sound == null) {
            return;
        }
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            level.playSound(null, p.blockPosition(), sound, SoundSource.MASTER, 1.0f, 1.0f);
        }
        if (soundRes.equals(ANN_DOWN_SOUND_RES) && STATIC_RANDOM.nextInt(20) == 0) {
            long targetTick = level.getGameTime() + (2 * 20);
            scheduledVineBooms.computeIfAbsent(targetTick, k -> new ArrayList<>()).add(soundOriginPos.immutable());
        }
    }
    private static void broadcast(ServerLevel level, Component msg) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(msg);
        }
    }
    public static boolean isPlayerDown(UUID playerUUID) {
        return playersDown.containsKey(playerUUID);
    }
    public static boolean isPlayerReviving(UUID reviverUUID) {
        return reviverToDownPlayer.containsKey(reviverUUID);
    }
    public static UUID getRevivingPlayer(UUID reviverUUID) {
        return reviverToDownPlayer.get(reviverUUID);
    }
    public static long getReviveStartTime(UUID reviverUUID) {
        return reviveStartTicks.getOrDefault(reviverUUID, 0L);
    }
    private static void saveAndEquipHandgunForDownedPlayer(ServerPlayer player) {
        List<ItemStack> currentInventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            currentInventory.add(player.getInventory().getItem(i).copy()); 
        }
        savedPlayerInventories.put(player.getUUID(), currentInventory);
        player.getInventory().clearContent();
        ItemStack downedHandgun = WeaponFacade.createWeaponStack("m1911", false, player);
        if (downedHandgun.isEmpty()) downedHandgun = new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD);
        ItemStack handgunBase = lastKnownHandgun.get(player.getUUID());
        if (handgunBase != null && WeaponFacade.isHandgun(handgunBase)) {
            me.cryo.zombierool.core.system.WeaponSystem.Definition def = WeaponFacade.getDefinition(handgunBase);
            if (def != null) {
                downedHandgun = WeaponFacade.createWeaponStack(def.id, false, player);
            }
        }
        downedHandgun.setCount(1); 
        WeaponFacade.setAmmo(downedHandgun, WeaponFacade.getMaxAmmo(downedHandgun));
        WeaponFacade.setReserve(downedHandgun, WeaponFacade.getMaxAmmo(downedHandgun));
        player.getInventory().setItem(1, downedHandgun);
        player.getInventory().selected = 1; 
    }
    private static void restorePlayerInventory(ServerPlayer player) {
        player.getInventory().clearContent();
        List<ItemStack> savedInventory = savedPlayerInventories.remove(player.getUUID());
        if (savedInventory != null) {
            for (ItemStack stack : savedInventory) {
                if (!stack.isEmpty()) {
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
            }
        }
    }
}