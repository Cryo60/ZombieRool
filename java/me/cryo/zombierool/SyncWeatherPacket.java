package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientEnvironmentEffects;

import java.util.function.Supplier;

public class SyncWeatherPacket {
    public final boolean enabled;
    public final String particleId;
    public final String density;
    public final String mode;

    public SyncWeatherPacket(boolean enabled, String particleId, String density, String mode) {
        this.enabled = enabled;
        this.particleId = particleId;
        this.density = density;
        this.mode = mode;
    }

    public static void encode(SyncWeatherPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeUtf(msg.particleId != null ? msg.particleId : "");
        buf.writeUtf(msg.density != null ? msg.density : "");
        buf.writeUtf(msg.mode != null ? msg.mode : "");
    }

    public static SyncWeatherPacket decode(FriendlyByteBuf buf) {
        return new SyncWeatherPacket(buf.readBoolean(), buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(SyncWeatherPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientEnvironmentEffects.handleWeatherSync(msg.enabled, msg.particleId, msg.density, msg.mode);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}