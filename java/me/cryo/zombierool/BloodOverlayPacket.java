package me.cryo.zombierool.network;

import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.client.BloodOverlayManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class BloodOverlayPacket {
	private final BlockPos pos;
	private final Direction face;
	private final int textureIndex;
	private final boolean add;
	private final int rotation;
	public BloodOverlayPacket(BlockPos pos, Direction face, int textureIndex, int rotation, boolean add) {
	    this.pos = pos;
	    this.face = face;
	    this.textureIndex = textureIndex;
	    this.rotation = rotation;
	    this.add = add;
	}
	
	public BloodOverlayPacket(FriendlyByteBuf buf) {
	    this.pos = buf.readBlockPos();
	    this.face = buf.readEnum(Direction.class);
	    this.textureIndex = buf.readInt();
	    this.rotation = buf.readInt();
	    this.add = buf.readBoolean();
	}
	
	public void encode(FriendlyByteBuf buf) {
	    buf.writeBlockPos(pos);
	    buf.writeEnum(face);
	    buf.writeInt(textureIndex);
	    buf.writeInt(rotation);
	    buf.writeBoolean(add);
	}
	
	public void handle(Supplier<NetworkEvent.Context> ctx) {
	    ctx.get().enqueueWork(() -> {
	        // Suppression du System.out.println("DEBUG Packet...")
	        
	        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
	            if (add) {
				    BloodOverlayManager.addOverlay(pos, face, textureIndex, rotation);
				} else {
				    BloodOverlayManager.removeOverlay(pos, face);
				}
	        });
	
	        if (ctx.get().getDirection().getReceptionSide().isServer()) {
	            var sender = ctx.get().getSender();
	            if (sender != null && sender.level() instanceof ServerLevel serverLevel) {
	                WorldConfig config = WorldConfig.get(serverLevel);
	                String key = pos.getX() + "_" + pos.getY() + "_" + pos.getZ() + "_" + face.getName();
	                if (add) {
					    config.addBloodOverlay(key, textureIndex + ":" + rotation);
					} else {
					    config.removeBloodOverlay(key);
					}
	            }
	        }
	    });
	    ctx.get().setPacketHandled(true);
	}
}