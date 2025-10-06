package net.mcreator.zombierool.network;

import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class StopFourIsReadySoundPacket {

    public StopFourIsReadySoundPacket() {
    }

    // Constructor for decoding the packet
    public StopFourIsReadySoundPacket(FriendlyByteBuf buffer) {
        // No data needed for this packet, as it just signals a sound stop
    }

    // Method for encoding the packet
    public void encode(FriendlyByteBuf buffer) {
        // No data to encode
    }

    // Handler for the packet
    public static void handle(StopFourIsReadySoundPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            // ONLY execute on the CLIENT side
            Minecraft.getInstance().getSoundManager().stop(ZombieroolModSounds.FOUR_IS_READY.get().getLocation(), SoundSource.PLAYERS);
        });
        context.setPacketHandled(true);
    }
}