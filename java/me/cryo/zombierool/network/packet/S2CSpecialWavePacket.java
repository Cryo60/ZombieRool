package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.gameplay.WaveManager;
import java.util.function.Supplier;

public class S2CSpecialWavePacket {
    private final boolean special,hellhoundFog;
    public S2CSpecialWavePacket(boolean special) {
        this(special,special);
    }
    public S2CSpecialWavePacket(boolean special,boolean hellhoundFog){
        this.special=special;this.hellhoundFog=hellhoundFog;
    }
    public static void encode(S2CSpecialWavePacket pkt, FriendlyByteBuf buf) {
        buf.writeBoolean(pkt.special);buf.writeBoolean(pkt.hellhoundFog);
    }
    public static S2CSpecialWavePacket decode(FriendlyByteBuf buf) {
        return new S2CSpecialWavePacket(buf.readBoolean(),buf.readBoolean());
    }
    public static void handle(S2CSpecialWavePacket pkt, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            WaveManager.setClientSpecialWave(pkt.special);
            WaveManager.setClientHellhoundFog(pkt.hellhoundFog);
        });
        ctx.get().setPacketHandled(true);
    }
}
