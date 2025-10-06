package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;

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
            // Client side logic
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level != null) {
                Player player = level.getPlayerByUUID(msg.playerUUID);
                if (player != null) {
                    player.setPose(msg.pose);
                }
            }
        });
        context.setPacketHandled(true);
    }
}