package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.core.manager.DynamicResourceManager;

import java.util.function.Supplier;

public class S2CSyncDynamicSkinPacket {
    private final String mobType;
    private final String skinId;
    private final byte[] textureData;

    public S2CSyncDynamicSkinPacket(String mobType, String skinId, byte[] textureData) {
        this.mobType = mobType;
        this.skinId = skinId;
        this.textureData = textureData;
    }

    public static void encode(S2CSyncDynamicSkinPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.mobType);
        buf.writeUtf(msg.skinId);
        buf.writeByteArray(msg.textureData);
    }

    public static S2CSyncDynamicSkinPacket decode(FriendlyByteBuf buf) {
        return new S2CSyncDynamicSkinPacket(buf.readUtf(), buf.readUtf(), buf.readByteArray());
    }

    public static void handle(S2CSyncDynamicSkinPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                DynamicResourceManager.registerClientSkin(msg.mobType, msg.skinId, msg.textureData);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}