package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.function.Supplier;

import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.player.PlayerCrawlManager;

public class C2SToggleCrawlPacket {
    public C2SToggleCrawlPacket() {}

    public C2SToggleCrawlPacket(FriendlyByteBuf buf) {}

    public static void encode(C2SToggleCrawlPacket msg, FriendlyByteBuf buf) {}

    public static C2SToggleCrawlPacket decode(FriendlyByteBuf buf) { 
        return new C2SToggleCrawlPacket(); 
    }

    public static void handle(C2SToggleCrawlPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                boolean isCrawling = !PlayerCrawlManager.isCrawling(player.getUUID());
                PlayerCrawlManager.setCrawling(player.getUUID(), isCrawling);
                player.refreshDimensions();
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new S2CSyncCrawlStatePacket(player.getUUID(), isCrawling));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}