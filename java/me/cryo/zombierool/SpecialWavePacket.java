package me.cryo.zombierool.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.WaveManager;
import java.util.function.Supplier;

public class SpecialWavePacket {
    private final boolean special;
    public SpecialWavePacket(boolean special) {
        this.special = special;
    }
    public static void encode(SpecialWavePacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.special);
    }
    public static SpecialWavePacket decode(FriendlyByteBuf buf) {
        return new SpecialWavePacket(buf.readBoolean());
    }
    public static void handle(SpecialWavePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            WaveManager.setClientSpecialWave(pkt.special);
        });
        ctx.get().setPacketHandled(true);
    }
}