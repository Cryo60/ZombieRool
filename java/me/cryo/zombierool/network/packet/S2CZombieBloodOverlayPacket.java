package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.screens.BloodZombieScreenOverlay;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;

public class S2CZombieBloodOverlayPacket {
    public final boolean active;

    public S2CZombieBloodOverlayPacket(boolean active) {
        this.active = active;
    }

    public static void encode(S2CZombieBloodOverlayPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.active);
    }

    public static S2CZombieBloodOverlayPacket decode(FriendlyByteBuf buf) {
        return new S2CZombieBloodOverlayPacket(buf.readBoolean());
    }

    public static void handle(S2CZombieBloodOverlayPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                BloodZombieScreenOverlay.isActiveClientSide = msg.active;
            });
        });
        ctx.get().setPacketHandled(true);
    }
}