package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraft.client.Minecraft;
import net.mcreator.zombierool.client.ScreenShakeHandler;

import java.util.function.Supplier;

public class ScreenShakePacket {
    private final int duration;
    private final float intensity;
    private final ScreenShakeHandler.ShakeType type;

    public ScreenShakePacket(int duration, float intensity, ScreenShakeHandler.ShakeType type) {
        this.duration = duration;
        this.intensity = intensity;
        this.type = type;
    }

    public ScreenShakePacket(FriendlyByteBuf buffer) {
        this.duration = buffer.readInt();
        this.intensity = buffer.readFloat();
        this.type = buffer.readEnum(ScreenShakeHandler.ShakeType.class);
    }

    public void encode(FriendlyByteBuf buffer) {
        buffer.writeInt(duration);
        buffer.writeFloat(intensity);
        buffer.writeEnum(type);
    }

    public static void handle(ScreenShakePacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            if (Minecraft.getInstance().player != null) {
                ScreenShakeHandler.startShake(message.duration, message.intensity, message.type);
                System.out.println("DEBUG: ScreenShakePacket received. Duration: " + message.duration + ", Intensity: " + message.intensity + ", Type: " + message.type);
            }
        });
        context.setPacketHandled(true);
    }
}
