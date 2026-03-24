package me.cryo.zombierool.network.handler;

import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.LogicalSide;
import me.cryo.zombierool.network.packet.S2CSetEyeColorPacket;
import me.cryo.zombierool.client.renderer.ZombieRenderer; // Importez votre renderer

import java.util.function.Supplier;

public class S2CSetEyeColorPacketHandler {

    // Cette méthode est appelée lorsque le paquet est reçu
    public static void handle(S2CSetEyeColorPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        // Le paquet S2CSetEyeColorPacket est envoyé du serveur au client (PLAY_TO_CLIENT).
        // Donc, il doit être géré côté client.
        if (context.getDirection().getReceptionSide() == LogicalSide.CLIENT) {
            context.enqueueWork(() -> {
                // S'assurer que le travail est exécuté sur le thread de rendu principal
                ZombieRenderer.setEyeTexture(msg.getEyeColorPreset());
            });
            context.setPacketHandled(true);
        } else {
            // Logique pour le côté serveur si ce paquet devait être bidirectionnel.
            // Pour ce cas, il ne devrait jamais être reçu côté serveur.
            context.setPacketHandled(false); // Indique que le paquet n'a pas été géré correctement
        }
    }
}