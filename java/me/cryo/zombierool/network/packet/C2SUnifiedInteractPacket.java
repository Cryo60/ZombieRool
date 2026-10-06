package me.cryo.zombierool.network.packet;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.handlers.ServerInteractionHandler;
import me.cryo.zombierool.network.InteractionType;
import me.cryo.zombierool.network.Packets;

import java.util.function.Supplier;

public class C2SUnifiedInteractPacket {
	private final BlockPos pos;
	private final InteractionType type;
	public C2SUnifiedInteractPacket(BlockPos pos, InteractionType type) {
	    this.pos = pos;
	    this.type = type;
	}
	
	public C2SUnifiedInteractPacket(FriendlyByteBuf buf) {
	    this.pos = buf.readBlockPos();
	    this.type = buf.readEnum(InteractionType.class);
	}
	
	public static C2SUnifiedInteractPacket decode(FriendlyByteBuf buf) {
	    return new C2SUnifiedInteractPacket(buf);
	}
	
	public static void encode(C2SUnifiedInteractPacket msg, FriendlyByteBuf buf) {
	    buf.writeBlockPos(msg.pos);
	    buf.writeEnum(msg.type);
	}
	
	public static void handle(C2SUnifiedInteractPacket msg, Supplier<NetworkEvent.Context> ctx) {
	    Packets.onServer(ctx, player -> ServerInteractionHandler.handleInteraction(player, msg.pos, msg.type));
	}
}