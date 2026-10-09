package me.cryo.zombierool.network.packet;

import me.cryo.zombierool.maptexture.MapTexturePixels;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;
import java.util.function.Supplier;

public record S2CMapTextureSlotPacket(int slot,String name,String sound,byte[][] faces){
    public static void encode(S2CMapTextureSlotPacket p,FriendlyByteBuf b){b.writeVarInt(p.slot);b.writeUtf(p.name,32);b.writeUtf(p.sound,32);for(byte[] face:p.faces)b.writeByteArray(face);}
    public static S2CMapTextureSlotPacket decode(FriendlyByteBuf b){
        int slot=b.readVarInt();if(slot<0||slot>=32)throw new IllegalArgumentException("Texture slot");
        String name=b.readUtf(32),sound=b.readUtf(32);byte[][] faces=new byte[MapTexturePixels.FACES.length][];
        for(int i=0;i<faces.length;i++)faces[i]=b.readByteArray(MapTexturePixels.MAX_FACE_BYTES);
        return new S2CMapTextureSlotPacket(slot,name,sound,faces);
    }
    public static void handle(S2CMapTextureSlotPacket p,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->me.cryo.zombierool.client.MapTextureClient.receive(p.slot,p.name,p.sound,p.faces)));
        ctx.get().setPacketHandled(true);
    }
}
