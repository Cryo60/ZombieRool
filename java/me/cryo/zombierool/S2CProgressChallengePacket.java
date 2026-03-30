package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.client.career.LocalCareerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class S2CProgressChallengePacket {
    private final String type;
    private final int amount;

    public S2CProgressChallengePacket(String type, int amount) {
        this.type = type;
        this.amount = amount;
    }

    public S2CProgressChallengePacket(FriendlyByteBuf buf) {
        this.type = buf.readUtf();
        this.amount = buf.readInt();
    }

    public static void encode(S2CProgressChallengePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.type);
        buf.writeInt(msg.amount);
    }

    public static S2CProgressChallengePacket decode(FriendlyByteBuf buf) {
        return new S2CProgressChallengePacket(buf);
    }

    public static void handle(S2CProgressChallengePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                LocalCareerManager.progressChallenge(LocalCareerManager.ChallengeType.valueOf(msg.type), msg.amount);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}