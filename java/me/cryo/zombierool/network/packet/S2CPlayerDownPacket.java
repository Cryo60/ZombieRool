package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.client.network.ClientPacketEffects;
import me.cryo.zombierool.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

public class S2CPlayerDownPacket {
    private final boolean isDown;
    private final UUID playerUUID;

    public S2CPlayerDownPacket(boolean isDown, UUID playerUUID) {
        this.isDown = isDown;
        this.playerUUID = playerUUID;
    }

    public static void encode(S2CPlayerDownPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isDown);
        buf.writeUUID(msg.playerUUID);
    }

    public static S2CPlayerDownPacket decode(FriendlyByteBuf buf) {
        return new S2CPlayerDownPacket(buf.readBoolean(), buf.readUUID());
    }

    public static void handle(S2CPlayerDownPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        Packets.onClient(contextSupplier, () -> ClientPacketEffects.applyPlayerDown(msg.isDown, msg.playerUUID));
    }
}
