package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

public class RecoilPacket {

    private final float pitchRecoil;
    private final float yawRecoil;

    public RecoilPacket(float pitchRecoil, float yawRecoil) {
        this.pitchRecoil = pitchRecoil;
        this.yawRecoil = yawRecoil;
    }

    public static void encode(RecoilPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.pitchRecoil);
        buf.writeFloat(msg.yawRecoil);
    }

    public static RecoilPacket decode(FriendlyByteBuf buf) {
        return new RecoilPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(RecoilPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(msg));
        });
        context.setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handle(RecoilPacket msg) {
            if (Minecraft.getInstance().player != null) {
                Player player = Minecraft.getInstance().player;
                player.setXRot(player.getXRot() - msg.pitchRecoil); 
                player.setYRot(player.getYRot() + msg.yawRecoil);
            }
        }
    }
}