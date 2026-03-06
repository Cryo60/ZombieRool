package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.configuration.HalloweenConfig;
import me.cryo.zombierool.configuration.HalloweenConfig.HalloweenMode;
import java.util.function.Supplier;

public class HalloweenConfigSyncPacket {
	private final HalloweenMode mode;
	public HalloweenConfigSyncPacket(HalloweenMode mode) {
	    this.mode = mode;
	}
	
	public HalloweenConfigSyncPacket(FriendlyByteBuf buf) {
	    this.mode = buf.readEnum(HalloweenMode.class);
	}
	
	public void encode(FriendlyByteBuf buf) {
	    buf.writeEnum(this.mode);
	}
	
	public static HalloweenConfigSyncPacket decode(FriendlyByteBuf buf) {
	    return new HalloweenConfigSyncPacket(buf);
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
	    ctx.get().enqueueWork(() -> {
	        ServerPlayer player = ctx.get().getSender();
	        if (player != null && player.hasPermissions(2)) {
	            HalloweenConfig.setHalloweenMode(this.mode);
	        }
	    });
	    ctx.get().setPacketHandled(true);
	}
}