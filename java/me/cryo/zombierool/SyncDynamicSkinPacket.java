package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.core.manager.DynamicResourceManager;

import java.util.function.Supplier;

public class SyncDynamicSkinPacket {
    private final String mobType;
    private final String skinId;
    private final byte[] textureData;

    public SyncDynamicSkinPacket(String mobType, String skinId, byte[] textureData) {
        this.mobType = mobType;
        this.skinId = skinId;
        this.textureData = textureData;
    }

    public static void encode(SyncDynamicSkinPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.mobType);
        buf.writeUtf(msg.skinId);
        buf.writeByteArray(msg.textureData);
    }

    public static SyncDynamicSkinPacket decode(FriendlyByteBuf buf) {
        return new SyncDynamicSkinPacket(buf.readUtf(), buf.readUtf(), buf.readByteArray());
    }

    public static void handle(SyncDynamicSkinPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                DynamicResourceManager.registerClientSkin(msg.mobType, msg.skinId, msg.textureData);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}