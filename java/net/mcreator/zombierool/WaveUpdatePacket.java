// Dans src/main/java/net/mcreator/zombierool/network/WaveUpdatePacket.java

package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft; // Uniquement pour le côté client
import java.util.function.Supplier;
import net.mcreator.zombierool.WaveManager; // AJOUTE CETTE LIGNE

public class WaveUpdatePacket {
    private final int wave;

    public WaveUpdatePacket(int wave) {
        this.wave = wave;
    }

    // Decoder: Lit les données envoyées
    public static WaveUpdatePacket decode(FriendlyByteBuf buffer) {
        return new WaveUpdatePacket(buffer.readInt());
    }

    // Encoder: Écrit les données à envoyer
    public static void encode(WaveUpdatePacket msg, FriendlyByteBuf buffer) {
        buffer.writeInt(msg.wave);
    }

    // Handler: Gère le paquet à la réception
    public static void handle(WaveUpdatePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Côté CLIENT : Mettre à jour la vague affichée
            if (Minecraft.getInstance().level != null) {
                // Nous allons créer une variable statique dans WaveManager pour stocker la vague côté client
                WaveManager.setClientWave(msg.wave); // Cette ligne va maintenant fonctionner
            }
        });
        context.setPacketHandled(true);
    }
}