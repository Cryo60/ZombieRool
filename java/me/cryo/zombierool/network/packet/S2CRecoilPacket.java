package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.client.network.ClientPacketEffects;
import me.cryo.zombierool.network.Packets;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CRecoilPacket {

    private final float pitchRecoil;
    private final float yawRecoil;

    public S2CRecoilPacket(float pitchRecoil, float yawRecoil) {
        this.pitchRecoil = pitchRecoil;
        this.yawRecoil = yawRecoil;
    }

    public static void encode(S2CRecoilPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.pitchRecoil);
        buf.writeFloat(msg.yawRecoil);
    }

    public static S2CRecoilPacket decode(FriendlyByteBuf buf) {
        return new S2CRecoilPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(S2CRecoilPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        Packets.onClient(contextSupplier, () -> ClientPacketEffects.applyRecoil(msg.pitchRecoil, msg.yawRecoil));
    }
}
