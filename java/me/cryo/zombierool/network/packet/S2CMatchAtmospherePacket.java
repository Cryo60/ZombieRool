package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import java.util.function.Supplier;
public record S2CMatchAtmospherePacket(int remaining,long start,long end) {
    public static void encode(S2CMatchAtmospherePacket msg,FriendlyByteBuf buf){buf.writeInt(msg.remaining);buf.writeLong(msg.start);buf.writeLong(msg.end);}
    public static S2CMatchAtmospherePacket decode(FriendlyByteBuf buf){return new S2CMatchAtmospherePacket(buf.readInt(),buf.readLong(),buf.readLong());}
    public static void handle(S2CMatchAtmospherePacket msg,Supplier<NetworkEvent.Context> ctx){ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->Client.apply(msg)));ctx.get().setPacketHandled(true);}
    private static class Client {static void apply(S2CMatchAtmospherePacket m){me.cryo.zombierool.client.MatchAtmosphere.update(m.remaining,m.start,m.end);}}
}
