package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.WaveManager;
import java.util.function.Supplier;

public class RequestConfigMenuPacket {
    public RequestConfigMenuPacket() {}
    public RequestConfigMenuPacket(FriendlyByteBuf buf) {}
    public void encode(FriendlyByteBuf buf) {}

    public static void handle(RequestConfigMenuPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null && player.hasPermissions(2)) {
                CompoundTag tag = new CompoundTag();
                WorldConfig.get(player.serverLevel()).saveEditable(tag);
                
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new OpenConfigMenuPacket(tag, WaveManager.isGameRunning(), WaveManager.getCurrentWave())
                );
            }
        });
        ctx.get().setPacketHandled(true);
    }
}