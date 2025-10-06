package net.mcreator.zombierool;

// Cette classe est maintenant uniquement un conteneur pour les constantes de configuration et la gestion du timestamp.
// Elle ne gère plus directement les événements du jeu.
import net.minecraftforge.fml.common.Mod; // Garde ceci si tu as d'autres écouteurs d'événements Forge ici, sinon tu peux le supprimer

@Mod.EventBusSubscriber(modid = "zombierool") // Tu peux potentiellement supprimer cette annotation si la classe ne gère plus d'événements
public class ZombiePlayerHandler {

    // --- Constantes de configuration pour les comportements du joueur et l'UI ---
    // Ces constantes seront utilisées par ton Mixin (PlayerTickMixin) et par ton code UI séparé
    public static final boolean HIDE_HUNGER = true;   // La barre de faim doit être masquée dans l'UI
    public static final boolean DISABLE_HUNGER = true;  // Désactive complètement la faim (le joueur ne perd pas de niveau de faim)
    public static final boolean DISABLE_XP = true;      // Désactive l'acquisition et l'affichage de l'XP
    public static final boolean HIDE_HEALTH = false;    // La barre de vie NE doit PAS être masquée dans l'UI (tu la veux visible)

    public static final double BASE_MAX_HEALTH = 6.0; // 3 cœurs de vie par défaut (6.0 points de vie)
    public static final int REGEN_DELAY = 100;        // Délai en ticks (5 secondes) avant la régénération lente après avoir subi des dégâts

    // Variable statique pour le timestamp de la dernière blessure du joueur.
    // Elle sera mise à jour par ton Mixin qui gère les dégâts (si tu en as un) ou par un événement LivingHurtEvent.
    private static int lastHurtTimestamp = 0;

    /**
     * Permet à d'autres classes (comme ton Mixin) d'accéder au timestamp de la dernière blessure.
     * @return Le tick du jeu au moment de la dernière blessure.
     */
    public static int getLastHurtTimestamp() {
        return lastHurtTimestamp;
    }

    /**
     * Permet à d'autres classes (comme ton Mixin) de mettre à jour le timestamp de la dernière blessure.
     * @param timestamp Le nouveau tick du jeu.
     */
    public static void setLastHurtTimestamp(int timestamp) {
        lastHurtTimestamp = timestamp;
    }

    // Tu peux laisser ici d'autres écouteurs d'événements Forge qui ne sont PAS gérés par des Mixins.
    // Par exemple, PlayerEvent.PlayerLoggedInEvent est un bon candidat pour rester dans cette classe.
    // ...
}