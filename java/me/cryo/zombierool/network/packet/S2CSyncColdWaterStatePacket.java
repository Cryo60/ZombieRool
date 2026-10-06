package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.screens.ColdWaterEffectOverlay; // Import the overlay class

import java.util.function.Supplier;

/**
 * Packet to synchronize the cold water effect intensity from server to client.
 */
public class S2CSyncColdWaterStatePacket {
    private final float coldWaterIntensity;

    public S2CSyncColdWaterStatePacket(float coldWaterIntensity) {
        this.coldWaterIntensity = coldWaterIntensity;
    }

    /**
     * Encodes the packet data into the buffer.
     */
    public static void encode(S2CSyncColdWaterStatePacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.coldWaterIntensity);
    }

    /**
     * Decodes the packet data from the buffer.
     */
    public static S2CSyncColdWaterStatePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncColdWaterStatePacket(buf.readFloat());
    }

    /**
     * Handles the packet on the client side.
     */
    public static void handle(S2CSyncColdWaterStatePacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // Client-side: Update the cold water intensity in the overlay class
            ColdWaterEffectOverlay.setColdWaterIntensity(msg.coldWaterIntensity);
        });
        context.setPacketHandled(true);
    }
}
