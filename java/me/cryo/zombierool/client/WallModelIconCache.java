package me.cryo.zombierool.client;

import java.util.*;
import java.util.concurrent.*;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.*;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Flat wall drawings must not submit the complete equipment mesh every frame. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class WallModelIconCache {
    private static final int SIZE=512,MAX=32;
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"ZombieRool wall icon baker");t.setDaemon(true);return t;});
    private static final LinkedHashMap<String,Entry> CACHE=new LinkedHashMap<>(16,.75f,true);
    private static int generation,serial;
    private static final class Entry { Icon icon;boolean failed; }
    public record Icon(ResourceLocation chalk,ResourceLocation color,float minX,float minY,float maxX,float maxY){}
    private record Texture(int width,int height,int[] pixels){}
    private record Vertex(float x,float y,float z,float u,float v){}
    private record Quad(Vertex[] points,Texture texture,int tint){}
    public static Icon get(ItemStack stack,BakedModel model,ItemDisplayContext context){
        String key=net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem())+"|"+context+"|"+System.identityHashCode(model)+"|"+MapWeaponPackClient.generation;
        Entry entry=CACHE.get(key);if(entry!=null)return entry.icon;
        if(model.isCustomRenderer())return null;
        entry=new Entry();CACHE.put(key,entry);trim();final Entry owner=entry;final int epoch=generation,id=serial++;
        var pose=new PoseStack();model=net.minecraftforge.client.ForgeHooksClient.handleCameraTransforms(pose,model,context,false);pose.translate(-.5,-.5,-.5);
        var random=RandomSource.create(42);var quads=new ArrayList<BakedQuad>();
        for(Direction face:Direction.values()){random.setSeed(42);quads.addAll(model.getQuads(null,face,random));}random.setSeed(42);quads.addAll(model.getQuads(null,null,random));
        if(quads.isEmpty()){entry.failed=true;return null;}
        // Snapshot texture pixels on the render thread; background work never touches live atlas images.
        var textures=new IdentityHashMap<SpriteContents,Texture>();var geometry=new ArrayList<Quad>(quads.size());
        float minX=Float.POSITIVE_INFINITY,minY=minX,maxX=Float.NEGATIVE_INFINITY,maxY=maxX;
        for(var quad:quads){var sprite=quad.getSprite();var contents=sprite.contents();var texture=textures.get(contents);
            if(texture==null){int w=contents.width(),h=contents.height();int[] pixels=new int[w*h];var image=contents.getOriginalImage();for(int y=0;y<h;y++)for(int x=0;x<w;x++)pixels[y*w+x]=image.getPixelRGBA(x,y);texture=new Texture(w,h,pixels);textures.put(contents,texture);}
            int tint=quad.isTinted()?Minecraft.getInstance().getItemColors().getColor(stack,quad.getTintIndex()):0xFFFFFF;
            int[] data=quad.getVertices();int stride=data.length/4;var points=new Vertex[4];
            for(int i=0;i<4;i++){int at=i*stride;var p=new org.joml.Vector3f(Float.intBitsToFloat(data[at]),Float.intBitsToFloat(data[at+1]),Float.intBitsToFloat(data[at+2]));pose.last().pose().transformPosition(p);float u=(Float.intBitsToFloat(data[at+4])-sprite.getU0())/(sprite.getU1()-sprite.getU0()),v=(Float.intBitsToFloat(data[at+5])-sprite.getV0())/(sprite.getV1()-sprite.getV0());points[i]=new Vertex(p.x,p.y,p.z,u,v);minX=Math.min(minX,p.x);maxX=Math.max(maxX,p.x);minY=Math.min(minY,p.y);maxY=Math.max(maxY,p.y);}
            geometry.add(new Quad(points,texture,tint));
        }
        float span=Math.max(maxX-minX,maxY-minY);if(!Float.isFinite(span)||span<1e-5){entry.failed=true;return null;}
        float cx=(minX+maxX)*.5f,cy=(minY+maxY)*.5f,pad=span*.012f,half=span*.5f+pad;
        float left=cx-half,bottom=cy-half,right=cx+half,top=cy+half;
        WORKER.execute(()->{try{
            int[] color=new int[SIZE*SIZE];float[] depth=new float[color.length];Arrays.fill(depth,Float.POSITIVE_INFINITY);
            for(var q:geometry){triangle(q,0,1,2,left,bottom,right,top,color,depth);triangle(q,0,2,3,left,bottom,right,top,color,depth);}
            var chalk=new NativeImage(SIZE,SIZE,true);var full=new NativeImage(SIZE,SIZE,true);
            for(int y=0;y<SIZE;y++)for(int x=0;x<SIZE;x++){int value=color[y*SIZE+x];full.setPixelRGBA(x,y,value);chalk.setPixelRGBA(x,y,(value>>>24)>=128?0xFFFFFFFF:0);}
            Minecraft.getInstance().execute(()->{if(generation!=epoch||CACHE.get(key)!=owner){chalk.close();full.close();return;}var chalkId=new ResourceLocation("zombierool","dynamic/model_chalk_"+id);var colorId=new ResourceLocation("zombierool","dynamic/model_color_"+id);
                try(var state=new TextureUploadState()){var manager=Minecraft.getInstance().getTextureManager();manager.register(chalkId,new DynamicTexture(chalk));manager.register(colorId,new DynamicTexture(full));owner.icon=new Icon(chalkId,colorId,left,bottom,right,top);}
            });
        }catch(Throwable error){Minecraft.getInstance().execute(()->owner.failed=true);me.cryo.zombierool.ZombieroolMod.LOGGER.warn("Could not bake wall model icon {}",key,error);}});
        return null;
    }
    private static void triangle(Quad q,int ia,int ib,int ic,float left,float bottom,float right,float top,int[] color,float[] depth){
        var a=q.points[ia];var b=q.points[ib];var c=q.points[ic];float scale=SIZE/(right-left);
        float ax=(a.x-left)*scale,ay=(top-a.y)*scale,bx=(b.x-left)*scale,by=(top-b.y)*scale,cx=(c.x-left)*scale,cy=(top-c.y)*scale;
        float area=(by-cy)*(ax-cx)+(cx-bx)*(ay-cy);if(Math.abs(area)<1e-7)return;
        int minX=Math.max(0,(int)Math.floor(Math.min(ax,Math.min(bx,cx)))),maxX=Math.min(SIZE-1,(int)Math.ceil(Math.max(ax,Math.max(bx,cx))));
        int minY=Math.max(0,(int)Math.floor(Math.min(ay,Math.min(by,cy)))),maxY=Math.min(SIZE-1,(int)Math.ceil(Math.max(ay,Math.max(by,cy))));
        for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
            float u=((by-cy)*(x+.5f-cx)+(cx-bx)*(y+.5f-cy))/area,v=((cy-ay)*(x+.5f-cx)+(ax-cx)*(y+.5f-cy))/area,w=1-u-v;if(u<-.00001f||v<-.00001f||w<-.00001f)continue;
            float z=a.z*u+b.z*v+c.z*w;int pixel=y*SIZE+x;if(z>depth[pixel])continue;
            float tx=a.u*u+b.u*v+c.u*w,ty=a.v*u+b.v*v+c.v*w;int ix=Math.max(0,Math.min(q.texture.width-1,(int)(tx*q.texture.width))),iy=Math.max(0,Math.min(q.texture.height-1,(int)(ty*q.texture.height)));int sample=q.texture.pixels[iy*q.texture.width+ix];if((sample>>>24)<128)continue;
            int r=(sample&255)*(q.tint>>16&255)/255,g=(sample>>8&255)*(q.tint>>8&255)/255,blue=(sample>>16&255)*(q.tint&255)/255;
            color[pixel]=(sample&0xFF000000)|(blue<<16)|(g<<8)|r;depth[pixel]=z;
        }
    }
    public static void render(Icon icon,boolean purchased,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(purchased?icon.color:icon.chalk));var matrix=pose.last().pose();
        out.vertex(matrix,icon.minX,icon.minY,0).color(255,255,255,255).uv(0,1).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();
        out.vertex(matrix,icon.maxX,icon.minY,0).color(255,255,255,255).uv(1,1).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();
        out.vertex(matrix,icon.maxX,icon.maxY,0).color(255,255,255,255).uv(1,0).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();
        out.vertex(matrix,icon.minX,icon.maxY,0).color(255,255,255,255).uv(0,0).overlayCoords(overlay).uv2(light).normal(0,0,1).endVertex();
    }
    private static void trim(){while(CACHE.size()>MAX){var it=CACHE.entrySet().iterator();var entry=it.next();release(entry.getValue());it.remove();}}
    private static void release(Entry e){if(e.icon!=null){var manager=Minecraft.getInstance().getTextureManager();manager.release(e.icon.chalk);manager.release(e.icon.color);}}
    private static void clear(){generation++;CACHE.values().forEach(WallModelIconCache::release);CACHE.clear();}
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener((net.minecraft.server.packs.resources.ResourceManagerReloadListener)manager->Minecraft.getInstance().execute(WallModelIconCache::clear));}
    private WallModelIconCache(){}
}
