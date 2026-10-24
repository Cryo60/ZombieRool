package me.cryo.zombierool.gameplay;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.*;
import net.minecraftforge.network.*;

public final class MapAtmospherePackets {
    private record Texture(String id,int index,int count,byte[] data){}
    private record Update(String action,CompoundTag settings){}
    private record Request(String action,CompoundTag settings){}
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new ResourceLocation("zombierool","map_atmosphere"),()->"1","1"::equals,"1"::equals);
    public static void texture(ServerPlayer p,String id,byte[] data){int size=256*1024,count=(data.length+size-1)/size;for(int i=0;i<count;i++)CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Texture(id,i,count,java.util.Arrays.copyOfRange(data,i*size,Math.min(data.length,(i+1)*size))));}
    public static void register(){
        CHANNEL.registerMessage(2,Texture.class,(p,b)->{b.writeUtf(p.id,256);b.writeVarInt(p.index);b.writeVarInt(p.count);b.writeByteArray(p.data);},b->new Texture(b.readUtf(256),b.readVarInt(),b.readVarInt(),b.readByteArray(256*1024)),(p,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.MapAtmosphereClient.texture(p.id,p.index,p.count,p.data)));c.get().setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(0,Update.class,(p,b)->{b.writeUtf(p.action,16);b.writeNbt(p.settings);},b->new Update(b.readUtf(16),b.readNbt()),(p,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.MapAtmosphereClient.receive(p.action,p.settings)));c.get().setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1,Request.class,(p,b)->{b.writeUtf(p.action,16);b.writeNbt(p.settings);},b->new Request(b.readUtf(16),b.readNbt()),(m,c)->{var context=c.get();context.enqueueWork(()->{var p=context.getSender();if(p==null||!p.hasPermissions(2)||!p.isCreative()||m.settings==null)return;var d=MapAtmosphere.get(p.serverLevel());switch(m.action){case "import"->MapAtmosphereImports.receive(p,m.settings);case "save"->{if(d.saveEditor(p.serverLevel(),m.settings)){MapAtmosphere.restorePlayer(p,new CompoundTag());send(p,"save_ok",d.editor());}else send(p,"save_error",new CompoundTag());}case "preview"->send(p,"preview",MapAtmosphere.sanitize(m.settings));case "restore"->sync(p,MapAtmosphere.effective(p));case "trigger"->d.trigger(p.serverLevel(),m.settings.getString("id"));case "open"->send(p,"open",d.editor());default->{}}});context.setPacketHandled(true);},java.util.Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }
    private static void send(ServerPlayer p,String action,CompoundTag n){CHANNEL.send(PacketDistributor.PLAYER.with(()->p),new Update(action,n.copy()));}
    public static void importResult(ServerPlayer p,boolean success,CompoundTag result){send(p,success?"import_ok":"import_error",result);}
    public static void open(ServerPlayer p){send(p,"open",MapAtmosphere.get(p.serverLevel()).editor());}
    public static void sync(ServerPlayer p,CompoundTag n){send(p,"sync",n);}
    public static void sync(ServerLevel l,CompoundTag n){CHANNEL.send(PacketDistributor.DIMENSION.with(l::dimension),new Update("sync",n.copy()));}
    public static void cue(ServerPlayer p,CompoundTag n){send(p,"cue",n);}
    public static void cue(ServerLevel l,CompoundTag n){CHANNEL.send(PacketDistributor.DIMENSION.with(l::dimension),new Update("cue",n.copy()));}
    public static void request(String action,CompoundTag n){CHANNEL.sendToServer(new Request(action,n));}
}
