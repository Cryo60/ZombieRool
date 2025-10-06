package net.mcreator.zombierool.util; // Créez ce package si ce n'est pas déjà fait

import net.mcreator.zombierool.init.ZombieroolModSounds; // Assurez-vous que vos sons sont définis ici
import net.minecraft.sounds.SoundEvent; // Import pour SoundEvent
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack; // Ajouté pour la vérification du type d'arme
import net.minecraft.world.level.Level;
import net.mcreator.zombierool.api.IReloadable; // Assurez-vous d'importer votre interface IReloadable
import net.mcreator.zombierool.WorldConfig; // NOUVEAU: Import pour WorldConfig
import net.minecraft.server.level.ServerLevel; // NOUVEAU: Import pour ServerLevel

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.Random;

/**
 * Gère la lecture des lignes vocales du joueur avec des logiques de chance et de temps de recharge.
 * Cette classe est conçue pour être appelée depuis n'importe quelle arme ou gestionnaire d'événements.
 */
public class PlayerVoiceManager {

    // Carte pour stocker le dernier temps de lecture du son de confirmation de kill pour chaque joueur
    private static final Map<UUID, Long> lastKillConfirmedPlayTime = new HashMap<>();
    
    // Temps de recharge en ticks (20 ticks = 1 seconde) pour éviter le spam du son de confirmation de kill
    private static final int KILL_CONFIRM_COOLDOWN_TICKS = 40; // 2 secondes de cooldown (originalement 40 ticks, soit 2s)
    
    // Chance en pourcentage de jouer le son de confirmation de kill
    private static final int KILL_CONFIRM_CHANCE_PERCENT = 45; // 45% de chance
    
    // Générateur de nombres aléatoires pour la chance
    private static final Random random = new Random();

    // NOUVEAU: Map pour suivre l'état de rechargement précédent de chaque joueur
    private static final Map<UUID, Boolean> wasReloadingLastTick = new HashMap<>();

    // NOUVEAU: Map pour stocker le dernier temps d'INITIATION de n'importe quelle ligne vocale (pour le cooldown global)
    private static final Map<UUID, Long> lastVoiceLineInitiationTime = new HashMap<>();
    
    // NOUVEAU: Temps de recharge global en ticks pour toutes les lignes vocales (0.5 seconde)
    private static final int VOICE_LINE_GLOBAL_COOLDOWN_TICKS = 10; 

    // NOUVEAU: Map pour les sons "kill confirmed" planifiés (UUID du joueur -> Tick de déclenchement)
    private static final Map<UUID, Long> scheduledKillConfirmedPlays = new HashMap<>();
    
    // NOUVEAU: Délai en ticks pour jouer le son "kill confirmed" après qu'il soit déclenché (1 seconde)
    private static final int KILL_CONFIRM_DELAY_TICKS = 12; 

    /**
     * Résout le SoundEvent correct en fonction du preset de voix et du type de son.
     * @param voicePreset Le preset de voix actuel ("uk", "us", "ru", "fr", "ger", "none").
     * @param soundType Le type de son ("inform_reloading", "inform_killfirm", "voice_melee_attack").
     * @return Le SoundEvent correspondant, ou null si le preset est "none", ou le son par défaut si non trouvé.
     */
    private static SoundEvent resolveVoiceSound(String voicePreset, String soundType) {
        // Si le preset est "none", ne retourne aucun son
        if ("none".equals(voicePreset)) {
            return null;
        }

        // Sons par défaut pour chaque type, utilisés comme fallback
        // Ces variables doivent correspondre aux noms des champs RegistryObject dans ZombieroolModSounds.
        // Puisque le preset par défaut est "uk", nous utilisons les sons UK comme défauts.
        SoundEvent defaultReloading = ZombieroolModSounds.PLAYER_UK_INFORM_RELOADING.get();
        SoundEvent defaultKillfirm = ZombieroolModSounds.PLAYER_UK_INFORM_KILLFIRM.get();
        SoundEvent defaultMelee = ZombieroolModSounds.PLAYER_VOICE_MELEE_ATTACK.get(); 

        SoundEvent selectedSound = null;

        // Tente d'obtenir le son localisé spécifique en fonction du preset et du type
        switch (voicePreset) {
            case "uk": // Nouveau preset UK
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_UK_INFORM_RELOADING.get(); break; 
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_UK_INFORM_KILLFIRM.get(); break; 
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "us":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_US_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_US_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "ru":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_RU_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_RU_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "fr":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_FR_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_FR_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "ger":
                switch (soundType) {
                    case "inform_reloading": selectedSound = ZombieroolModSounds.PLAYER_GER_INFORM_RELOADING.get(); break;
                    case "inform_killfirm": selectedSound = ZombieroolModSounds.PLAYER_GER_INFORM_KILLFIRM.get(); break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break; 
                }
                break;
            case "default":
            default: // Cela gérera le preset "default" et tout preset non reconnu
                switch (soundType) {
                    case "inform_reloading": selectedSound = defaultReloading; break;
                    case "inform_killfirm": selectedSound = defaultKillfirm; break;
                    case "voice_melee_attack": selectedSound = defaultMelee; break;
                }
                break;
        }

        // Si selectedSound est null (ce qui signifie que le soundType n'a pas été trouvé
        // dans le switch du preset, ou si .get() a retourné null pour une raison quelconque),
        // nous revenons au son par défaut générique pour ce type de son (les sons UK).
        // La vérification .getLocation() == null est maintenue comme une sécurité supplémentaire.
        if (selectedSound == null || selectedSound.getLocation() == null) {
            switch (soundType) {
                case "inform_reloading": return defaultReloading;
                case "inform_killfirm": return defaultKillfirm;
                case "voice_melee_attack": return defaultMelee;
                default: return defaultReloading; // Fallback ultime si le soundType lui-même est inconnu
            }
        }

        return selectedSound;
    }

