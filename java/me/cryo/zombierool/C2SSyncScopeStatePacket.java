package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SSyncScopeStatePacket {
    private final boolean isScoping;

    public C2SSyncScopeStatePacket(boolean isScoping) {
        this.isScoping = isScoping;
    }

    public C2SSyncScopeStatePacket(FriendlyByteBuf buf) {
        this.isScoping = buf.readBoolean();
    }

    public static void encode(C2SSyncScopeStatePacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.isScoping);
    }

    public static C2SSyncScopeStatePacket decode(FriendlyByteBuf buf) {
        return new C2SSyncScopeStatePacket(buf);
    }

    public static void handle(C2SSyncScopeStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                player.getPersistentData().putBoolean("zr_is_scoping", msg.isScoping);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}