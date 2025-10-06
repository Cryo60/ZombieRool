package net.mcreator.zombierool.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;
import net.mcreator.zombierool.block.entity.PerksLowerBlockEntity;

import java.util.function.Supplier;

public class SavePerksConfigMessage {
    private final BlockPos pos;
    private final int price;
    private final String perkId;

    public SavePerksConfigMessage(BlockPos pos, int price, String perkId) {
        this.pos = pos;
        this.price = price;
        this.perkId = perkId;
    }

    public static SavePerksConfigMessage decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int price = buf.readInt();
        String perkId = buf.readUtf(32767);
        return new SavePerksConfigMessage(pos, price, perkId);
    }

    public static void encode(SavePerksConfigMessage msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.price);
        buf.writeUtf(msg.perkId);
    }

    public static void handle(SavePerksConfigMessage msg, Supplier<NetworkEvent.Context> ctxSupplier) {
	    NetworkEvent.Context ctx = ctxSupplier.get();
	    ctx.enqueueWork(() -> {
	        ServerPlayer sender = ctx.getSender();
	        if (sender == null) return;
	        // Remplace getLevel() par level()
	        var world = sender.level();
	        var be = world.getBlockEntity(msg.pos);
	        if (be instanceof PerksLowerBlockEntity perksBE) {
	            perksBE.setSavedPrice(msg.price);
	            perksBE.setSavedPerkId(msg.perkId);
	        }
	    });
	    ctx.setPacketHandled(true);
	}
}
