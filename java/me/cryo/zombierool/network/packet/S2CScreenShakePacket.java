package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ScreenShakeHandler;

import java.util.function.Supplier;

public class S2CScreenShakePacket {
    private final int duration;
    private final float intensity;
    private final ScreenShakeHandler.ShakeType type;

    public S2CScreenShakePacket(int duration, float intensity, ScreenShakeHandler.ShakeType type) {
        this.duration = duration;
        this.intensity = intensity;
        this.type = type;
    }

    public S2CScreenShakePacket(FriendlyByteBuf buffer) {
        this.duration = buffer.readInt();
        this.intensity = buffer.readFloat();
        this.type = buffer.readEnum(ScreenShakeHandler.ShakeType.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(duration);
        buffer.writeFloat(intensity);
        buffer.writeEnum(type);
    }

    public static void handle(S2CScreenShakePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(message)));
        context.setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2CScreenShakePacket message) {
            if (net.minecraft.client.Minecraft.getInstance().player != null) {
                ScreenShakeHandler.startShake(message.duration, message.intensity, message.type);
            }
        }
    }
}
