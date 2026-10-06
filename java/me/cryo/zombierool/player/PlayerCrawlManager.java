package me.cryo.zombierool.player;

import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class PlayerCrawlManager {
    private static final Set<UUID> crawlingPlayers = ConcurrentHashMap.newKeySet();

    public static boolean isCrawling(UUID uuid) {
        return crawlingPlayers.contains(uuid);
    }

    public static void setCrawling(UUID uuid, boolean crawling) {
        if (crawling) crawlingPlayers.add(uuid);
        else crawlingPlayers.remove(uuid);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Player player = event.player;
            if (isCrawling(player.getUUID())) {
                player.setPose(Pose.SWIMMING);
            }
        }
    }

    @SubscribeEvent
    public static void onEntitySize(EntityEvent.Size event) {
        if (event.getEntity() instanceof Player player) {
            boolean isDownCrawl = PlayerDownManager.isPlayerDown(player.getUUID());
            boolean isManualCrawl = isCrawling(player.getUUID());

            if (isDownCrawl || isManualCrawl) {
                // On garde la hauteur de la hitbox à 1.8 pour éviter de traverser les trous 1x1
                // et permettre aux zombies d'attaquer le joueur.
                event.setNewSize(EntityDimensions.scalable(0.6F, 1.8F), false);
                
                // Mais on fixe la caméra au ras du sol
                event.setNewEyeHeight(0.4F);
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        crawlingPlayers.remove(event.getEntity().getUUID());
    }
}