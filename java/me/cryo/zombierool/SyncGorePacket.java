package me.cryo.zombierool.network;

import me.cryo.zombierool.core.manager.GoreManager;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class SyncGorePacket {
    private final int entityId;
    private final CompoundTag goreData;

    public SyncGorePacket(int entityId, CompoundTag goreData) {
        this.entityId = entityId;
        this.goreData = goreData;
    }

    public static void encode(SyncGorePacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.entityId);
        buf.writeNbt(msg.goreData);
    }

    public static SyncGorePacket decode(FriendlyByteBuf buf) {
        return new SyncGorePacket(buf.readInt(), buf.readNbt());
    }

    public static void handle(SyncGorePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientHandler.handle(msg));
        });
        ctx.get().setPacketHandled(true);
    }

    private static class ClientHandler {
        public static void handle(SyncGorePacket msg) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null) {
                Entity entity = mc.level.getEntity(msg.entityId);
                if (entity != null) {
                    CompoundTag data = entity.getPersistentData();
                    // Mise à jour des NBT côté client pour que le Renderer les lise
                    if (msg.goreData.contains("h")) data.putBoolean(GoreManager.TAG_NO_HEAD, msg.goreData.getBoolean("h"));
                    if (msg.goreData.contains("la")) data.putBoolean(GoreManager.TAG_NO_LEFT_ARM, msg.goreData.getBoolean("la"));
                    if (msg.goreData.contains("ra")) data.putBoolean(GoreManager.TAG_NO_RIGHT_ARM, msg.goreData.getBoolean("ra"));
                }
            }
        }
    }
}