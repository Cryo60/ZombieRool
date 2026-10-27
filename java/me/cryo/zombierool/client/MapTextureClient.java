package me.cryo.zombierool.client;

import com.mojang.blaze3d.platform.NativeImage;
import me.cryo.zombierool.maptexture.MapTextures;
import me.cryo.zombierool.maptexture.MapTexturePixels;
import me.cryo.zombierool.maptexture.MapTextureAnimation;
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
    private static final Map<String,AnimationSprite> animations=new HashMap<>();
    private static long animationTick;
    private static final Map<Integer,net.minecraft.client.renderer.texture.DynamicTexture> overlays=new HashMap<>();
    private static final Map<Integer,ResourceLocation> overlayIds=new HashMap<>();
    public static ResourceLocation waterOverlay(int slot){return overlayIds.get(slot);}
    private static int opacity(int color,float opacity){return (color&0xffffff)|Math.round((color>>>24)*opacity)<<24;}
    private static void appearance(NativeImage image,int slot){
        long r=0,g=0,b=0,n=0;float alpha=me.cryo.zombierool.maptexture.MapTextureAppearance.opacity(slot);
        for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++){int c=image.getPixelRGBA(x,y);if((c>>>24)>0&&y<32){r+=c&255;g+=(c>>>8)&255;b+=(c>>>16)&255;n++;}image.setPixelRGBA(x,y,opacity(c,alpha));}
        if(n>0)me.cryo.zombierool.maptexture.MapTextureAppearance.color(slot,(int)(r/n)<<16|(int)(g/n)<<8|(int)(b/n));
    }
    private static void overlay(int slot,NativeImage image){
        var oldId=overlayIds.remove(slot);if(oldId!=null)Minecraft.getInstance().getTextureManager().release(oldId);overlays.remove(slot);var frame=new NativeImage(32,32,false);for(int y=0;y<32;y++)for(int x=0;x<32;x++)frame.setPixelRGBA(x,y,image.getPixelRGBA(x,y));
        var texture=new net.minecraft.client.renderer.texture.DynamicTexture(frame);overlays.put(slot,texture);var id=new ResourceLocation("zombierool","map_water_overlay/"+slot);Minecraft.getInstance().getTextureManager().register(id,texture);overlayIds.put(slot,id);Minecraft.getInstance().getTextureManager().bindForSetup(TextureAtlas.LOCATION_BLOCKS);
    }
    private static final class AnimationSprite implements AutoCloseable {
        final MapTextureAnimation animation;
        final NativeImage sheet;
        int lastFrame=-1,lastNext=-1;
        float lastBlend=-1;
        AnimationSprite(MapTextureAnimation animation)throws java.io.IOException {
            this.animation=animation;sheet=NativeImage.read(animation.png());
            if(sheet.getWidth()!=32 || sheet.getHeight()%32!=0 || sheet.getHeight()>32*MapTextureAnimation.MAX_FRAMES){sheet.close();throw new java.io.IOException("Invalid texture sheet");}
        }
        public void close(){sheet.close();}
    }
    private MapTextureClient(){}
    public static Path worldRoot(){var server=Minecraft.getInstance().getSingleplayerServer();return server==null?null:server.getWorldPath(LevelResource.ROOT);}
    @SubscribeEvent public static void login(ClientPlayerNetworkEvent.LoggingIn event){invalidate();Minecraft.getInstance().tell(MapTextureClient::rebuildIfChanged);}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){me.cryo.zombierool.maptexture.CustomWaterlogging.CLIENT.clear();java.util.Arrays.fill(received,null);invalidate();for(var id:overlayIds.values())Minecraft.getInstance().getTextureManager().release(id);overlayIds.clear();overlays.clear();animationTick=0;for(int i=0;i<MapTextures.SLOTS;i++)MapTextures.applyClientSlot(i,"","stone");}
    public static void invalidate(){uploaded.clear();animations.values().forEach(AnimationSprite::close);animations.clear();}
    @SubscribeEvent public static void tick(net.minecraftforge.event.TickEvent.ClientTickEvent event){
        var mc=Minecraft.getInstance();
        if(event.phase!=net.minecraftforge.event.TickEvent.Phase.END || mc.level==null || mc.isPaused())return;
        animationTick++;
        if(animations.isEmpty())return;
        mc.getTextureManager().bindForSetup(TextureAtlas.LOCATION_BLOCKS);
        for(var entry:animations.entrySet())paint(entry.getKey(),entry.getValue());
    }
    public static void requestRefresh(){if(Minecraft.getInstance().getConnection()!=null)me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendToServer(new me.cryo.zombierool.network.packet.C2SRefreshMapTexturesPacket());rebuildIfChanged();}
    public static void receive(int slot,String name,String sound,byte[][] faces){
        receive(slot,name,sound,faces,1);
    }
    public static void receive(int slot,String name,String sound,byte[][] faces,float opacity){
        if(slot<0||slot>=MapTextures.SLOTS||faces.length!=MapTexturePixels.FACES.length)return;
        me.cryo.zombierool.maptexture.MapTextureAppearance.client(slot,opacity);
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
                var crc=new java.util.zip.CRC32();if(data!=null)crc.update(data);long fingerprint=crc.getValue()^Float.floatToIntBits(me.cryo.zombierool.maptexture.MapTextureAppearance.opacity(slot));
                if(uploaded.getOrDefault(key,-1L)==fingerprint)continue;
                var sprite=mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation("zombierool","block/maptex/"+key));
                if(!sprite.contents().name().getPath().equals("block/maptex/"+key))continue;
                AnimationSprite previous=animations.remove(key);if(previous!=null)previous.close();
                MapTextureAnimation animation=data==null||data.length==0?null:MapTextureAnimation.decode(data);
                if(animation!=null){
                    if(animation.png().length<24)throw new java.io.IOException("Invalid PNG");
                    var header=java.nio.ByteBuffer.wrap(animation.png());
                    if(header.getInt(16)!=32 || header.getInt(20)<32 || header.getInt(20)>32*MapTextureAnimation.MAX_FRAMES || header.getInt(20)%32!=0)throw new java.io.IOException("32px texture sheet required");
                }
                if(animation!=null && animation.frames().length>1){
                    var animated=new AnimationSprite(animation);appearance(animated.sheet,slot);if(f==0)overlay(slot,animated.sheet);animations.put(key,animated);paint(key,animated);
                    uploaded.put(key,fingerprint);continue;
                }
                try(NativeImage source=animation==null?null:NativeImage.read(animation.png())){
                    if(source!=null){appearance(source,slot);if(f==0)overlay(slot,source);}
                    int frame=animation==null?0:animation.frames()[0];
                    for(NativeImage target:sprite.contents().byMipLevel)for(int y=0;y<target.getHeight();y++)for(int x=0;x<target.getWidth();x++)target.setPixelRGBA(x,y,source==null?((x/4+y/4)%2==0?0xFF777777:0xFF444444):source.getPixelRGBA(x*32/target.getWidth(),frame*32+y*32/target.getHeight()));
                    sprite.uploadFirstFrame();
                }
                uploaded.put(key,fingerprint);
            }catch(Exception e){me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Cannot upload map sprite {}",key,e);}
        }
    }
    private static void paint(String key,AnimationSprite value){
        var animation=value.animation;int step=animation.step(animationTick);
        int frame=animation.frames()[step],next=animation.frames()[(step+1)%animation.frames().length];
        float blend=animation.blend(animationTick,step);
        if(frame==value.lastFrame && (blend==0 && value.lastBlend==0 || next==value.lastNext && blend==value.lastBlend))return;
        var sprite=Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(new ResourceLocation("zombierool","block/maptex/"+key));
        for(NativeImage target:sprite.contents().byMipLevel)for(int y=0;y<target.getHeight();y++)for(int x=0;x<target.getWidth();x++){
            int sx=x*32/target.getWidth(),sy=y*32/target.getHeight();
            int color=value.sheet.getPixelRGBA(sx,frame*32+sy);
            if(blend>0){int other=value.sheet.getPixelRGBA(sx,next*32+sy),mixed=0;for(int shift=0;shift<32;shift+=8)mixed|=(int)(((color>>>shift)&255)*(1-blend)+((other>>>shift)&255)*blend)<<shift;color=mixed;}
            target.setPixelRGBA(x,y,color);
        }
        sprite.uploadFirstFrame();value.lastFrame=frame;value.lastNext=next;value.lastBlend=blend;
    }
}
