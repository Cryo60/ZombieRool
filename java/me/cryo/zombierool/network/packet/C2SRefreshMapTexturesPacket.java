package me.cryo.zombierool.network.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import java.util.function.Supplier;

public final class C2SRefreshMapTexturesPacket {
    public static void encode(C2SRefreshMapTexturesPacket p,FriendlyByteBuf b){}
    public static C2SRefreshMapTexturesPacket decode(FriendlyByteBuf b){return new C2SRefreshMapTexturesPacket();}
    public static void handle(C2SRefreshMapTexturesPacket p,Supplier<NetworkEvent.Context> ctx){
        ctx.get().enqueueWork(()->{
            var player=ctx.get().getSender();if(player==null||!player.isCreative())return;
            long now=player.serverLevel().getGameTime();var data=player.getPersistentData();
            if(data.contains("zr_texture_refresh")&&now-data.getLong("zr_texture_refresh")<20)return;
            data.putLong("zr_texture_refresh",now);
            var level=player.server.overworld();me.cryo.zombierool.maptexture.MapTextures.syncFolder(level);
            me.cryo.zombierool.maptexture.MapTextureSync.refresh(level,null);
        });ctx.get().setPacketHandled(true);
    }
}
