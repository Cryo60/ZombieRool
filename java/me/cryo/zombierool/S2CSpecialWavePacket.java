package me.cryo.zombierool.network;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.WaveManager;
import java.util.function.Supplier;

public class S2CSpecialWavePacket {
    private final boolean special;
    public S2CSpecialWavePacket(boolean special) {
        this.special = special;
    }
    public static void encode(S2CSpecialWavePacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.special);
    }
    public static S2CSpecialWavePacket decode(FriendlyByteBuf buf) {
        return new S2CSpecialWavePacket(buf.readBoolean());
    }
    public static void handle(S2CSpecialWavePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            WaveManager.setClientSpecialWave(pkt.special);
        });
        ctx.get().setPacketHandled(true);
    }
}