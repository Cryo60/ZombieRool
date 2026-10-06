package me.cryo.zombierool.network.packet;
import me.cryo.zombierool.career.ServerCareerManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class C2SSyncEquippedSkinsPacket {

    private final Map<String, String> skins;

    public C2SSyncEquippedSkinsPacket(Map<String, String> skins) {
        this.skins = skins;
    }

    public C2SSyncEquippedSkinsPacket(FriendlyByteBuf buf) {
        this.skins = new HashMap<>();
        int size = buf.readInt();
        for (int i = 0; i < size; i++) {
            skins.put(buf.readUtf(), buf.readUtf());
        }
    }

    public static void encode(C2SSyncEquippedSkinsPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.skins.size());
        for (Map.Entry<String, String> entry : msg.skins.entrySet()) {
            buf.writeUtf(entry.getKey());
            buf.writeUtf(entry.getValue());
        }
    }

    public static C2SSyncEquippedSkinsPacket decode(FriendlyByteBuf buf) {
        return new C2SSyncEquippedSkinsPacket(buf);
    }

    public static void handle(C2SSyncEquippedSkinsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                ServerCareerManager.setEquippedSkins(player, msg.skins);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
