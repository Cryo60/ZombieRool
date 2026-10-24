package me.cryo.zombierool.gameplay;

import java.nio.file.*;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Bounded, ordered editor uploads. Files belong to the world, including on dedicated servers. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class MapAtmosphereImports {
    public static final int CHUNK=24*1024;
    private record Upload(UUID token,String dimension,String kind,String name,int count,long started,java.io.ByteArrayOutputStream bytes,int[] next){}
    private static final Map<UUID,Upload> uploads=new HashMap<>();
    private MapAtmosphereImports(){}
    public static void receive(ServerPlayer player,CompoundTag n){
        if(!player.hasPermissions(2)||!player.isCreative())return;
        long now=System.nanoTime();uploads.values().removeIf(u->now-u.started>60_000_000_000L);
        try{
            UUID token=n.getUUID("token");String kind=n.getString("kind"),name=n.getString("name");int index=n.getInt("index"),count=n.getInt("count");
            int limit=kind.equals("sky")?8*1024*1024:kind.equals("sound")?15*1024*1024:0;
            byte[] bytes=n.getByteArray("data");
            if(limit==0||count<1||count>(limit+CHUNK-1)/CHUNK||index<0||index>=count||bytes.length<1||bytes.length>CHUNK||name.length()>128)throw new IllegalArgumentException();
            String dimension=player.serverLevel().dimension().location().toString();
            if(index==0){if(!uploads.containsKey(player.getUUID())&&uploads.size()>=4)throw new IllegalArgumentException();uploads.put(player.getUUID(),new Upload(token,dimension,kind,name,count,now,new java.io.ByteArrayOutputStream(),new int[1]));}
            var upload=uploads.get(player.getUUID());
            if(upload==null||!upload.token.equals(token)||!upload.dimension.equals(dimension)||!upload.kind.equals(kind)||!upload.name.equals(name)||upload.count!=count||upload.next[0]!=index||upload.bytes.size()+bytes.length>limit)throw new IllegalArgumentException();
            upload.bytes.writeBytes(bytes);upload.next[0]++;
            if(index+1<count)return;
            uploads.remove(player.getUUID());String id=install(player,kind,name,upload.bytes.toByteArray());
            var result=new CompoundTag();result.putString("id",id);result.putString("kind",kind);if(kind.equals("sky")){var header=java.nio.ByteBuffer.wrap(upload.bytes.toByteArray());result.putString("skyLayout",header.getInt(16)==header.getInt(20)*2?"panorama":"cube");result.putString("skyCoordinates",isCross(header.getInt(16),header.getInt(20))?"y_up":"z_up");}MapAtmospherePackets.importResult(player,true,result);
        }catch(Exception error){uploads.remove(player.getUUID());MapAtmospherePackets.importResult(player,false,new CompoundTag());}
    }
    public static String install(ServerPlayer player,String kind,String name,byte[] data)throws java.io.IOException{
        if(!player.hasPermissions(2)||!player.isCreative())throw new java.io.IOException("Editor permission required");
        String base=name.replaceFirst("(?i)\\.(png|ogg)$","").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+","_");if(base.isBlank())base="asset";base=base.substring(0,Math.min(48,base.length()))+"_"+UUID.randomUUID().toString().replace("-","");
        Path root=player.server.getWorldPath(LevelResource.ROOT).resolve("zombierool");
        if(kind.equals("sky")){
            data=normalizeSky(data);
            Path folder=root.resolve("assets/skyboxes/imports");Files.createDirectories(folder);if(MapAtmosphereAssets.skyIds().size()>=16)throw new java.io.IOException("Skybox library full");
            Files.write(folder.resolve(base+".png"),data,StandardOpenOption.CREATE_NEW);MapAtmosphereAssets.load(root.toFile());String id="zombierool:world_sky/imports/"+base;
            if(!MapAtmosphereAssets.skyIds().contains(id)){Files.deleteIfExists(folder.resolve(base+".png"));throw new java.io.IOException("Skybox memory limit");}
            for(var p:player.server.getPlayerList().getPlayers())MapAtmospherePackets.texture(p,id,data);
            return id;
        }
        if(!kind.equals("sound")||data.length<64||data.length>15*1024*1024||data[0]!='O'||data[1]!='g'||data[2]!='g'||data[3]!='S'||!new String(data,0,Math.min(256,data.length),java.nio.charset.StandardCharsets.ISO_8859_1).contains("\u0001vorbis"))throw new java.io.IOException("Ogg Vorbis required");
        Path folder=root.resolve("audio/custom/imports");Files.createDirectories(folder);Files.write(folder.resolve(base+".ogg"),data,StandardOpenOption.CREATE_NEW);
        String id="zombierool:custom/imports/"+base;me.cryo.zombierool.core.manager.DynamicResourceManager.importAmbientAudio(player.server,id,data);return id;
    }
    private static boolean isCross(int w,int h){return w>0&&h>0&&w%4==0&&h%3==0&&w/4==h/3;}
    /** Convert the horizontal cube net to the renderer's +X,-X,+Y,-Y,+Z,-Z atlas. */
    public static byte[] normalizeSky(byte[] data)throws java.io.IOException{
        if(data.length<24||data.length>8*1024*1024||java.nio.ByteBuffer.wrap(data).getLong()!=0x89504e470d0a1a0aL)throw new java.io.IOException("PNG required");
        var header=java.nio.ByteBuffer.wrap(data);int w=header.getInt(16),h=header.getInt(20);boolean cross=isCross(w,h);
        if(w<1||h<1||w>4096||h>4096||!cross&&(long)w*h>8_388_608||!(cross||w==h*2||w%3==0&&h%2==0&&w/3==h/2))throw new java.io.IOException("Use a 2:1 panorama, 3:2 atlas or 4:3 horizontal cross, up to 4096 pixels");
        var image=javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(data));if(image==null)throw new java.io.IOException("Invalid PNG");
        if(!cross)return data;
        int size=w/4;var atlas=new java.awt.image.BufferedImage(size*3,size*2,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        int[][] cells={{2,1},{0,1},{1,0},{1,2},{1,1},{3,1}};
        for(int face=0;face<6;face++){int[] pixels=image.getRGB(cells[face][0]*size,cells[face][1]*size,size,size,null,0,size);atlas.setRGB(face%3*size,face/3*size,size,size,pixels,0,size);}
        var out=new java.io.ByteArrayOutputStream();if(!javax.imageio.ImageIO.write(atlas,"png",out)||out.size()>8*1024*1024)throw new java.io.IOException("Converted atlas too large");return out.toByteArray();
    }
    @SubscribeEvent public static void logout(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent e){uploads.remove(e.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(net.minecraftforge.event.server.ServerStoppedEvent e){uploads.clear();}
}
