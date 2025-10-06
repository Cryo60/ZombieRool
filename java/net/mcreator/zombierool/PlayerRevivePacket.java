package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.client.Minecraft;

import net.mcreator.zombierool.handlers.KeyInputHandler;

import java.util.function.Supplier;
import java.util.UUID;

/**
 * Paquet serveur-client pour envoyer la progression de la réanimation au client.
 * Ce paquet est utilisé pour mettre à jour la barre de progression de la réanimation sur l'écran du joueur.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class PlayerRevivePacket {
    private final UUID downPlayerUUID;
    private final long reviveStartTimeOnServer; // Temps de jeu absolu sur le serveur quand la réanimation a commencé
    private final long effectiveReviveDurationTicks; // Durée effective de la réanimation (peut être modifiée par perk)

    /**
     * @param downPlayerUUID L'UUID du joueur en cours de réanimation.
     * @param reviveStartTimeOnServer Le temps de jeu (en ticks) sur le serveur au début de la réanimation.
     * Utiliser -1 pour signaler une annulation de la réanimation par le serveur.
     * @param effectiveReviveDurationTicks La durée totale attendue de la réanimation, en ticks.
     */
    public PlayerRevivePacket(UUID downPlayerUUID, long reviveStartTimeOnServer, long effectiveReviveDurationTicks) {
        this.downPlayerUUID = downPlayerUUID;
        this.reviveStartTimeOnServer = reviveStartTimeOnServer;
        this.effectiveReviveDurationTicks = effectiveReviveDurationTicks;
    }

    /**
     * Encode les données du paquet dans un FriendlyByteBuf.
     */
    public static void encode(PlayerRevivePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.downPlayerUUID);
        buf.writeLong(msg.reviveStartTimeOnServer);
        buf.writeLong(msg.effectiveReviveDurationTicks);
    }

    /**
     * Décode les données du paquet depuis un FriendlyByteBuf.
     */
    public static PlayerRevivePacket decode(FriendlyByteBuf buf) {
        return new PlayerRevivePacket(buf.readUUID(), buf.readLong(), buf.readLong());
    }

    /**
     * Gère la réception du paquet côté client.
     * Cette méthode ne s'exécute que sur le client pour mettre à jour l'interface utilisateur.
     */
    public static void handle(PlayerRevivePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // S'assure que ce paquet est géré uniquement côté client
            if (context.getDirection().getReceptionSide().isClient()) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    if (msg.reviveStartTimeOnServer == -1) {
                        // Le serveur signale une annulation
                        KeyInputHandler.revivingTargetUUID = null;
                        KeyInputHandler.reviveBarStartTime = 0;
                        KeyInputHandler.setClientReviveDuration(0); // Réinitialise la durée client
                    } else {
                        // Met à jour les variables côté client pour l'affichage de la barre de progression
                        KeyInputHandler.revivingTargetUUID = msg.downPlayerUUID;
                        // Ajuste le temps de début de la barre côté client pour refléter le début sur le serveur
                        KeyInputHandler.reviveBarStartTime = mc.level.getGameTime() - (mc.level.getGameTime() - msg.reviveStartTimeOnServer); // Simple réaffectation du temps de début
                        KeyInputHandler.setClientReviveDuration(msg.effectiveReviveDurationTicks); // Met à jour la durée effective
                    }
                }
            }
        });
        context.setPacketHandled(true);
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        // L'enregistrement de ce paquet se fait dans la classe NetworkHandler principale.
    }
}
