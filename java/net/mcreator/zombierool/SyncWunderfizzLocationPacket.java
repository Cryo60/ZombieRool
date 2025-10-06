package net.mcreator.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft; // NOUVEAU: Import pour Minecraft client

import java.util.function.Supplier;

// Ce paquet est envoyé du serveur au client pour synchroniser la position de la Wunderfizz active.
public class SyncWunderfizzLocationPacket {
    private final BlockPos wunderfizzLocation;

    // Constructeur pour envoyer un paquet avec une position.
    // Si la position est null, cela signifie qu'aucune Wunderfizz n'est active.
    public SyncWunderfizzLocationPacket(BlockPos wunderfizzLocation) {
        this.wunderfizzLocation = wunderfizzLocation;
    }

    // Méthode d'encodage: écrit la position dans le buffer.
    public static void encode(SyncWunderfizzLocationPacket msg, FriendlyByteBuf buf) {
        // Écrit la position si elle n'est pas nulle, sinon écrit un booléen false pour indiquer l'absence.
        buf.writeBoolean(msg.wunderfizzLocation != null);
        if (msg.wunderfizzLocation != null) {
            buf.writeBlockPos(msg.wunderfizzLocation);
        }
    }

    // Méthode de décodage: lit la position du buffer.
    public static SyncWunderfizzLocationPacket decode(FriendlyByteBuf buf) {
        boolean hasLocation = buf.readBoolean();
        BlockPos wunderfizzLocation = null;
        if (hasLocation) {
            wunderfizzLocation = buf.readBlockPos();
        }
        return new SyncWunderfizzLocationPacket(wunderfizzLocation);
    }

    // Gestionnaire de paquets côté client.
    public static void handle(SyncWunderfizzLocationPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Sur le thread du client, met à jour la position active de la Wunderfizz.
            // Utilisez une variable statique ou un champ dans une classe de gestionnaire client.
            // Par exemple, dans KeyInputHandler ou une nouvelle classe ClientWunderfizzManager.
            Minecraft.getInstance().execute(() -> {
                // Ici, nous allons mettre à jour une variable statique dans KeyInputHandler.
                // Cela évite de dépendre d'une instance de WunderfizzManager côté client.
                net.mcreator.zombierool.handlers.KeyInputHandler.setClientActiveWunderfizzLocation(msg.wunderfizzLocation);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
