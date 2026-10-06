package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSyncAimPacket {
    private final boolean aiming;

    public C2SSyncAimPacket(boolean aiming) {
        this.aiming = aiming;
    }

    public C2SSyncAimPacket(FriendlyByteBuf buf) {
        this.aiming = buf.readBoolean();
    }

    public static void encode(C2SSyncAimPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.aiming);
    }

    public static C2SSyncAimPacket decode(FriendlyByteBuf buf) {
        return new C2SSyncAimPacket(buf);
    }

    public static void handle(C2SSyncAimPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getPersistentData().putBoolean("zr_ads", msg.aiming);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
