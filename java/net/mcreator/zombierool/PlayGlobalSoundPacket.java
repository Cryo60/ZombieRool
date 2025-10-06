package net.mcreator.zombierool.network.packet; // Assurez-vous que le chemin du paquet correspond à votre structure

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries; // Pour accéder aux événements sonores enregistrés

import java.util.function.Supplier;

/**
 * Paquet envoyé du serveur au client pour demander la lecture d'un son globalement.
 */
public class PlayGlobalSoundPacket {
    private final ResourceLocation soundLocation;

    /**
     * Constructeur du paquet.
     * @param soundLocation La ResourceLocation du son à jouer.
     */
    public PlayGlobalSoundPacket(ResourceLocation soundLocation) {
        this.soundLocation = soundLocation;
    }

    /**
     * Encode le paquet dans un FriendlyByteBuf.
     * @param msg Le paquet à encoder.
     * @param buf Le buffer dans lequel écrire.
     */
    public static void encode(PlayGlobalSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.soundLocation);
    }

    /**
     * Décode le paquet à partir d'un FriendlyByteBuf.
     * @param buf Le buffer à lire.
     * @return Le paquet décodé.
     */
    public static PlayGlobalSoundPacket decode(FriendlyByteBuf buf) {
        return new PlayGlobalSoundPacket(buf.readResourceLocation());
    }

    /**
     * Gère le paquet côté client.
     * @param msg Le paquet reçu.
     * @param contextSupplier Fournit le contexte du réseau.
     */
    public static void handle(PlayGlobalSoundPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // S'exécute sur le thread principal du client
            SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(msg.soundLocation);
            if (sound != null) {
                // Joue le son. SimpleSoundInstance.forUI est souvent utilisé pour les sons non spatialisés,
                // ou SoundSource.MASTER avec un volume et un pitch qui ne dépendent pas de la position.
                // Ici, nous utilisons un SimpleSoundInstance de base qui ne se soucie pas de la position.
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, 1.0f)); // Volume 1.0, Pitch 1.0
            } else {
                System.err.println("Son non trouvé côté client pour ResourceLocation: " + msg.soundLocation);
            }
        });
        context.setPacketHandled(true);
    }
}
