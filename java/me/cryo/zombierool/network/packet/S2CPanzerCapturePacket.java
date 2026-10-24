package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.function.Supplier;
public record S2CPanzerCapturePacket(boolean caught){
    public void encode(FriendlyByteBuf b){b.writeBoolean(caught);}
    public static S2CPanzerCapturePacket decode(FriendlyByteBuf b){return new S2CPanzerCapturePacket(b.readBoolean());}
    public static void handle(S2CPanzerCapturePacket p,Supplier<NetworkEvent.Context> c){c.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.PanzerCaptureInput.caught=p.caught));c.get().setPacketHandled(true);}
}
