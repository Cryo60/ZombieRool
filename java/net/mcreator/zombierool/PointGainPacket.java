package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection; // Make sure this import is there!

import java.util.function.Supplier;

public class PointGainPacket {
    private final int gainedAmount;

    public PointGainPacket(int gainedAmount) {
        this.gainedAmount = gainedAmount;
    }

    public static PointGainPacket decode(FriendlyByteBuf buffer) {
        return new PointGainPacket(buffer.readInt());
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(gainedAmount);
    }

    public static void handle(PointGainPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                // Ajoutez cette ligne de débogage CÔTÉ CLIENT (avant d'accéder au joueur)
                System.out.println("[CLIENT] Received PointGainPacket with amount: " + message.gainedAmount + " (before player check).");

                net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
                if (clientPlayer != null) {
                    // Ajoutez cette ligne de débogage CÔTÉ CLIENT (après accès au joueur)
                    System.out.println("[CLIENT] Updating PointManager.LAST_POINT_GAINS for " + clientPlayer.getName().getString() + " with amount: " + message.gainedAmount + " at client game time: " + clientPlayer.level().getGameTime());

                    net.mcreator.zombierool.PointManager.LAST_POINT_GAINS.put(
                        clientPlayer.getUUID(),
                        new net.mcreator.zombierool.PointManager.PointGainInfo(message.gainedAmount, clientPlayer.level().getGameTime())
                    );
                    // Vous pouvez vérifier la valeur stockée ici
                    System.out.println("[CLIENT] PointManager.LAST_POINT_GAINS for " + clientPlayer.getUUID() + " is now: " + net.mcreator.zombierool.PointManager.LAST_POINT_GAINS.get(clientPlayer.getUUID()).amount);
                } else {
                    System.out.println("[CLIENT] Received PointGainPacket, but clientPlayer is null.");
                }
            } else {
                System.out.println("[CLIENT] PointGainPacket received with incorrect direction: " + context.getDirection());
            }
        });
        context.setPacketHandled(true);
    }
}