package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.career.CareerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class C2SBuyCamoPacket {
    private final String camoId;

    public C2SBuyCamoPacket(String camoId) {
        this.camoId = camoId;
    }

    public C2SBuyCamoPacket(FriendlyByteBuf buf) {
        this.camoId = buf.readUtf();
    }

    public static void encode(C2SBuyCamoPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.camoId);
    }

    public static C2SBuyCamoPacket decode(FriendlyByteBuf buf) {
        return new C2SBuyCamoPacket(buf);
    }

    public static void handle(C2SBuyCamoPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                CareerManager.buyCamo(player, msg.camoId);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}