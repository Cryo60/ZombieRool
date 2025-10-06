// src/main/java/net/mcreator/zombierool/network/CaptureWallTexturePacket.java
package net.mcreator.zombierool.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.mcreator.zombierool.block.entity.BuyWallWeaponBlockEntity;
import net.minecraft.world.level.Level;

import java.util.function.Supplier;

public record CaptureWallTexturePacket(BlockPos wallPos, ResourceLocation blockRL) {
    public static void encode(CaptureWallTexturePacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.wallPos());
        buf.writeResourceLocation(pkt.blockRL());
    }

    public static CaptureWallTexturePacket decode(FriendlyByteBuf buf) {
        return new CaptureWallTexturePacket(buf.readBlockPos(), buf.readResourceLocation());
    }

    public static void handle(CaptureWallTexturePacket pkt, Supplier<NetworkEvent.Context> ctx) {
	    ctx.get().enqueueWork(() -> {
	        ServerPlayer sender = ctx.get().getSender();
	        if (sender == null) return;
	        Level lvl = sender.getCommandSenderWorld();
	        if (!(lvl instanceof ServerLevel server)) return;
	        BlockEntity be = server.getBlockEntity(pkt.wallPos());
	        if (!(be instanceof BuyWallWeaponBlockEntity weaponBe)) return;
	
	        weaponBe.setCapturedBlock(pkt.blockRL());
	        weaponBe.setChanged();
	        server.sendBlockUpdated(
	            pkt.wallPos(),
	            weaponBe.getBlockState(),
	            weaponBe.getBlockState(),
	            3
	        );
	    });
	    ctx.get().setPacketHandled(true);
	}
}
