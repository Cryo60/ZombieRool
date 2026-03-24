package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
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
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(msg));
        });
        context.setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handle(S2CRecoilPacket msg) {
            if (Minecraft.getInstance().player != null) {
                Player player = Minecraft.getInstance().player;
                player.setXRot(player.getXRot() - msg.pitchRecoil); 
                player.setYRot(player.getYRot() + msg.yawRecoil);
            }
        }
    }
}