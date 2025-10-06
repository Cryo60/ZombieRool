package net.mcreator.zombierool.event; // Créez ce package si ce n'est pas déjà fait

import net.mcreator.zombierool.api.IReloadable; // Assurez-vous d'importer votre interface IReloadable
import net.mcreator.zombierool.util.PlayerVoiceManager; // Importe la classe utilitaire
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Entity; // Importation ajoutée pour le type Entity
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
// Imports des mobs vanilla retirés car ils ne seront plus utilisés directement
// import net.minecraft.world.entity.monster.Zombie; 
// import net.minecraft.world.entity.monster.Skeleton;
// import net.minecraft.world.entity.monster.Husk;
// import net.minecraft.world.entity.monster.Stray;
// import net.minecraft.world.entity.monster.Drowned;

// NOUVEAU: Imports de vos entités personnalisées
import net.mcreator.zombierool.entity.ZombieEntity; // Assurez-vous que le chemin est correct
import net.mcreator.zombierool.entity.HellhoundEntity; // Assurez-vous que le chemin est correct
import net.mcreator.zombierool.entity.CrawlerEntity; // Assurez-vous que le chemin est correct

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.mcreator.zombierool.ZombieroolMod; // Assurez-vous que c'est le bon chemin vers votre classe de mod principale

/**
 * Cette classe gère les événements globaux pour déclencher les sons vocaux du joueur
 * sans modifier directement les classes d'armes.
 */
@Mod.EventBusSubscriber(modid = ZombieroolMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommonWeaponEventHandler {

    /**
     * Écoute l'événement de mort d'une entité pour déclencher le son "kill confirmed".
     * @param event L'événement LivingDeathEvent.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        // S'assure que l'événement se produit côté serveur pour éviter les doublons en multijoueur
        if (event.getEntity().level().isClientSide) {
            return;
        }

        LivingEntity killedEntity = event.getEntity();
        Entity eventKiller = event.getSource().getEntity(); // Récupère l'entité générique du tueur

        // Vérifie si l'entité qui a causé la mort est un LivingEntity et un Player
        if (eventKiller instanceof Player player) {
            // NOUVEAU: Vérifie si l'entité tuée est l'un de vos monstres personnalisés
            boolean isZombieLike = killedEntity instanceof ZombieEntity || 
                                   killedEntity instanceof HellhoundEntity ||
                                   killedEntity instanceof CrawlerEntity;

            if (isZombieLike) {
                // Déclenche le son de confirmation de kill via le PlayerVoiceManager
                // Le PlayerVoiceManager gérera la chance et le cooldown.
                PlayerVoiceManager.playKillConfirmedSound(player, player.level());
            }
        }
    }

    /**
     * Écoute l'événement de tick du joueur pour détecter le début du rechargement.
     * @param event L'événement PlayerTickEvent.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // S'assure que c'est le bon type de tick (serveur, fin de phase) et que c'est un joueur réel
        if (event.phase == TickEvent.Phase.END && event.side.isServer() && event.player != null) {
            // Appelle la méthode dans PlayerVoiceManager pour vérifier et jouer le son de rechargement
            PlayerVoiceManager.checkAndPlayReloadingSoundOnTick(event.player);
        }
    }
}