    /**
     * Vérifie si une ligne vocale peut être initiée pour le joueur donné, en respectant le cooldown global.
     * Si oui, met à jour le temps d'initiation et retourne true.
     * @param player Le joueur.
     * @param level Le niveau actuel.
     * @return true si une ligne vocale peut être initiée, false sinon.
     */
    private static boolean canInitiateVoiceLine(Player player, Level level) {
        long currentTick = level.getGameTime();
        UUID playerId = player.getUUID();
        long lastInitiationTime = lastVoiceLineInitiationTime.getOrDefault(playerId, 0L);

        if (currentTick - lastInitiationTime < VOICE_LINE_GLOBAL_COOLDOWN_TICKS) {
            return false; // Le cooldown global n'est pas écoulé
        }
        lastVoiceLineInitiationTime.put(playerId, currentTick); // Met à jour le temps d'initiation
        return true;
    }

    /**
     * Joue la ligne vocale "reloading!" pour le joueur donné.
     * Ce son est joué sans cooldown ni chance, car il est directement lié à une action du joueur,
     * mais respecte le cooldown global pour éviter les chevauchements.
     *
     * @param player Le joueur pour lequel le son doit être joué.
     * @param level Le niveau (monde) dans lequel le son doit être joué.
     */
    public static void playReloadingSound(Player player, Level level) {
        // Le son ne doit être joué que côté serveur pour éviter les doublons en multijoueur
        if (level.isClientSide) {
            return;
        }

        String voicePreset = "uk"; // Le preset de voix par défaut est maintenant "uk"
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }

        // Si le preset est "none", ne joue aucun son
        if ("none".equals(voicePreset)) {
            return;
        }

        if (!canInitiateVoiceLine(player, level)) {
            return; // Bloqué par le cooldown global
        }

