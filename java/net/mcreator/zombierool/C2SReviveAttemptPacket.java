package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel; // Assurez-vous que cet import est présent
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.player.PlayerDownManager;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Paquet client-serveur pour initier ou annuler une tentative de réanimation.
 */
public class C2SReviveAttemptPacket {
    private final UUID targetPlayerUUID;
    private final boolean isCanceling;

    /**
     * @param targetPlayerUUID L'UUID du joueur que le client tente de réanimer.
     * @param isCanceling Vrai si le client annule une réanimation, Faux pour initier/continuer.
     */
    public C2SReviveAttemptPacket(UUID targetPlayerUUID, boolean isCanceling) {
        this.targetPlayerUUID = targetPlayerUUID;
        this.isCanceling = isCanceling;
    }

    /**
     * Encode les données du paquet dans un FriendlyByteBuf.
     */
    public static void encode(C2SReviveAttemptPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetPlayerUUID);
        buf.writeBoolean(msg.isCanceling);
    }

    /**
     * Décode les données du paquet depuis un FriendlyByteBuf.
     */
    public static C2SReviveAttemptPacket decode(FriendlyByteBuf buf) {
        return new C2SReviveAttemptPacket(buf.readUUID(), buf.readBoolean());
    }

    /**
     * Gère la réception du paquet côté serveur.
     * Cette méthode est appelée lorsque le paquet est reçu par le serveur.
     */
    public static void handle(C2SReviveAttemptPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender(); // Le joueur qui a envoyé ce paquet
            if (sender != null) {
                ServerLevel level = sender.serverLevel(); // Récupère l'instance de ServerLevel

                Player targetPlayer = level.getServer().getPlayerList().getPlayer(msg.targetPlayerUUID);

                // Vérifie si la cible est un joueur valide et est bien un ServerPlayer
                if (targetPlayer instanceof ServerPlayer downPlayer) {
                    if (msg.isCanceling) {
                        // Si le client annule, on appelle la méthode d'annulation du PlayerDownManager
                        PlayerDownManager.cancelRevive(level, sender.getUUID()); // AJOUT DE 'level'
                    } else {
                        // Si le client tente de réanimer, on appelle la méthode de démarrage/continuation
                        PlayerDownManager.startRevive(sender, downPlayer, level);
                    }
                } else {
                    // Si la cible n'est pas trouvée ou n'est plus valide (ex: déconnectée),
                    // on annule toute réanimation en cours pour cet expéditeur.
                    PlayerDownManager.cancelRevive(level, sender.getUUID()); // AJOUT DE 'level'
                }
            }
        });
        context.setPacketHandled(true);
    }
}
