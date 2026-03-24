package me.cryo.zombierool.network;

import net.minecraft.core.BlockPos; 
import net.minecraft.network.FriendlyByteBuf; 
import net.minecraftforge.network.NetworkEvent; 
import java.util.function.Supplier; 
import me.cryo.zombierool.block.system.BlindBuySystem.BlindBuyCabinetBlockEntity;

public record C2SSetBlindBuyConfigPacket(BlockPos pos, int price, int redstoneMode) {
    public static void encode(C2SSetBlindBuyConfigPacket m, FriendlyByteBuf b) { b.writeBlockPos(m.pos()); b.writeInt(m.price()); b.writeInt(m.redstoneMode()); }
    public static C2SSetBlindBuyConfigPacket decode(FriendlyByteBuf b) { return new C2SSetBlindBuyConfigPacket(b.readBlockPos(), b.readInt(), b.readInt()); }
    public static void handle(C2SSetBlindBuyConfigPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { 
            var s = ctx.get().getSender(); 
            if (s != null && s.hasPermissions(2) && s.level().getBlockEntity(m.pos()) instanceof BlindBuyCabinetBlockEntity be) { 
                be.setPrice(m.price()); 
            }
        }); 
        ctx.get().setPacketHandled(true);
    }
}