        SoundEvent soundToPlay = resolveVoiceSound(voicePreset, "inform_reloading");
        if (soundToPlay != null) { // Vérifie si un son valide a été résolu
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    soundToPlay, // Utilise le SoundEvent résolu
                    SoundSource.PLAYERS, 1.0f, 1.0f); // Volume et pitch standards
        }
    }

    /**
     * Joue la ligne vocale "kill confirmed!" pour le joueur donné, avec une chance et un temps de recharge.
     * Ceci est pour éviter le spam et rendre le son moins répétitif.
     * Le son est maintenant planifié pour être joué après un délai.
     *
     * @param player Le joueur pour lequel le son doit être joué.
     * @param level Le niveau (monde) dans lequel le son doit être joué.
     */
    public static void playKillConfirmedSound(Player player, Level level) {
        // Le son ne doit être joué que côté serveur pour éviter les doublons en multijoueur
        if (level.isClientSide) {
            return;
        }

        String voicePreset = "uk"; // Le preset de voix par défaut est maintenant "uk"
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }

        // Si le preset est "none", ne joue aucun son
        if ("none".equals(voicePreset)) {
            return;
        }

        long currentTick = level.getGameTime(); // Récupère le tick actuel du jeu
        UUID playerId = player.getUUID(); // Obtient l'ID unique du joueur

        // Récupère le dernier temps où ce son a été joué pour ce joueur, ou 0 si jamais joué
        long lastPlayTime = lastKillConfirmedPlayTime.getOrDefault(playerId, 0L);

        // Vérifie si le temps de recharge spécifique au "kill confirmed" est écoulé
        if (currentTick - lastPlayTime < KILL_CONFIRM_COOLDOWN_TICKS) {
            return; // Si le cooldown n'est pas terminé, ne joue pas le son
        }

        // Vérifie la chance de jouer le son
        if (random.nextInt(100) < KILL_CONFIRM_CHANCE_PERCENT) {
            // Vérifie le cooldown global avant de planifier le son
            if (!canInitiateVoiceLine(player, level)) {
                return; // Bloqué par le cooldown global
            }

            // Planifie le son pour qu'il se joue après le délai spécifié
            scheduledKillConfirmedPlays.put(playerId, currentTick + KILL_CONFIRM_DELAY_TICKS);
            
            // Met à jour le dernier temps de lecture pour ce joueur (pour le cooldown spécifique au kill)
            lastKillConfirmedPlayTime.put(playerId, currentTick);
        }
    }

    /**
     * Vérifie si un joueur vient de commencer à recharger une arme et joue le son vocal si c'est le cas.
     * Cette méthode est conçue pour être appelée à chaque tick du joueur.
     * Elle gère également le déclenchement des sons "kill confirmed" planifiés.
     * @param player Le joueur à vérifier.
     */
    public static void checkAndPlayReloadingSoundOnTick(Player player) {
        if (player.level().isClientSide) {
            return; // Seulement côté serveur
        }

        Level level = player.level();
        long currentTick = level.getGameTime();
        UUID playerId = player.getUUID();

        String voicePreset = "uk"; // Le preset de voix par défaut est maintenant "uk"
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }

        // Si le preset est "none", ne joue aucun son
        if ("none".equals(voicePreset)) {
            scheduledKillConfirmedPlays.remove(playerId); // S'assure que les sons planifiés sont annulés
            return;
        }

        SoundEvent killConfirmedSound = resolveVoiceSound(voicePreset, "inform_killfirm");


        // Vérifie et joue les sons "kill confirmed" planifiés
        if (scheduledKillConfirmedPlays.containsKey(playerId)) {
            long scheduledTick = scheduledKillConfirmedPlays.get(playerId);
            if (currentTick >= scheduledTick) {
                if (killConfirmedSound != null) { // Vérifie si un son valide a été résolu
                    level.playSound(null, player.getX(), player.getY(), player.getZ(),
                            killConfirmedSound, // Utilise le SoundEvent résolu
                            SoundSource.PLAYERS, 1.0f, 1.0f); // Volume et pitch standards
                }
                scheduledKillConfirmedPlays.remove(playerId); // Supprime le son planifié
            }
        }

        ItemStack heldItem = player.getMainHandItem(); // Ou player.getOffhandItem() si applicable
        boolean isCurrentlyReloading = false;

        // Vérifie si l'objet tenu est une arme IReloadable et si son timer de rechargement est actif
        if (heldItem.getItem() instanceof IReloadable reloadedWeapon) {
            // Accède au NBT de l'ItemStack pour obtenir le ReloadTimer
            // Note: Cette méthode est un peu moins "propre" car elle accède directement au NBT
            // mais elle est nécessaire pour ne pas modifier les classes d'armes.
            if (heldItem.hasTag() && heldItem.getTag().contains("ReloadTimer")) {
                isCurrentlyReloading = reloadedWeapon.getReloadTimer(heldItem) > 0;
            }
        }

        boolean wasReloading = wasReloadingLastTick.getOrDefault(playerId, false);

        // Détecte la transition de "ne recharge pas" à "recharge"
        if (isCurrentlyReloading && !wasReloading) {
            playReloadingSound(player, player.level());
        }

        // Met à jour l'état pour le prochain tick
        wasReloadingLastTick.put(playerId, isCurrentlyReloading);
    }

    /**
     * Joue la ligne vocale "melee attack!" pour le joueur donné.
     * Ce son est joué sans cooldown ni chance, car il est directement lié à une action du joueur,
     * mais respecte le cooldown global pour éviter les chevauchements.
     *
     * @param player Le joueur pour lequel le son doit être joué.
     * @param level Le niveau (monde) dans lequel le son doit être joué.
     */
    public static void playMeleeAttackSound(Player player, Level level) {
        // Le son ne doit être joué que côté serveur pour éviter les doublons en multijoueur
        if (level.isClientSide) {
            return;
        }

        String voicePreset = "uk"; // Le preset de voix par défaut est maintenant "uk"
        if (level instanceof ServerLevel serverLevel) {
            voicePreset = WorldConfig.get(serverLevel).getVoicePreset();
        }

        // Si le preset est "none", ne joue aucun son
        if ("none".equals(voicePreset)) {
            return;
        }

        if (!canInitiateVoiceLine(player, level)) {
            return; // Bloqué par le cooldown global
        }

        SoundEvent soundToPlay = resolveVoiceSound(voicePreset, "voice_melee_attack");
        if (soundToPlay != null) { // Vérifie si un son valide a été résolu
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    soundToPlay, // Utilise le SoundEvent résolu
                    SoundSource.PLAYERS, 1.0f, 1.0f); // Volume et pitch standards
        }
    }
}
