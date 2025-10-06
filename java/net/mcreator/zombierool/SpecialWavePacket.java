package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
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
            // Met à jour l'état côté client
            net.mcreator.zombierool.client.ClientWaveState.setSpecialWave(pkt.special);
        });
        ctx.get().setPacketHandled(true);
    }
}