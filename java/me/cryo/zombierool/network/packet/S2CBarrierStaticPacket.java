package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.client.screens.BarrierStaticOverlay;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CBarrierStaticPacket {
    private final float intensity;

    public S2CBarrierStaticPacket(float intensity) {
        this.intensity = intensity;
    }

    public static void encode(S2CBarrierStaticPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.intensity);
    }

    public static S2CBarrierStaticPacket decode(FriendlyByteBuf buf) {
        return new S2CBarrierStaticPacket(buf.readFloat());
    }

    public static void handle(S2CBarrierStaticPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> BarrierStaticOverlay.setIntensity(msg.intensity)));
        context.setPacketHandled(true);
    }
}
