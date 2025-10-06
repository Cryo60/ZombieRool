package net.mcreator.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.ScoreboardRenderer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

/**
 * Paquet envoyé du serveur au client pour déclencher l'animation de changement de vague
 * (clignotement du nombre de vague en haut à gauche).
 */
public class WaveChangeAnimationPacket {
    private final int fromWave;
    private final int toWave;

    /**
     * Constructeur du paquet.
     * @param fromWave Le numéro de la vague précédente.
     * @param toWave Le numéro de la nouvelle vague.
     */
    public WaveChangeAnimationPacket(int fromWave, int toWave) {
        this.fromWave = fromWave;
        this.toWave = toWave;
    }

    /**
     * Encode le paquet dans le buffer.
     * @param msg L'instance du paquet à encoder.
     * @param buf Le buffer dans lequel écrire.
     */
    public static void encode(WaveChangeAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.fromWave);
        buf.writeInt(msg.toWave);
    }

    /**
     * Décode le paquet depuis le buffer.
     * @param buf Le buffer à partir duquel lire.
     * @return Une nouvelle instance du paquet.
     */
    public static WaveChangeAnimationPacket decode(FriendlyByteBuf buf) {
        return new WaveChangeAnimationPacket(buf.readInt(), buf.readInt());
    }

    /**
     * Gère le paquet côté client.
     * @param msg L'instance du paquet reçu.
     * @param contextSupplier Le fournisseur de contexte du réseau.
     */
    public static void handle(WaveChangeAnimationPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // S'assure que le code client est exécuté sur le thread client principal
            Minecraft.getInstance().tell(() -> {
                ScoreboardRenderer.triggerWaveChangeAnimation(msg.fromWave, msg.toWave);
            });
        });
        context.setPacketHandled(true);
    }
}
