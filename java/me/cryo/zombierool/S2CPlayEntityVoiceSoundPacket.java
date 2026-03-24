package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;
import me.cryo.zombierool.client.ClientVoiceManager;
public class S2CPlayEntityVoiceSoundPacket {
    private final int entityId;
    private final String soundEventName;
    public S2CPlayEntityVoiceSoundPacket(int entityId, String soundEventName) {
        this.entityId = entityId;
        this.soundEventName = soundEventName;
    }
    public S2CPlayEntityVoiceSoundPacket(FriendlyByteBuf buf) {
        this.entityId = buf.readInt();
        this.soundEventName = buf.readUtf();
    }
    public static void encode(S2CPlayEntityVoiceSoundPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeUtf(msg.soundEventName);
    }
    public static S2CPlayEntityVoiceSoundPacket decode(FriendlyByteBuf buf) {
        return new S2CPlayEntityVoiceSoundPacket(buf);
    }
    public static void handle(S2CPlayEntityVoiceSoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                ClientVoiceManager.handleVoicePacket(msg.entityId, msg.soundEventName);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}