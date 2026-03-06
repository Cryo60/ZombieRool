package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import me.cryo.zombierool.PointManager;
import java.util.function.Supplier;

public class PointGainPacket {
	private final int gainedAmount;
	public PointGainPacket(int gainedAmount) {
	    this.gainedAmount = gainedAmount;
	}
	
	public static PointGainPacket decode(FriendlyByteBuf buffer) {
	    return new PointGainPacket(buffer.readInt());
	}
	
	public void encode(FriendlyByteBuf buffer) {
	    buffer.writeInt(gainedAmount);
	}
	
	public static void handle(PointGainPacket message, Supplier<NetworkEvent.Context> contextSupplier) {
	    NetworkEvent.Context context = contextSupplier.get();
	    context.enqueueWork(() -> {
	        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
	            net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;
	            if (clientPlayer != null) {
	                long now = clientPlayer.level().getGameTime();
	                // Modification: Accumulate points if received within a short timeframe
	                PointManager.PointGainInfo existingInfo = PointManager.LAST_POINT_GAINS.get(clientPlayer.getUUID());
	                int newAmount = message.gainedAmount;
	                
	                if (existingInfo != null && (now - existingInfo.timestamp) < 5) {
	                    newAmount += existingInfo.amount;
	                }
	
	                PointManager.LAST_POINT_GAINS.put(
	                    clientPlayer.getUUID(),
	                    new PointManager.PointGainInfo(newAmount, now)
	                );
	            }
	        }
	    });
	    context.setPacketHandled(true);
	}
}