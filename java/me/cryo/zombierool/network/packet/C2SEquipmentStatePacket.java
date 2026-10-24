package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.gameplay.EquipmentSystem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public record C2SEquipmentStatePacket(int slot, int state) {
    public static void encode(C2SEquipmentStatePacket p,FriendlyByteBuf b){b.writeByte(p.slot);b.writeByte(p.state);}
    public static C2SEquipmentStatePacket decode(FriendlyByteBuf b){return new C2SEquipmentStatePacket(b.readUnsignedByte(),b.readUnsignedByte());}
    public static void handle(C2SEquipmentStatePacket p,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->{var player=ctx.get().getSender();if(player!=null&&p.slot<=2&&p.state<=2)EquipmentSystem.input(player,p.slot,p.state);});
        ctx.get().setPacketHandled(true);
    }
}
