package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.handlers.LethalWeaponManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SUpdateLethalStatePacket {
    private final int state;

    public C2SUpdateLethalStatePacket(int state) {
        this.state = state;
    }

    public static void encode(C2SUpdateLethalStatePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.state);
    }

    public static C2SUpdateLethalStatePacket decode(FriendlyByteBuf buf) {
        return new C2SUpdateLethalStatePacket(buf.readInt());
    }

    public static void handle(C2SUpdateLethalStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                LethalWeaponManager.handleStateChange(player, msg.state);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}