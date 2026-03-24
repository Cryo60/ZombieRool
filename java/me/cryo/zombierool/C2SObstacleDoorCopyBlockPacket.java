package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

public class C2SObstacleDoorCopyBlockPacket {
	private final BlockPos pos;
	private final ResourceLocation blockToCopyId;

	public C2SObstacleDoorCopyBlockPacket(BlockPos pos, Block blockToCopy) {
	    this.pos = pos;
	    this.blockToCopyId = ForgeRegistries.BLOCKS.getKey(blockToCopy);
	}

	public C2SObstacleDoorCopyBlockPacket(FriendlyByteBuf buffer) {
	    this.pos = buffer.readBlockPos();
	    this.blockToCopyId = buffer.readResourceLocation();
	}

	public void encode(FriendlyByteBuf buffer) {
	    buffer.writeBlockPos(this.pos);
	    buffer.writeResourceLocation(this.blockToCopyId);
	}

	public static void handle(C2SObstacleDoorCopyBlockPacket msg, Supplier<NetworkEvent.Context> contextSupplier) {
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
	                if (entity instanceof me.cryo.zombierool.block.system.ObstacleDoorSystem.ObstacleDoorBlockEntity obstacleDoorEntity) {
	                    net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
	                        player.position(), player.getDirection().getOpposite(), blockPos, false
	                    );
	                    net.minecraft.world.item.ItemStack dummyStack = new net.minecraft.world.item.ItemStack(blockToCopy);
	                    BlockState placementState = me.cryo.zombierool.block.system.MimicSystem.getStateForMimic(
	                        player, net.minecraft.world.InteractionHand.MAIN_HAND, dummyStack, hit, blockToCopy
	                    );
	                    obstacleDoorEntity.setMimic(placementState);
	                }	
	            }
	        }
	    });
	    context.setPacketHandled(true);
	}
}