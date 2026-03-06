package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.UUID;
import java.util.function.Supplier;

public class PlayerPosePacket {
    private final UUID playerUUID;
    private final Pose pose;

    public PlayerPosePacket(UUID playerUUID, Pose pose) {
        this.playerUUID = playerUUID;
        this.pose = pose;
    }

    public static void encode(PlayerPosePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeEnum(msg.pose);
    }

    public static PlayerPosePacket decode(FriendlyByteBuf buf) {
        return new PlayerPosePacket(buf.readUUID(), buf.readEnum(Pose.class));
    }

    public static void handle(PlayerPosePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Exécuter uniquement côté client
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePosePacket(msg));
        });
        context.setPacketHandled(true);
    }

    // Classe interne pour gérer le code client
    private static class ClientHandler {
        public static void handlePosePacket(PlayerPosePacket msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.multiplayer.ClientLevel level = mc.level;
            if (level != null) {
                net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(msg.playerUUID);
                if (player != null) {
                    player.setPose(msg.pose);
                }
            }
        }
    }
}