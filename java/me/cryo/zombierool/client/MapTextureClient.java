package me.cryo.zombierool.client;

import com.mojang.blaze3d.platform.NativeImage;
import me.cryo.zombierool.maptexture.MapTextures;
import me.cryo.zombierool.maptexture.MapTexturePixels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Server-supplied 32px sprites; changing worlds never reloads gunpacks. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class MapTextureClient {
    private static final byte[][][] received=new byte[MapTextures.SLOTS][][];
    private static final Map<String,Long> uploaded=new HashMap<>();
    private MapTextureClient(){}
    public static Path worldRoot(){var server=Minecraft.getInstance().getSingleplayerServer();return server==null?null:server.getWorldPath(LevelResource.ROOT);}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event){invalidate();Minecraft.getInstance().tell(MapTextureClient::rebuildIfChanged);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){java.util.Arrays.fill(received,null);uploaded.clear();for(int i=0;i<MapTextures.SLOTS;i++)MapTextures.applyClientSlot(i,"","stone");}
    public static void invalidate(){uploaded.clear();}
    public static void requestRefresh(){if(Minecraft.getInstance().getConnection()!=null)me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendToServer(new me.cryo.zombierool.network.packet.C2SRefreshMapTexturesPacket());rebuildIfChanged();}
    public static void receive(int slot,String name,String sound,byte[][] faces){
        if(slot<0||slot>=MapTextures.SLOTS||faces.length!=MapTexturePixels.FACES.length)return;
        received[slot]=faces;MapTextures.applyClientSlot(slot,name,sound);uploadSlot(slot);
    }
    public static void rebuildIfChanged(){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var repository=mc.getResourcePackRepository();var ids=new java.util.ArrayList<>(repository.getSelectedIds());
        if(ids.remove("file/zombierool_maptex")){repository.setSelected(ids);mc.options.updateResourcePacks(repository);mc.options.save();}
        for(int slot=0;slot<MapTextures.SLOTS;slot++)uploadSlot(slot);
    }
    private static void uploadSlot(int slot){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        mc.getTextureManager().bindForSetup(TextureAtlas.LOCATION_BLOCKS);
        for(int f=0;f<MapTexturePixels.FACES.length;f++){
            String key=slot+"_"+MapTexturePixels.FACES[f];byte[] data=received[slot]==null?null:received[slot][f];
            try{
                var crc=new java.util.zip.CRC32();if(data!=null)crc.update(data);long fingerprint=crc.getValue();
                if(uploaded.getOrDefault(key,-1L)==fingerprint)continue;
                var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation("zombierool","block/maptex/"+key));
                if(!sprite.contents().name().getPath().equals("block/maptex/"+key))continue;
                if(data!=null&&data.length!=0){if(data.length<24||data.length>MapTexturePixels.MAX_FACE_BYTES)throw new java.io.IOException("PNG limit");var header=java.nio.ByteBuffer.wrap(data);if(header.getInt(16)!=32||header.getInt(20)!=32)throw new java.io.IOException("32px PNG required");}
                try(NativeImage source=data==null||data.length==0?null:NativeImage.read(data)){
                    for(NativeImage target:sprite.contents().byMipLevel)for(int y=0;y<target.getHeight();y++)for(int x=0;x<target.getWidth();x++)target.setPixelRGBA(x,y,source==null?((x/4+y/4)%2==0?0xFF777777:0xFF444444):source.getPixelRGBA(x*source.getWidth()/target.getWidth(),y*source.getHeight()/target.getHeight()));
                    sprite.uploadFirstFrame();
                }
                uploaded.put(key,fingerprint);
            }catch(Exception e){me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Cannot upload map sprite {}",key,e);}
        }
    }
}
