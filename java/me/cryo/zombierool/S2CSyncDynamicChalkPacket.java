package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.core.manager.DynamicResourceManager;

import java.util.function.Supplier;

public class S2CSyncDynamicChalkPacket {
    private final String chalkId;
    private final byte[] textureData;

    public S2CSyncDynamicChalkPacket(String chalkId, byte[] textureData) {
        this.chalkId = chalkId;
        this.textureData = textureData;
    }

    public static void encode(S2CSyncDynamicChalkPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.chalkId);
        buf.writeByteArray(msg.textureData);
    }

    public static S2CSyncDynamicChalkPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncDynamicChalkPacket(buf.readUtf(), buf.readByteArray());
    }

    public static void handle(S2CSyncDynamicChalkPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                DynamicResourceManager.registerClientChalk(msg.chalkId, msg.textureData);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}