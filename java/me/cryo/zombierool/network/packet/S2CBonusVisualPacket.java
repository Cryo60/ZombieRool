package me.cryo.zombierool.network.packet;
import java.util.*;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
public record S2CBonusVisualPacket(List<Entry> entries){
 public record Entry(UUID uuid,String type,double x,double y,double z,int remaining){}
 public static void encode(S2CBonusVisualPacket p,FriendlyByteBuf b){b.writeVarInt(p.entries.size());for(var e:p.entries){b.writeUUID(e.uuid);b.writeUtf(e.type,64);b.writeDouble(e.x);b.writeDouble(e.y);b.writeDouble(e.z);b.writeVarInt(e.remaining);}}
 public static S2CBonusVisualPacket decode(FriendlyByteBuf b){int count=b.readVarInt();if(count<0||count>256)throw new IllegalArgumentException("Bonus visual count");var rows=new ArrayList<Entry>();for(int i=0;i<count;i++)rows.add(new Entry(b.readUUID(),b.readUtf(64),b.readDouble(),b.readDouble(),b.readDouble(),b.readVarInt()));return new S2CBonusVisualPacket(rows);}
 public static void handle(S2CBonusVisualPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->me.cryo.zombierool.client.BonusVisuals.receive(p.entries));c.get().setPacketHandled(true);}
}
