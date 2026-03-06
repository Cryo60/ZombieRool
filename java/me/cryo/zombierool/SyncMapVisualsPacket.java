package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import me.cryo.zombierool.client.LinkRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class SyncMapVisualsPacket {
	private final List<BlockPos> playerSpawners;
	public SyncMapVisualsPacket(List<BlockPos> playerSpawners) {
	    this.playerSpawners = playerSpawners;
	}
	
	public static void encode(SyncMapVisualsPacket msg, FriendlyByteBuf buf) {
	    buf.writeInt(msg.playerSpawners.size());
	    for (BlockPos pos : msg.playerSpawners) {
	        buf.writeBlockPos(pos);
	    }
	}
	
	public static SyncMapVisualsPacket decode(FriendlyByteBuf buf) {
	    int count = buf.readInt();
	    List<BlockPos> list = new ArrayList<>();
	    for (int i = 0; i < count; i++) {
	        list.add(buf.readBlockPos());
	    }
	    return new SyncMapVisualsPacket(list);
	}
	
	public static void handle(SyncMapVisualsPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
	    NetworkEvent.Context context = contextSupplier.get();
	    context.enqueueWork(() -> {
	        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
	            LinkRenderer.clientPlayerSpawners = msg.playerSpawners;
	        });
	    });
	    context.setPacketHandled(true);
	}
}