package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public class S2CStopFourIsReadySoundPacket {

    public S2CStopFourIsReadySoundPacket() {
    }

    // Constructor for decoding the packet
    public S2CStopFourIsReadySoundPacket(FriendlyByteBuf buffer) {
        // No data needed for this packet, as it just signals a sound stop
    }

    // Method for encoding the packet
    public void encode(FriendlyByteBuf buffer) {
        // No data to encode
    }

    // Handler for the packet
    public static void handle(S2CStopFourIsReadySoundPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientHandler::apply));
        context.setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply() {
            net.minecraft.client.Minecraft.getInstance().getSoundManager().stop(
                    ZombieroolModSounds.FOUR_IS_READY.get().getLocation(), SoundSource.PLAYERS);
        }
    }
}