package net.mcreator.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.client.screens.ColdWaterEffectOverlay; // Import the overlay class

import java.util.function.Supplier;

/**
 * Packet to synchronize the cold water effect intensity from server to client.
 */
public class SyncColdWaterStatePacket {
    private final float coldWaterIntensity;

    public SyncColdWaterStatePacket(float coldWaterIntensity) {
        this.coldWaterIntensity = coldWaterIntensity;
    }

    /**
     * Encodes the packet data into the buffer.
     */
    public static void encode(SyncColdWaterStatePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.coldWaterIntensity);
    }

    /**
     * Decodes the packet data from the buffer.
     */
    public static SyncColdWaterStatePacket decode(FriendlyByteBuf buf) {
        return new SyncColdWaterStatePacket(buf.readFloat());
    }

    /**
     * Handles the packet on the client side.
     */
    public static void handle(SyncColdWaterStatePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Client-side: Update the cold water intensity in the overlay class
            ColdWaterEffectOverlay.setColdWaterIntensity(msg.coldWaterIntensity);
        });
        context.setPacketHandled(true);
    }
}
