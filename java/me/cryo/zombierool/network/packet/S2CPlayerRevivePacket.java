package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraft.client.Minecraft;

import me.cryo.zombierool.handlers.KeyInputHandler;

import java.util.function.Supplier;
import java.util.UUID;

/**
 * Paquet serveur-client pour envoyer la progression de la réanimation au client.
 * Ce paquet est utilisé pour mettre à jour la barre de progression de la réanimation sur l'écran du joueur.
 */
@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class S2CPlayerRevivePacket {
    private final UUID downPlayerUUID;
    private final long reviveStartTimeOnServer; // Temps de jeu absolu sur le serveur quand la réanimation a commencé
    private final long effectiveReviveDurationTicks; // Durée effective de la réanimation (peut être modifiée par perk)

    /**
     * @param downPlayerUUID L'UUID du joueur en cours de réanimation.
     * @param reviveStartTimeOnServer Le temps de jeu (en ticks) sur le serveur au début de la réanimation.
     * Utiliser -1 pour signaler une annulation de la réanimation par le serveur.
     * @param effectiveReviveDurationTicks La durée totale attendue de la réanimation, en ticks.
     */
    public S2CPlayerRevivePacket(UUID downPlayerUUID, long reviveStartTimeOnServer, long effectiveReviveDurationTicks) {
        this.downPlayerUUID = downPlayerUUID;
        this.reviveStartTimeOnServer = reviveStartTimeOnServer;
        this.effectiveReviveDurationTicks = effectiveReviveDurationTicks;
    }

    /**
     * Encode les données du paquet dans un FriendlyByteBuf.
     */
    public static void encode(S2CPlayerRevivePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.downPlayerUUID);
        buf.writeLong(msg.reviveStartTimeOnServer);
        buf.writeLong(msg.effectiveReviveDurationTicks);
    }

    /**
     * Décode les données du paquet depuis un FriendlyByteBuf.
     */
    public static S2CPlayerRevivePacket decode(FriendlyByteBuf buf) {
        return new S2CPlayerRevivePacket(buf.readUUID(), buf.readLong(), buf.readLong());
    }

    /**
     * Gère la réception du paquet côté client.
     * Cette méthode ne s'exécute que sur le client pour mettre à jour l'interface utilisateur.
     */
    public static void handle(S2CPlayerRevivePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection().getReceptionSide().isClient()) {
                net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT, () -> () -> ClientHandler.apply(msg));
            }
        });
        context.setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2CPlayerRevivePacket msg) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            if (msg.reviveStartTimeOnServer == -1) {
                KeyInputHandler.revivingTargetUUID = null;
                KeyInputHandler.reviveBarStartTime = 0;
                KeyInputHandler.setClientReviveDuration(0);
            } else {
                KeyInputHandler.revivingTargetUUID = msg.downPlayerUUID;
                KeyInputHandler.reviveBarStartTime = mc.level.getGameTime() - (mc.level.getGameTime() - msg.reviveStartTimeOnServer);
                KeyInputHandler.setClientReviveDuration(msg.effectiveReviveDurationTicks);
            }
        }
    }

    @SubscribeEvent
    public static void register(FMLCommonSetupEvent event) {
        // L'enregistrement de ce paquet se fait dans la classe NetworkHandler principale.
    }
}
