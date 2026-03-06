package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.ClientHUDHandler;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;

public class WaveChangeAnimationPacket {
    private final int fromWave;
    private final int toWave;
    public WaveChangeAnimationPacket(int fromWave, int toWave) {
        this.fromWave = fromWave;
        this.toWave = toWave;
    }
    public static void encode(WaveChangeAnimationPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.fromWave);
        buf.writeInt(msg.toWave);
    }
    public static WaveChangeAnimationPacket decode(FriendlyByteBuf buf) {
        return new WaveChangeAnimationPacket(buf.readInt(), buf.readInt());
    }
    public static void handle(WaveChangeAnimationPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft.getInstance().tell(() -> {
                ClientHUDHandler.triggerWaveChangeAnimation(msg.fromWave, msg.toWave);
            });
        });
        context.setPacketHandled(true);
    }
}