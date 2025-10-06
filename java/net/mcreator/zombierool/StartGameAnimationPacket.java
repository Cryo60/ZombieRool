package net.mcreator.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.ScoreboardRenderer;
import net.mcreator.zombierool.WaveManager; // Import ajouté
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/**
 * Paquet envoyé du serveur au client pour déclencher l'animation de début de partie
 * affichant "MANCHE I" au centre de l'écran.
 */
public class StartGameAnimationPacket {
    private final int waveNumber;

    /**
     * Constructeur du paquet.
     * @param waveNumber Le numéro de la vague à afficher (normalement 1 pour le début).
     */
    public StartGameAnimationPacket(int waveNumber) {
        this.waveNumber = waveNumber;
    }

    /**
     * Encode le paquet dans le buffer.
     * @param msg L'instance du paquet à encoder.
     * @param buf Le buffer dans lequel écrire.
     */
    public static void encode(StartGameAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.waveNumber);
    }

    /**
     * Décode le paquet depuis le buffer.
     * @param buf Le buffer à partir duquel lire.
     * @return Une nouvelle instance du paquet.
     */
    public static StartGameAnimationPacket decode(FriendlyByteBuf buf) {
        return new StartGameAnimationPacket(buf.readInt());
    }

    /**
     * Gère le paquet côté client.
     * @param msg L'instance du paquet reçu.
     * @param contextSupplier Le fournisseur de contexte du réseau.
     */
    public static void handle(StartGameAnimationPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // S'assure que le code client est exécuté sur le thread client principal
            Minecraft.getInstance().tell(() -> {
                ScoreboardRenderer.triggerStartGameAnimation(msg.waveNumber);
                // NOUVEAU : Met à jour immédiatement la vague côté client pour l'affichage statique.
                WaveManager.setClientWave(msg.waveNumber); 
            });
        });
        context.setPacketHandled(true);
    }
}
