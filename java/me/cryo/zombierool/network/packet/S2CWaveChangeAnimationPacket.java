package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientHUDHandler;
import java.util.function.Supplier;

public class S2CWaveChangeAnimationPacket {
    private final int fromWave;
    private final int toWave;
    public S2CWaveChangeAnimationPacket(int fromWave, int toWave) {
        this.fromWave = fromWave;
        this.toWave = toWave;
    }
    public static void encode(S2CWaveChangeAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.fromWave);
        buf.writeInt(msg.toWave);
    }
    public static S2CWaveChangeAnimationPacket decode(FriendlyByteBuf buf) {
        return new S2CWaveChangeAnimationPacket(buf.readInt(), buf.readInt());
    }
    public static void handle(S2CWaveChangeAnimationPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.apply(msg)));
        context.setPacketHandled(true);
    }

    private static final class ClientHandler {
        private static void apply(S2CWaveChangeAnimationPacket msg) {
            net.minecraft.client.Minecraft.getInstance().tell(() ->
                    ClientHUDHandler.triggerWaveChangeAnimation(msg.fromWave, msg.toWave));
        }
    }
}