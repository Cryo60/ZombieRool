package me.cryo.zombierool;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import me.cryo.zombierool.block.entity.BuyWallWeaponBlockEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

public record SetWallWeaponConfigPacket(BlockPos pos, int price, ItemStack stack) {

    public static void encode(SetWallWeaponConfigPacket pkt, FriendlyByteBuf buf) {
        buf.writeBlockPos(pkt.pos());
        buf.writeInt(pkt.price());
        buf.writeItem(pkt.stack());
    }

    public static SetWallWeaponConfigPacket decode(FriendlyByteBuf buf) {
        return new SetWallWeaponConfigPacket(
            buf.readBlockPos(),
            buf.readInt(),
            buf.readItem()
        );
    }

    public static void handle(SetWallWeaponConfigPacket pkt, Supplier<NetworkEvent.Context> ctx) {
	    ctx.get().enqueueWork(() -> {
	        ServerPlayer sender = ctx.get().getSender();
	        if (sender == null) return;

	        Level lvl = sender.getCommandSenderWorld();
	        if (!(lvl instanceof ServerLevel serverLevel)) return;

	        BlockEntity be = serverLevel.getBlockEntity(pkt.pos());
	        if (!(be instanceof BuyWallWeaponBlockEntity weaponBe)) return;

	        weaponBe.setPrice(pkt.price());
	        
	        // Only store the item stack, don't rely on ResourceLocation
	        weaponBe.getCapability(ForgeCapabilities.ITEM_HANDLER, null).ifPresent(cap -> {
	            if (cap instanceof ItemStackHandler handler) {
	                handler.setStackInSlot(0, pkt.stack().copy());
	            }
	        });

	        weaponBe.setChanged();
	        serverLevel.sendBlockUpdated(
	            pkt.pos(),
	            weaponBe.getBlockState(),
	            weaponBe.getBlockState(),
	            3
	        );
	    });
	    ctx.get().setPacketHandled(true);
	}
}