package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Block;
import java.util.function.Supplier;

public class ObstacleDoorCopyBlockPacket {
	private final BlockPos pos;
	private final ResourceLocation blockToCopyId;
	public ObstacleDoorCopyBlockPacket(BlockPos pos, Block blockToCopy) {
	    this.pos = pos;
	    this.blockToCopyId = ForgeRegistries.BLOCKS.getKey(blockToCopy);
	}
	
	public ObstacleDoorCopyBlockPacket(FriendlyByteBuf buffer) {
	    this.pos = buffer.readBlockPos();
	    this.blockToCopyId = buffer.readResourceLocation();
	}
	
	public void encode(FriendlyByteBuf buffer) {
	    buffer.writeBlockPos(this.pos);
	    buffer.writeResourceLocation(this.blockToCopyId);
	}
	
	public static void handle(ObstacleDoorCopyBlockPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
	    NetworkEvent.Context context = contextSupplier.get();
	    context.enqueueWork(() -> {
	        if (context.getDirection() == NetworkDirection.PLAY_TO_SERVER) {
	            net.minecraft.server.level.ServerPlayer player = context.getSender();
	            if (player == null) return; 
	            net.minecraft.world.level.Level level = player.level();
	            BlockPos blockPos = msg.pos;
	            Block blockToCopy = ForgeRegistries.BLOCKS.getValue(msg.blockToCopyId);
	            
	            if (blockToCopy != null) {
	                net.minecraft.world.level.block.entity.BlockEntity entity = level.getBlockEntity(blockPos);
	                if (entity instanceof me.cryo.zombierool.block.entity.ObstacleDoorBlockEntity obstacleDoorEntity) {
	                    // Use unified mimic setter
	                    obstacleDoorEntity.setMimic(blockToCopy.defaultBlockState());
	                }	
	            }
	        }
	    });
	    context.setPacketHandled(true);
	}
}