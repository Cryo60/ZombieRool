package me.cryo.zombierool.player;

import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.PlayerDownPacket;
import me.cryo.zombierool.network.PlayerRevivePacket;
import me.cryo.zombierool.PointManager;
import me.cryo.zombierool.WaveManager; 
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.init.ZombieroolModMobEffects;
import me.cryo.zombierool.init.ZombieroolModSounds;
import me.cryo.zombierool.core.system.WeaponFacade; 
import me.cryo.zombierool.core.system.WeaponSystem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; 
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Pose;
import me.cryo.zombierool.network.PlayerPosePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Random;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerDownManager {

    private static boolean isEnglishClient(ServerPlayer player) {
        return true; 
    }

    private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

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

    private static final Component MSG_REVIVING_PROGRESS = Component.literal("§eEn train de se réanimer...");
    private static final Component MSG_SELF_REVIVED = Component.literal("§aVous vous êtes réanimé !");
    private static final Component MSG_TARGET_NOT_DOWN = Component.literal("§cLe joueur ciblé n'est pas tombé au combat.");
    private static final Component MSG_TARGET_SOLO_REVIVING = Component.literal("§cCe joueur est en train de se réanimer seul !");
    private static final Component MSG_YOU_ARE_DOWN_CANNOT_REVIVE = Component.literal("§cVous êtes tombé au combat et ne pouvez pas réanimer !");
    private static final Component MSG_NOT_SURVIVAL_ADVENTURE = Component.literal("§cVous devez être en mode Survie ou Aventure pour réanimer !");
    private static final Component MSG_SPECTATOR_MODE_UNTIL_NEXT_WAVE = Component.literal("§7Vous êtes en mode spectateur jusqu'à la prochaine vague.");
    private static final Component MSG_GAME_OVER_ALL_DOWN = Component.literal("§4GAME OVER ! Tous les joueurs sont tombés au combat.");
    private static final Component MSG_GAME_OVER_HOST_LEFT = Component.literal("§4GAME OVER ! L'hôte a quitté le serveur.");
    private static final Component MSG_REVIVE_CANCELED_REVIVER_MOVED = Component.literal("La réanimation a été annulée car le réanimateur s'est éloigné ou a relâché la touche.");
    private static final Component MSG_REVIVE_CANCELED_TARGET_INVALID = Component.literal("La réanimation a été annulée car le joueur ciblé n'est plus tombé ou a quitté.");

    private static final String ANNOUNCE_DOWNED_SUFFIX = " est tombé au combat !";
    private static final String ANNOUNCE_PERMANENT_DEATH_SUFFIX = " est mort pour de bon !";
    private static final String ANNOUNCE_REVIVE_ATTEMPT_MIDDLE = " tente de réanimer ";
    private static final String ANNOUNCE_REVIVED_MIDDLE = " a été réanimé par ";
    private static final String ANNOUNCE_REVIVED_SUFFIX = " !";

    private static final Map<UUID, Long> playersDown = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> reviverToDownPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> reviveStartTicks = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> soloQuickRevivePlayers = new ConcurrentHashMap<>();
    private static final Map<Long, List<BlockPos>> scheduledVineBooms = new ConcurrentHashMap<>();
    private static final Map<UUID, List<ItemStack>> savedPlayerInventories = new ConcurrentHashMap<>();
    private static final Map<UUID, ItemStack> lastKnownHandgun = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();

            if (!WaveManager.isGameRunning()) {
                handlePlayerPermanentDeath(player, level); 
                checkAndEndGame(level); 
                return;
            }

            ItemStack currentMainHandItem = player.getMainHandItem();
            if (WeaponFacade.isHandgun(currentMainHandItem)) {
                lastKnownHandgun.put(player.getUUID(), currentMainHandItem.copy()); 
            }

            long activePlayersCount = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .filter(p -> !isPlayerDown(p.getUUID()))
                .count();

            boolean isSoloPlayer = level.getServer().getPlayerList().getPlayers().size() == 1;

            if (isSoloPlayer && player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get())) {
                event.setCanceled(true); 
                player.setHealth(1.0f); 
                player.removeEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get());
                player.sendSystemMessage(getTranslatedComponent(player, "§aQuick Revive vous a sauvé ! Votre perk a été consommé.", "§aQuick Revive saved you! Your perk has been consumed."));
                
                markPlayerDown(player, level);
                soloQuickRevivePlayers.put(player.getUUID(), level.getGameTime()); 
                broadcast(level, getTranslatedComponent(player, player.getName().getString() + ANNOUNCE_DOWNED_SUFFIX, player.getName().getString() + " is down!").getString());
                playGlobalSound(level, ANN_DOWN_SOUND_RES, player.blockPosition());
                
                saveAndEquipHandgunForDownedPlayer(player);
                return;
            }

            if (!playersDown.containsKey(player.getUUID())) {
                long totalPlayersOnServer = level.getServer().getPlayerList().getPlayers().size();
                long currentlyDownPlayers = playersDown.size();

                if (activePlayersCount <= 1 && totalPlayersOnServer - currentlyDownPlayers <= 1) {
                    handlePlayerPermanentDeath(player, level); 
                    checkAndEndGame(level); 
                } else {
                    event.setCanceled(true); 
                    player.setHealth(1.0f); 
                    markPlayerDown(player, level); 
                    
                    broadcast(level, getTranslatedComponent(player, player.getName().getString() + ANNOUNCE_DOWNED_SUFFIX, player.getName().getString() + " is down!").getString());
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
                    player.sendSystemMessage(getTranslatedComponent(player, "Vous avez perdu " + pointsLost + " points en tombant au combat !", "You lost " + pointsLost + " points when you went down!"));

                    saveAndEquipHandgunForDownedPlayer(player);
                }
            } else {
                handlePlayerPermanentDeath(player, level);
                checkAndEndGame(level); 
            }
        }
    }

    private static void markPlayerDown(ServerPlayer player, ServerLevel level) {
        playersDown.put(player.getUUID(), level.getGameTime());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerDownPacket(true, player.getUUID()));

        List<MobEffectInstance> effectsToRemove = player.getActiveEffects().stream()
            .filter(effect -> effect.getEffect() != MobEffects.GLOWING)
            .collect(Collectors.toList());

        for (MobEffectInstance effect : effectsToRemove) {
            player.removeEffect(effect.getEffect());
        }

        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (BASE_DOWN_DURATION_TICKS + 40), 0, false, false, true));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (BASE_DOWN_DURATION_TICKS + 40), 250, false, false, false));

        player.setPose(Pose.SWIMMING);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerPosePacket(player.getUUID(), Pose.SWIMMING));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel level = event.getServer().overworld();
            long currentTime = level.getGameTime();

            if (level.getServer().getPlayerList().getPlayers().isEmpty() && WaveManager.isGameRunning()) {
                WaveManager.endGame(level, getTranslatedComponent(null, MSG_GAME_OVER_HOST_LEFT.getString(), "GAME OVER! Host left the server."));
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
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) remainingDownTime + 20, 0, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) remainingDownTime + 20, 250, false, false, false));
                    }

                    boolean isSoloPlayer = level.getServer().getPlayerList().getPlayers().size() == 1;

                    if (soloQuickRevivePlayers.containsKey(uuid) && isSoloPlayer) {
                        long soloReviveStartTime = soloQuickRevivePlayers.get(uuid);
                        if (currentTime - soloReviveStartTime < SOLO_QUICK_REVIVE_DURATION_TICKS) {
                            player.sendSystemMessage(getTranslatedComponent(player, MSG_REVIVING_PROGRESS.getString(), "Reviving..."), true);
                        } else {
                            completeSoloQuickRevive(player, level);
                            soloQuickRevivePlayers.remove(uuid);
                            playersDownIterator.remove();
                        }
                    } else if (currentTime - downTime >= BASE_DOWN_DURATION_TICKS) {
                        handlePlayerPermanentDeath(player, level);
                        playersDownIterator.remove();
                        checkAndEndGame(level); 
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
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PlayerRevivePacket(downPlayerUUID, -1, 0));
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
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PlayerRevivePacket(downPlayerUUID, -1, 0));
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
            .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
            .filter(p -> !isPlayerDown(p.getUUID()) || (isPlayerDown(p.getUUID()) && soloQuickRevivePlayers.containsKey(p.getUUID())))
            .count();

        if (totalPlayers > 0 && trulyActivePlayers == 0) {
            boolean allAreSpectatorOrDownAndNotSoloReviving = level.getServer().getPlayerList().getPlayers().stream()
                .allMatch(p -> p.gameMode.getGameModeForPlayer() == GameType.SPECTATOR ||
                               (isPlayerDown(p.getUUID()) && !soloQuickRevivePlayers.containsKey(p.getUUID())));

            if (allAreSpectatorOrDownAndNotSoloReviving) {
                WaveManager.endGame(level, getTranslatedComponent(null, MSG_GAME_OVER_ALL_DOWN.getString(), "GAME OVER! All players are down."));
            }
        }
    }

    private static void completeSoloQuickRevive(ServerPlayer player, ServerLevel level) {
        playersDown.remove(player.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerDownPacket(false, player.getUUID()));

        restorePlayerInventory(player); 
        resetPlayerState(player); 

        player.sendSystemMessage(getTranslatedComponent(player, MSG_SELF_REVIVED.getString(), "You revived yourself!"));
    }

    public static void handlePlayerPermanentDeath(ServerPlayer player, ServerLevel level) {
        playersDown.remove(player.getUUID());
        soloQuickRevivePlayers.remove(player.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerDownPacket(false, player.getUUID()));

        savedPlayerInventories.remove(player.getUUID());
        lastKnownHandgun.remove(player.getUUID()); 

        resetPlayerState(player); 

        if (WaveManager.isGameRunning()) {
            broadcast(level, getTranslatedComponent(player, "§c" + player.getName().getString() + ANNOUNCE_PERMANENT_DEATH_SUFFIX, "§c" + player.getName().getString() + " died permanently!").getString());

            String penalty = WorldConfig.get(level).getDeathPenalty();
            if ("kick".equalsIgnoreCase(penalty)) {
                player.connection.disconnect(Component.literal("GAME OVER: Vous êtes mort de façon permanente."));
            } else if ("spectator".equalsIgnoreCase(penalty)) {
                player.setHealth(0);
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(getTranslatedComponent(player, "§7Vous êtes en mode spectateur pour le reste de la partie.", "§7You are a spectator for the rest of the game."));
            } else {
                player.setHealth(0);
                player.setGameMode(GameType.SPECTATOR);
                player.sendSystemMessage(getTranslatedComponent(player, MSG_SPECTATOR_MODE_UNTIL_NEXT_WAVE.getString(), "You are in spectator mode until the next wave."));
            }

            PointManager.modifyScore(player, 500);
            player.getInventory().clearContent();
            
            ItemStack starterItemStack = WeaponFacade.createWeaponStack("m1911", false);
            if (starterItemStack.isEmpty()) starterItemStack = new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD);

            if (!player.getInventory().add(starterItemStack.copy())) {
                player.drop(starterItemStack.copy(), false);
            }
        } else {
            player.setHealth(0);
            player.getInventory().clearContent();
        }

        checkAndEndGame(level);
    }

    private static void resetPlayerState(ServerPlayer player) {
        player.removeAllEffects(); 
        player.setHealth(player.getMaxHealth());
        player.setPose(Pose.STANDING);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerPosePacket(player.getUUID(), Pose.STANDING));
    }

    public static void startRevive(ServerPlayer reviver, ServerPlayer downPlayer, ServerLevel level) {
        if (!playersDown.containsKey(downPlayer.getUUID())) {
            reviver.sendSystemMessage(getTranslatedComponent(reviver, MSG_TARGET_NOT_DOWN.getString(), "The targeted player is not down."));
            return;
        }

        boolean isSoloPlayer = level.getServer().getPlayerList().getPlayers().size() == 1;
        if (soloQuickRevivePlayers.containsKey(downPlayer.getUUID()) && !isSoloPlayer) {
            reviver.sendSystemMessage(getTranslatedComponent(reviver, MSG_TARGET_SOLO_REVIVING.getString(), "This player is self-reviving!"));
            return;
        }

        if (reviverToDownPlayer.containsKey(reviver.getUUID())) {
            return;
        }

        if (isPlayerDown(reviver.getUUID())) {
            reviver.sendSystemMessage(getTranslatedComponent(reviver, MSG_YOU_ARE_DOWN_CANNOT_REVIVE.getString(), "You are down and cannot revive!"));
            return;
        }

        if (!(reviver.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || reviver.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)) {
            reviver.sendSystemMessage(getTranslatedComponent(reviver, MSG_NOT_SURVIVAL_ADVENTURE.getString(), "You must be in Survival or Adventure mode to revive!"));
            return;
        }

        reviverToDownPlayer.put(reviver.getUUID(), downPlayer.getUUID());
        reviveStartTicks.put(reviver.getUUID(), level.getGameTime());

        broadcast(level, getTranslatedComponent(reviver, reviver.getName().getString() + ANNOUNCE_REVIVE_ATTEMPT_MIDDLE + downPlayer.getName().getString() + "...", reviver.getName().getString() + " is attempting to revive " + downPlayer.getName().getString() + "...").getString());

        long effectiveDuration = BASE_REVIVE_DURATION_TICKS;
        if (reviver.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get())) {
            effectiveDuration /= 2;
        }
        
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver),
            new PlayerRevivePacket(downPlayer.getUUID(), level.getGameTime(), effectiveDuration));
    }

    public static void cancelRevive(ServerLevel level, UUID reviverUUID) {
        ServerPlayer reviver = level.getServer().getPlayerList().getPlayer(reviverUUID);
        UUID downPlayerUUID = reviverToDownPlayer.get(reviverUUID); 
        ServerPlayer downPlayer = null;
        if (downPlayerUUID != null) {
            downPlayer = level.getServer().getPlayerList().getPlayer(downPlayerUUID);
        }

        if (reviver != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PlayerRevivePacket(downPlayerUUID, -1, 0));
            reviver.sendSystemMessage(getTranslatedComponent(reviver, MSG_REVIVE_CANCELED_REVIVER_MOVED.getString(), "Revive canceled because the reviver moved away or released the key."));
        }
        
        if (downPlayer != null) {
            broadcast(level, getTranslatedComponent(null, MSG_REVIVE_CANCELED_TARGET_INVALID.getString(), "Revive canceled because the target player is no longer down or has left.").getString());
        }

        reviverToDownPlayer.remove(reviverUUID);
        reviveStartTicks.remove(reviverUUID);
    }

    private static void completeRevive(ServerPlayer downPlayer, ServerPlayer reviver, ServerLevel level) {
        playersDown.remove(downPlayer.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerDownPacket(false, downPlayer.getUUID()));

        restorePlayerInventory(downPlayer); 
        resetPlayerState(downPlayer); 

        broadcast(level, getTranslatedComponent(reviver, downPlayer.getName().getString() + ANNOUNCE_REVIVED_MIDDLE + reviver.getName().getString() + ANNOUNCE_REVIVED_SUFFIX, downPlayer.getName().getString() + " has been revived by " + reviver.getName().getString() + "!").getString());

        PointManager.modifyScore(reviver, POINTS_GAINED_ON_REVIVE);
        reviver.sendSystemMessage(getTranslatedComponent(reviver, "§aVous avez réanimé " + downPlayer.getName().getString() + " et gagné " + POINTS_GAINED_ON_REVIVE + " points !", "§aYou revived " + downPlayer.getName().getString() + " and gained " + POINTS_GAINED_ON_REVIVE + " points!"));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            ItemStack currentMainHandItem = player.getMainHandItem();
            if (WeaponFacade.isHandgun(currentMainHandItem)) {
                lastKnownHandgun.put(player.getUUID(), currentMainHandItem.copy()); 
            }

            if (playersDown.containsKey(player.getUUID())) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 250, false, false, false));
                player.setPose(Pose.SWIMMING);
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerPosePacket(player.getUUID(), Pose.SWIMMING));
                player.setDeltaMovement(player.getDeltaMovement().x, -2.0, player.getDeltaMovement().z); 
                player.fallDistance = 0f; 
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

    private static void broadcast(ServerLevel level, String msg) {
        for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(msg));
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
        
        ItemStack downedHandgun = WeaponFacade.createWeaponStack("m1911", false);
        if (downedHandgun.isEmpty()) downedHandgun = new ItemStack(net.minecraft.world.item.Items.WOODEN_SWORD);

        ItemStack handgunBase = lastKnownHandgun.get(player.getUUID());
        if (handgunBase != null && WeaponFacade.isHandgun(handgunBase)) {
            WeaponSystem.Definition def = WeaponFacade.getDefinition(handgunBase);
            if (def != null) {
                downedHandgun = WeaponFacade.createWeaponStack(def.id, false);
            }
        }
        
        downedHandgun.setCount(1); 
        WeaponFacade.setAmmo(downedHandgun, WeaponFacade.getMaxAmmo(downedHandgun));
        WeaponFacade.setReserve(downedHandgun, WeaponFacade.getMaxAmmo(downedHandgun));
        
        player.getInventory().setItem(0, downedHandgun);
        player.getInventory().selected = 0; 
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