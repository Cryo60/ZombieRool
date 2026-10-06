package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.career.ServerCareerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class C2SSyncEquippedCamosPacket {

    private final Map<String, String> camos;

    public C2SSyncEquippedCamosPacket(Map<String, String> camos) {
        this.camos = camos;
    }

    public C2SSyncEquippedCamosPacket(FriendlyByteBuf buf) {
        this.camos = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            camos.put(buf.readUtf(), buf.readUtf());
        }
    }

    public static void encode(C2SSyncEquippedCamosPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.camos.size());
        for (Map.Entry<String, String> entry : msg.camos.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    public static C2SSyncEquippedCamosPacket decode(FriendlyByteBuf buf) {
        return new C2SSyncEquippedCamosPacket(buf);
    }

    public static void handle(C2SSyncEquippedCamosPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerCareerManager.setEquippedCamos(player, msg.camos);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}