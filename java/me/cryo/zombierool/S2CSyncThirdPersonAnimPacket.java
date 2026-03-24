package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.UUID;
import java.util.function.Supplier;

public class S2CSyncThirdPersonAnimPacket {
    private final UUID playerUUID;
    private final String animType;
    private final int ticks;

    public S2CSyncThirdPersonAnimPacket(UUID playerUUID, String animType, int ticks) {
        this.playerUUID = playerUUID;
        this.animType = animType;
        this.ticks = ticks;
    }

    public static void encode(S2CSyncThirdPersonAnimPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.playerUUID);
        buf.writeUtf(msg.animType);
        buf.writeInt(msg.ticks);
    }

    public static S2CSyncThirdPersonAnimPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncThirdPersonAnimPacket(buf.readUUID(), buf.readUtf(), buf.readInt());
    }

    public static void handle(S2CSyncThirdPersonAnimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                me.cryo.zombierool.client.ThirdPersonAnimHandler.startAnim(msg.playerUUID, msg.animType, msg.ticks);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}