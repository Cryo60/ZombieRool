package me.cryo.zombierool.network;

import net.minecraft.network.FriendlyByteBuf; 
import net.minecraftforge.network.NetworkEvent; 
import java.util.function.Supplier; 
import me.cryo.zombierool.block.system.ObstacleDoorSystem;

public record C2SObstacleDoorGUIPacket(int x, int y, int z, int prix, String canal, boolean isCreative) {
    public static void encode(C2SObstacleDoorGUIPacket m, FriendlyByteBuf b) { b.writeInt(m.x()); b.writeInt(m.y()); b.writeInt(m.z()); b.writeInt(m.prix()); b.writeUtf(m.canal()); b.writeBoolean(m.isCreative()); }
    public static C2SObstacleDoorGUIPacket decode(FriendlyByteBuf b) { return new C2SObstacleDoorGUIPacket(b.readInt(), b.readInt(), b.readInt(), b.readInt(), b.readUtf(), b.readBoolean()); }
    public static void handle(C2SObstacleDoorGUIPacket m, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> { 
            var s = ctx.get().getSender(); 
            if (s != null) ObstacleDoorSystem.handlePacket(s, m); 
        }); 
        ctx.get().setPacketHandled(true);
    }
}