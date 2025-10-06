package net.mcreator.zombierool.player;

import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.PlayerDownPacket;
import net.mcreator.zombierool.network.PlayerRevivePacket;
import net.mcreator.zombierool.PointManager;
import net.mcreator.zombierool.WaveManager; // Assure que ceci détermine correctement si le jeu est actif
import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.init.ZombieroolModMobEffects;
import net.mcreator.zombierool.init.ZombieroolModItems;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.mcreator.zombierool.item.IHandgunWeapon; // Assurez-vous que le chemin est correct pour votre projet
import net.mcreator.zombierool.api.IReloadable; // Importez IReloadable pour gérer les munitions

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.Pose;
import net.mcreator.zombierool.network.PlayerPosePacket;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

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

    // Helper method to check if the client's language is English
    private static boolean isEnglishClient(ServerPlayer player) {
        // For server-side, we can't directly access client language settings.
        // This is a placeholder. In a real scenario, you'd need client-server sync
        // to pass the player's language preference to the server.
        // For this example, we'll assume English if a player context is available.
        return true; 
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(ServerPlayer player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage);
    }

    // --- Durées et Constantes ---
    private static final long BASE_DOWN_DURATION_TICKS = 60 * 20; // 1 minute
    public static final long BASE_REVIVE_DURATION_TICKS = 6 * 20;  // 6 seconds
    private static final long SOLO_QUICK_REVIVE_DURATION_TICKS = 33 * 20; // 33 seconds pour le Quick Revive solo
    private static final float REVIVE_DISTANCE_SQR = 2.25f; // Distance maximale au carré pour réanimer (1.5 blocs)
    private static final int POINTS_LOST_ON_DOWN_PERCENTAGE = 20; // 20% des points perdus
    private static final int POINTS_GAINED_ON_REVIVE = 100; // Points gagnés par le réanimateur

    // --- Sons et Annonces ---
    private static final ResourceLocation ANN_DOWN_SOUND_RES = new ResourceLocation("zombierool", "ann_down");
    private static SoundEvent getVineBoomSound() {
        return ZombieroolModSounds.VINE_BOOM.get(); // This will now safely get the sound event
    }

    private static final Random STATIC_RANDOM = new Random(); // Pour l'Easter Egg

    // Messages pour le joueur
    private static final Component MSG_QUICK_REVIVE_SAVED = Component.literal("§aQuick Revive vous a sauvé ! Votre perk a été consommé.");
    private static final Component MSG_REVIVING_PROGRESS = Component.literal("§eEn train de se réanimer...");
    private static final Component MSG_SELF_REVIVED = Component.literal("§aVous vous êtes réanimé !");
    private static final Component MSG_TOO_FAR_TO_REVIVE_PREFIX = Component.literal("§cVous êtes trop éloigné de ");
    private static final Component MSG_TARGET_NOT_DOWN = Component.literal("§cLe joueur ciblé n'est pas tombé au combat.");
    private static final Component MSG_TARGET_SOLO_REVIVING = Component.literal("§cCe joueur est en train de se réanimer seul !");
    private static final Component MSG_YOU_ARE_DOWN_CANNOT_REVIVE = Component.literal("§cVous êtes tombé au combat et ne pouvez pas réanimer !");
    private static final Component MSG_NOT_SURVIVAL_ADVENTURE = Component.literal("§cVous devez être en mode Survie ou Aventure pour réanimer !");
    private static final Component MSG_SPECTATOR_MODE_UNTIL_NEXT_WAVE = Component.literal("§7Vous êtes en mode spectateur jusqu'à la prochaine vague.");
    private static final Component MSG_GAME_OVER_ALL_DOWN = Component.literal("§4GAME OVER ! Tous les joueurs sont tombés au combat.");
    private static final Component MSG_GAME_OVER_HOST_LEFT = Component.literal("§4GAME OVER ! L'hôte a quitté le serveur.");

    // NOUVEAUX MESSAGES
    private static final Component MSG_REVIVE_CANCELED_REVIVER_MOVED = Component.literal("La réanimation a été annulée car le réanimateur s'est éloigné ou a relâché la touche.");
    private static final Component MSG_REVIVE_CANCELED_TARGET_INVALID = Component.literal("La réanimation a été annulée car le joueur ciblé n'est plus tombé ou a quitté.");


    // Annonces globales
    private static final String ANNOUNCE_DOWNED_SUFFIX = " est tombé au combat !";
    private static final String ANNOUNCE_PERMANENT_DEATH_SUFFIX = " est mort pour de bon !";
    private static final String ANNOUNCE_REVIVE_ATTEMPT_MIDDLE = " tente de réanimer ";
    private static final String ANNOUNCE_REVIVED_MIDDLE = " a été réanimé par ";
    private static final String ANNOUNCE_REVIVED_SUFFIX = " !";


    // --- États des joueurs (UUID -> temps de début ou joueur ciblé) ---
    private static final Map<UUID, Long> playersDown = new ConcurrentHashMap<>();
    private static final Map<UUID, UUID> reviverToDownPlayer = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> reviveStartTicks = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> soloQuickRevivePlayers = new ConcurrentHashMap<>();

    // --- Easter Egg : Boom de vigne programmé (UUID -> Liste de BlockPos) ---
    private static final Map<Long, List<BlockPos>> scheduledVineBooms = new ConcurrentHashMap<>();

    // NOUVEAU : Pour sauvegarder l'inventaire des joueurs en état "down"
    private static final Map<UUID, List<ItemStack>> savedPlayerInventories = new ConcurrentHashMap<>();
    // NOUVEAU : Pour stocker le dernier handgun du joueur
    private static final Map<UUID, ItemStack> lastKnownHandgun = new ConcurrentHashMap<>();


    // --- Événement de mort d'entité ---
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            ServerLevel level = player.serverLevel();

            // S'assurer que le jeu est en cours
            if (!WaveManager.isGameRunning()) {
                handlePlayerPermanentDeath(player, level); // Si le jeu n'est pas en cours, mort instantanée
                checkAndEndGame(level); // Vérifier si le jeu doit se terminer
                return;
            }

            // Mettre à jour le dernier handgun connu avant que le joueur ne soit potentiellement affecté
            // Assurez-vous que l'item principal n'est pas vide et est bien un IHandgunWeapon
            ItemStack currentMainHandItem = player.getMainHandItem();
            if (!currentMainHandItem.isEmpty() && currentMainHandItem.getItem() instanceof IHandgunWeapon) {
                lastKnownHandgun.put(player.getUUID(), currentMainHandItem.copy()); // Copie complète avec NBT
            }

            // Compter les joueurs qui sont "actifs" (survie/aventure et non déjà à terre)
            long activePlayersCount = level.getServer().getPlayerList().getPlayers().stream()
                .filter(p -> p.gameMode.getGameModeForPlayer() == GameType.SURVIVAL || p.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .filter(p -> !isPlayerDown(p.getUUID()))
                .count();

            // Logique pour Quick Revive en solo
            boolean isSoloPlayer = level.getServer().getPlayerList().getPlayers().size() == 1;

            if (isSoloPlayer && player.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get())) {
                event.setCanceled(true); // Annule l'événement de mort
                player.setHealth(1.0f); // Met la vie du joueur à 1 pour empêcher l'écran de mort
                player.removeEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get());
                player.sendSystemMessage(getTranslatedComponent(player, "§aQuick Revive vous a sauvé ! Votre perk a été consommé.", "§aQuick Revive saved you! Your perk has been consumed."));

                markPlayerDown(player, level);
                soloQuickRevivePlayers.put(player.getUUID(), level.getGameTime()); // Démarre le chrono de revive solo
                broadcast(level, getTranslatedComponent(player, player.getName().getString() + ANNOUNCE_DOWNED_SUFFIX, player.getName().getString() + " is down!").getString());
                playGlobalSound(level, ANN_DOWN_SOUND_RES, player.blockPosition());
                
                // Gérer l'inventaire du joueur tombé avec Quick Revive
                saveAndEquipHandgunForDownedPlayer(player);
                return;
            }

            // Logique d'état "downed" régulière si non déjà "down" ou si Quick Revive solo ne s'est pas déclenché
            if (!playersDown.containsKey(player.getUUID())) {
                long totalPlayersOnServer = level.getServer().getPlayerList().getPlayers().size();
                long currentlyDownPlayers = playersDown.size();

                // Si le joueur mourant est le dernier joueur non "down", ou si tous les joueurs sont déjà "downed"
                if (activePlayersCount <= 1 && totalPlayersOnServer - currentlyDownPlayers <= 1) {
                    handlePlayerPermanentDeath(player, level); // Mort permanente
                    checkAndEndGame(level); // Vérifier si le jeu doit se terminer
                } else {
                    event.setCanceled(true); // Empêche la mort réelle
                    player.setHealth(1.0f); // Met à 1 HP

                    markPlayerDown(player, level); // Marque comme "downed"
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
                    // NOUVEAU MESSAGE: Points perdus
                    player.sendSystemMessage(getTranslatedComponent(player, "Vous avez perdu " + pointsLost + " points en tombant au combat !", "You lost " + pointsLost + " points when you went down!"));
                    // Gérer l'inventaire du joueur tombé (co-op)
                    saveAndEquipHandgunForDownedPlayer(player);
                }
            } else {
                // Si le joueur est déjà "down" et subit à nouveau des dégâts mortels, il meurt de façon permanente
                handlePlayerPermanentDeath(player, level);
                checkAndEndGame(level); // Vérifier si le jeu doit se terminer après une mort permanente
            }
        }
    }

    private static void markPlayerDown(ServerPlayer player, ServerLevel level) {
        playersDown.put(player.getUUID(), level.getGameTime());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerDownPacket(true, player.getUUID()));

        // Retire tous les effets actifs sauf Glowing, puis ajoute Glowing
        List<MobEffectInstance> effectsToRemove = player.getActiveEffects().stream()
            .filter(effect -> effect.getEffect() != MobEffects.GLOWING)
            .collect(Collectors.toList());

        for (MobEffectInstance effect : effectsToRemove) {
            player.removeEffect(effect.getEffect());
        }

        // Ajoute l'effet GLOWING avec une durée légèrement supérieure à la durée maximale de down
        // Ceci assure que l'effet est visible pendant toute la durée du down et sera retiré explicitement
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (BASE_DOWN_DURATION_TICKS + 40), 0, false, false, true));

        // Applique un ralentissement extrême pour empêcher le mouvement et le saut
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) (BASE_DOWN_DURATION_TICKS + 40), 250, false, false, false));


        player.setPose(Pose.SWIMMING);
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerPosePacket(player.getUUID(), Pose.SWIMMING));
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ServerLevel level = event.getServer().overworld();
            long currentTime = level.getGameTime();

            // Vérifier si le jeu doit se terminer si le nombre de joueurs est de 0
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
                    } else {
                        System.err.println("[PlayerDownManager] Easter Egg: Sound 'vine_boom' not found during scheduled play (getter returned null).");
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
                    // S'assurer que l'effet GLOWING est maintenu pendant que le joueur est DOWNED
                    // La durée est ajustée dynamiquement ici.
                    long remainingDownTime = BASE_DOWN_DURATION_TICKS - (currentTime - downTime);
                    if (remainingDownTime > 0) {
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) remainingDownTime + 20, 0, false, false, true));
                        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, (int) remainingDownTime + 20, 250, false, false, false));
                    }


                    // Quick Revive auto-revive est UNIQUEMENT en solo
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
                        checkAndEndGame(level); // Vérifier si le jeu doit se terminer après une mort permanente
                    }
                } else {
                    playersDownIterator.remove();
                    soloQuickRevivePlayers.remove(uuid);
                    checkAndEndGame(level); // Vérifier si le jeu doit se terminer si un joueur "down" quitte
                }
            }

            // NOUVEAU: Logique pour compléter la réanimation coopérative
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
                        // Cela ne devrait pas arriver si les données sont cohérentes
                        reviverIterator.remove();
                        reviveStartTicks.remove(reviverUUID);
                        System.err.println("[PlayerDownManager] Erreur: reviver " + reviverUUID + " n'a pas de temps de début de réanimation. Annulation de la réanimation.");
                        // Envoie un packet d'annulation au reviver pour nettoyer le client
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PlayerRevivePacket(downPlayerUUID, -1, 0));
                        continue;
                    }

                    // Calculer la durée effective de réanimation (potentiellement réduite par Quick Revive du réanimateur)
                    long effectiveDuration = BASE_REVIVE_DURATION_TICKS;
                    if (reviver.hasEffect(ZombieroolModMobEffects.PERKS_EFFECT_QUICK_REVIVE.get())) {
                        effectiveDuration /= 2;
                    }

                    if (currentTime - reviveStartTime >= effectiveDuration) {
                        // Réanimation terminée
                        completeRevive(downPlayer, reviver, level);
                        reviverIterator.remove(); // Supprimer après la complétion
                        reviveStartTicks.remove(reviverUUID);
                    } else {
                        // Optionnel: Ajouter une vérification de distance ici pour plus de robustesse.
                        // Cependant, le KeyInputHandler côté client envoie déjà un packet d'annulation si
                        // le joueur s'éloigne ou lâche la touche.
                        // Pour l'instant, on fait confiance au client pour les annulations prématurées.
                    }
                } else {
                    // Si l'un des joueurs (réanimateur ou tombé) n'est plus valide (déconnecté, mort permanente, etc.)
                    System.out.println("[PlayerDownManager] Réanimation annulée: Joueur introuvable ou invalide (reviver: " + reviverUUID + ", downPlayer: " + downPlayerUUID + ").");
                    reviverIterator.remove();
                    reviveStartTicks.remove(reviverUUID);
                    // Informer le client du réanimateur que la réanimation est annulée si le reviver est encore connecté
                    if (reviver != null) {
                        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PlayerRevivePacket(downPlayerUUID, -1, 0));
                    }
                }
            }

            checkAndEndGame(level); // La vérification finale de fin de jeu
        }
    }

    // Nouvelle méthode pour vérifier la fin du jeu
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

        restorePlayerInventory(player); // Restaurer l'inventaire
        resetPlayerState(player); // Assure la suppression du GLOWING et réinitialise la pose/santé
        player.sendSystemMessage(getTranslatedComponent(player, MSG_SELF_REVIVED.getString(), "You revived yourself!"));
    }

    public static void handlePlayerPermanentDeath(ServerPlayer player, ServerLevel level) {
        playersDown.remove(player.getUUID());
        soloQuickRevivePlayers.remove(player.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerDownPacket(false, player.getUUID()));

        // Retirer l'inventaire sauvegardé si le joueur meurt de façon permanente
        savedPlayerInventories.remove(player.getUUID());
        lastKnownHandgun.remove(player.getUUID()); // Nettoyer aussi le dernier handgun connu

        resetPlayerState(player); // Supprime le GLOWING et réinitialise la pose/santé

        if (WaveManager.isGameRunning()) {
            broadcast(level, getTranslatedComponent(player, "§c" + player.getName().getString() + ANNOUNCE_PERMANENT_DEATH_SUFFIX, "§c" + player.getName().getString() + " died permanently!").getString());
            player.setHealth(0);

            player.setGameMode(GameType.SPECTATOR);
            player.sendSystemMessage(getTranslatedComponent(player, MSG_SPECTATOR_MODE_UNTIL_NEXT_WAVE.getString(), "You are in spectator mode until the next wave."));

            BlockPos respawnPos = WaveManager.PLAYER_RESPAWN_POINTS.get(player.getUUID());
            if (respawnPos != null) {
                player.teleportTo(level, respawnPos.getX() + 0.5, respawnPos.getY() + 1.0, respawnPos.getZ() + 0.5, player.getYRot(), player.getXRot());
            } else {
                System.err.println("[PlayerDownManager] Le joueur " + player.getName().getString() + " (UUID: " + player.getUUID() + ") est mort de façon permanente mais n'a pas de point de respawn défini. Il reste à sa position de mort.");
            }

            PointManager.modifyScore(player, 500);

            player.getInventory().clearContent();

            WorldConfig worldConfig = WorldConfig.get(level);
            ResourceLocation starterItemId = worldConfig.getStarterItem();
            Item starterItem = BuiltInRegistries.ITEM.get(starterItemId);

            if (starterItem == null) {
                starterItem = ZombieroolModItems.M_1911_WEAPON.get();
            }
            ItemStack starterItemStack = new ItemStack(starterItem);

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
        player.removeAllEffects(); // Supprime tous les effets, y compris GLOWING
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
        UUID downPlayerUUID = reviverToDownPlayer.get(reviverUUID); // Get the UUID of the player being revived
        ServerPlayer downPlayer = null;
        if (downPlayerUUID != null) {
            downPlayer = level.getServer().getPlayerList().getPlayer(downPlayerUUID);
        }

        if (reviver != null) {
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> reviver), new PlayerRevivePacket(downPlayerUUID, -1, 0));
            // Send message to reviver
            reviver.sendSystemMessage(getTranslatedComponent(reviver, MSG_REVIVE_CANCELED_REVIVER_MOVED.getString(), "Revive canceled because the reviver moved away or released the key."));
        }
        
        // Broadcast message to all players if the target player is not null
        if (downPlayer != null) {
            broadcast(level, getTranslatedComponent(null, MSG_REVIVE_CANCELED_TARGET_INVALID.getString(), "Revive canceled because the target player is no longer down or has left.").getString());
        }

        reviverToDownPlayer.remove(reviverUUID);
        reviveStartTicks.remove(reviverUUID);
    }

    private static void completeRevive(ServerPlayer downPlayer, ServerPlayer reviver, ServerLevel level) {
        playersDown.remove(downPlayer.getUUID());
        NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerDownPacket(false, downPlayer.getUUID()));

        restorePlayerInventory(downPlayer); // Restaurer l'inventaire
        resetPlayerState(downPlayer); // Assure la suppression du GLOWING et réinitialise la pose/santé
        broadcast(level, getTranslatedComponent(reviver, downPlayer.getName().getString() + ANNOUNCE_REVIVED_MIDDLE + reviver.getName().getString() + ANNOUNCE_REVIVED_SUFFIX, downPlayer.getName().getString() + " has been revived by " + reviver.getName().getString() + "!").getString());

        PointManager.modifyScore(reviver, POINTS_GAINED_ON_REVIVE);
        reviver.sendSystemMessage(getTranslatedComponent(reviver, "§aVous avez réanimé " + downPlayer.getName().getString() + " et gagné " + POINTS_GAINED_ON_REVIVE + " points !", "§aYou revived " + downPlayer.getName().getString() + " and gained " + POINTS_GAINED_ON_REVIVE + " points!"));
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START && event.player instanceof ServerPlayer player) {
            // Mettre à jour le dernier handgun connu régulièrement
            ItemStack currentMainHandItem = player.getMainHandItem();
            // S'assurer que l'item principal n'est pas vide et est bien un IHandgunWeapon
            if (!currentMainHandItem.isEmpty() && currentMainHandItem.getItem() instanceof IHandgunWeapon) {
                lastKnownHandgun.put(player.getUUID(), currentMainHandItem.copy()); // Copie complète avec NBT
            }

            if (playersDown.containsKey(player.getUUID())) {
                // S'assure que le ralentissement et la pose sont toujours appliqués tant que le joueur est down
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 2, 250, false, false, false));
                player.setPose(Pose.SWIMMING);
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new PlayerPosePacket(player.getUUID(), Pose.SWIMMING));
                
                // Empêche le saut en appliquant une force vers le bas plus significative.
                // Cela devrait contrecarrer toute tentative du client de générer une vélocité verticale positive.
                // Utiliser une valeur encore plus grande si -0.5 ne suffit pas.
                player.setDeltaMovement(player.getDeltaMovement().x, -2.0, player.getDeltaMovement().z); // Augmenté à -2.0
                player.fallDistance = 0f; // Réinitialise la distance de chute pour éviter les dégâts de chute inattendus
            }
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity().level().isClientSide()) return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (playersDown.containsKey(player.getUUID())) {
                event.setCanceled(true); // Empêche les dégâts supplémentaires aux joueurs "down"
            }
        }
    }

    private static void playGlobalSound(ServerLevel level, ResourceLocation soundRes, BlockPos soundOriginPos) {
        SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundRes);
        if (sound == null) {
            System.err.println("[PlayerDownManager] Son non trouvé: " + soundRes);
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

    /**
     * Sauvegarde l'inventaire du joueur et lui donne un handgun pour l'état down.
     * Le handgun sera le dernier connu s'il en tenait un, sinon le starter item défini,
     * et aura 2 chargeurs max de munitions.
     * @param player Le joueur à équiper.
     */
    private static void saveAndEquipHandgunForDownedPlayer(ServerPlayer player) {
        // Sauvegarder l'inventaire actuel du joueur (y compris le handgun original avec son état d'ammo)
        List<ItemStack> currentInventory = new ArrayList<>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            currentInventory.add(player.getInventory().getItem(i).copy()); // Copier les ItemStacks avec leurs NBT
        }
        savedPlayerInventories.put(player.getUUID(), currentInventory);

        // Vider l'inventaire du joueur
        player.getInventory().clearContent();

        // Déterminer l'item de fallback si aucun handgun n'est connu ou valide
        WorldConfig worldConfig = WorldConfig.get(player.serverLevel()); // Correction: .G() -> .get()
        ResourceLocation starterItemId = worldConfig.getStarterItem();
        Item defaultFallbackItem = BuiltInRegistries.ITEM.get(starterItemId);

        // Si le starter item n'existe pas ou n'est pas un IReloadable, utiliser le M1911 par défaut comme ultime fallback
        if (defaultFallbackItem == null || !(defaultFallbackItem instanceof IReloadable)) {
            defaultFallbackItem = ZombieroolModItems.M_1911_WEAPON.get();
        }

        // Déterminer le handgun de BASE à partir du dernier connu ou le starter item/M1911 par défaut
        ItemStack handgunBase = lastKnownHandgun.get(player.getUUID());
        if (handgunBase == null || !(handgunBase.getItem() instanceof IHandgunWeapon)) {
            handgunBase = new ItemStack(defaultFallbackItem);
        }

        // Créer une NOUVELLE instance du handgun pour l'état "down"
        ItemStack downedHandgun = handgunBase.copy();
        downedHandgun.setCount(1); // S'assurer qu'il n'y en a qu'un

        // Définir l'ammo pour l'état "down": 2 chargeurs max
        if (downedHandgun.getItem() instanceof IReloadable reloadableHandgun) {
            int maxAmmoPerMag = reloadableHandgun.getMaxAmmo();
            reloadableHandgun.setAmmo(downedHandgun, maxAmmoPerMag);
            reloadableHandgun.setReserve(downedHandgun, maxAmmoPerMag);
        } else {
            // Si le handgunBase n'est pas un IReloadable (devrait être géré par le check plus haut, mais au cas où)
            // Utiliser la capacité par défaut du M1911 pour le NBT des munitions
            IReloadable fallbackReloader = (IReloadable)ZombieroolModItems.M_1911_WEAPON.get(); // Assumer M1911 est IReloadable
            downedHandgun.getOrCreateTag().putInt("Ammo", fallbackReloader.getMaxAmmo());
            downedHandgun.getOrCreateTag().putInt("Reserve", fallbackReloader.getMaxAmmo());
        }

        // Donner le handgun directement dans le premier slot de l'inventaire (hotbar)
        player.getInventory().setItem(0, downedHandgun);
        player.getInventory().selected = 0; // S'assurer que le handgun est sélectionné
    }

    /**
     * Restaure l'inventaire du joueur après une réanimation.
     * @param player Le joueur dont l'inventaire doit être restauré.
     */
    private static void restorePlayerInventory(ServerPlayer player) {
        // Vider l'inventaire actuel (qui devrait contenir seulement le handgun de l'état down)
        player.getInventory().clearContent();

        List<ItemStack> savedInventory = savedPlayerInventories.remove(player.getUUID());
        if (savedInventory != null) {
            for (ItemStack stack : savedInventory) {
                if (!stack.isEmpty()) {
                    // Tenter d'ajouter l'item. Si l'inventaire est plein, le déposer.
                    if (!player.getInventory().add(stack)) {
                        player.drop(stack, false);
                    }
                }
            }
        }
        // lastKnownHandgun doit rester pour les prochaines fois où le joueur tombera,
        // il n'est donc pas supprimé ici.
        // La sélection de l'item sera gérée par le client ou par l'interaction normale du jeu.
    }
}
