package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CPlayerPosePacket {
    private final UUID playerUUID;
    private final Pose pose;

    public S2CPlayerPosePacket(UUID playerUUID, Pose pose) {
        this.playerUUID = playerUUID;
        this.pose = pose;
    }

    public static void encode(S2CPlayerPosePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeEnum(msg.pose);
    }

    public static S2CPlayerPosePacket decode(FriendlyByteBuf buf) {
        return new S2CPlayerPosePacket(buf.readUUID(), buf.readEnum(Pose.class));
    }

    public static void handle(S2CPlayerPosePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handlePosePacket(msg));
        });
        context.setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handlePosePacket(S2CPlayerPosePacket msg) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            net.minecraft.client.multiplayer.ClientLevel level = mc.level;
            if (level != null) {
                net.minecraft.world.entity.player.Player player = level.getPlayerByUUID(msg.playerUUID);
                if (player != null) {
                    player.setPose(msg.pose);
                    player.refreshDimensions();
                }
            }
        }
    }
}