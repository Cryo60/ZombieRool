package me.cryo.zombierool.network.packet;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

import me.cryo.zombierool.player.PlayerCrawlManager;

public class S2CSyncCrawlStatePacket {
    public final UUID uuid;
    public final boolean isCrawling;

    public S2CSyncCrawlStatePacket(UUID uuid, boolean isCrawling) {
        this.uuid = uuid;
        this.isCrawling = isCrawling;
    }

    public S2CSyncCrawlStatePacket(FriendlyByteBuf buf) {
        this.uuid = buf.readUUID();
        this.isCrawling = buf.readBoolean();
    }

    public static void encode(S2CSyncCrawlStatePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.uuid);
        buf.writeBoolean(msg.isCrawling);
    }

    public static S2CSyncCrawlStatePacket decode(FriendlyByteBuf buf) {
        return new S2CSyncCrawlStatePacket(buf);
    }

    public static void handle(S2CSyncCrawlStatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                PlayerCrawlManager.setCrawling(msg.uuid, msg.isCrawling);
                if (Minecraft.getInstance().level != null) {
                    Player player = Minecraft.getInstance().level.getPlayerByUUID(msg.uuid);
                    if (player != null) {
                        player.refreshDimensions();
                    }
                }
            });
        });
        ctx.get().setPacketHandled(true);
    }
}