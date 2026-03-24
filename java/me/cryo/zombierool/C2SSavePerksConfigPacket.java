package me.cryo.zombierool.network;
import net.minecraft.core.BlockPos; 
import net.minecraft.network.FriendlyByteBuf; 
import net.minecraftforge.network.NetworkEvent; 
import java.util.function.Supplier; 
import me.cryo.zombierool.block.system.PerksSystem.PerksAColaBlockEntity; 

public record C2SSavePerksConfigPacket(BlockPos pos, int price, String perkId) {
    public static void encode(C2SSavePerksConfigPacket m, FriendlyByteBuf b) { b.writeBlockPos(m.pos()); b.writeInt(m.price()); b.writeUtf(m.perkId()); }
    public static C2SSavePerksConfigPacket decode(FriendlyByteBuf b) { return new C2SSavePerksConfigPacket(b.readBlockPos(), b.readInt(), b.readUtf(32767)); }
    public static void handle(C2SSavePerksConfigPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { 
            var s = ctx.get().getSender(); 
            if (s != null && s.hasPermissions(2) && s.level().getBlockEntity(m.pos()) instanceof PerksAColaBlockEntity be) { 
                be.setSavedPrice(m.price()); 
                be.setSavedPerkId(m.perkId()); 
            }
        }); 
        ctx.get().setPacketHandled(true);
    }
}