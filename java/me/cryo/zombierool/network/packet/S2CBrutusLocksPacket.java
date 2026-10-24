package me.cryo.zombierool.network.packet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;
public record S2CBrutusLocksPacket(CompoundTag data){
    public static void encode(S2CBrutusLocksPacket p,FriendlyByteBuf b){b.writeNbt(p.data);}
    public static S2CBrutusLocksPacket decode(FriendlyByteBuf b){return new S2CBrutusLocksPacket(b.readNbt());}
    public static void handle(S2CBrutusLocksPacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.BrutusChains.receive(p.data)));c.get().setPacketHandled(true);}
}
