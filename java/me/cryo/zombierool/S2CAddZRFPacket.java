package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.client.career.LocalCareerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CAddZRFPacket {
    private final int amount;

    public S2CAddZRFPacket(int amount) {
        this.amount = amount;
    }

    public S2CAddZRFPacket(FriendlyByteBuf buf) {
        this.amount = buf.readInt();
    }

    public static void encode(S2CAddZRFPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.amount);
    }

    public static S2CAddZRFPacket decode(FriendlyByteBuf buf) {
        return new S2CAddZRFPacket(buf);
    }

    public static void handle(S2CAddZRFPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                LocalCareerManager.addZRF(msg.amount, "message.zombierool.career.zrf_earned");
            });
        });
        ctx.get().setPacketHandled(true);
    }
}