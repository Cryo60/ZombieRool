package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import me.cryo.zombierool.client.gui.SecretConsoleScreen;

import java.util.function.Supplier;

public class S2CSecretConsoleLogPacket {
    private final String message;

    public S2CSecretConsoleLogPacket(String message) {
        this.message = message;
    }

    public S2CSecretConsoleLogPacket(FriendlyByteBuf buf) {
        this.message = buf.readUtf();
    }

    public static void encode(S2CSecretConsoleLogPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.message);
    }

    public static S2CSecretConsoleLogPacket decode(FriendlyByteBuf buf) {
        return new S2CSecretConsoleLogPacket(buf);
    }

    public static void handle(S2CSecretConsoleLogPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                SecretConsoleScreen.receiveLog(msg.message);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}