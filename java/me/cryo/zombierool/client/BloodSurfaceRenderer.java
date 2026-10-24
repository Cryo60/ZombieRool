package me.cryo.zombierool.client;

import java.util.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import me.cryo.zombierool.entity.BloodDecalEntity;
import me.cryo.zombierool.util.*;

/** One merged layer per visible block face; separate deposits build up its coverage. */
public final class BloodSurfaceRenderer {
    private static final int RESOLUTION=64;
    // Bounded background CPU work; texture uploads remain on the render thread.
    private static final java.util.concurrent.ThreadPoolExecutor RASTER_WORKER=new java.util.concurrent.ThreadPoolExecutor(1,1,0,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(64),task->{var thread=new Thread(task,"ZR Blood Raster");thread.setDaemon(true);thread.setPriority(Thread.NORM_PRIORITY-1);return thread;},new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
    private record Raster(long signature,int[] pixels){}
    private static int uploadsRemaining;

    public record Stamp(Vec3 origin,Direction face,List<BloodSurfaceGeometry.Piece> pieces,int color,int alpha) {}
    private record Face(BlockPos pos,Direction direction,long plane) {}
    private record Fragment(Stamp stamp,BloodSurfaceGeometry.Piece piece) {}
    private record Cached(long signature,Stamp stamp) {}
    private static final Map<Face,Tile> TILES=new HashMap<>();
    private static final Map<BloodDecalEntity,Cached> ENTITIES=new WeakHashMap<>();
    private static List<Stamp> previousStamps=List.of();
    private static Map<Face,List<Fragment>> previousGroups=Map.of();
    private static net.minecraft.world.level.Level owner;
    private static final int GRID=16;
    private static final List<Atlas> ATLASES=new ArrayList<>();
    private static final class Atlas {
        final DynamicTexture texture;final ResourceLocation id;final BitSet used=new BitSet(GRID*GRID);
        Atlas(){try(var state=new TextureUploadState()){texture=new DynamicTexture(RESOLUTION*GRID,RESOLUTION*GRID,true);id=Minecraft.getInstance().getTextureManager().register("zr_blood_atlas",texture);}}
        void close(){Minecraft.getInstance().getTextureManager().release(id);BloodRenderType.TYPES.remove(id);}
    }
    private static final class Tile {
        final Atlas atlas;final int slot;final com.mojang.blaze3d.platform.NativeImage pixels=new com.mojang.blaze3d.platform.NativeImage(RESOLUTION,RESOLUTION,true);
        Tile(){Atlas available=null;for(var page:ATLASES)if(page.used.nextClearBit(0)<GRID*GRID){available=page;break;}if(available==null){available=new Atlas();ATLASES.add(available);}atlas=available;slot=atlas.used.nextClearBit(0);atlas.used.set(slot);}
        long signature;boolean initialized;java.util.concurrent.Future<Raster> pending;Raster ready;
        void close(){if(pending!=null)pending.cancel(false);pixels.close();atlas.used.clear(slot);}
        void update(Face face,List<Fragment> fragments,long wanted){
            if(pending!=null&&pending.isDone()){try{ready=pending.get();}catch(java.util.concurrent.CancellationException ignored){}catch(Exception error){org.slf4j.LoggerFactory.getLogger("ZombieRool").warn("Cannot rasterize blood surface",error);}pending=null;}
            if(ready!=null&&ready.signature!=wanted)ready=null;
            if(ready!=null&&uploadsRemaining>0){var data=ready.pixels;for(int y=0;y<RESOLUTION;y++)for(int x=0;x<RESOLUTION;x++)pixels.setPixelRGBA(x,y,data[y*RESOLUTION+x]);upload();uploadsRemaining--;signature=wanted;initialized=true;ready=null;}
            if(pending==null&&ready==null&&(!initialized||signature!=wanted)){try{var snapshot=List.copyOf(fragments);pending=RASTER_WORKER.submit(()->new Raster(wanted,raster(face,snapshot)));}catch(java.util.concurrent.RejectedExecutionException busy){/* Retry next frame; retain the current tile. */}}
        }
        float u(float value){return (slot%GRID+value)/GRID;}float v(float value){return (slot/GRID+value)/GRID;}
        void upload(){try(var state=new TextureUploadState()){com.mojang.blaze3d.systems.RenderSystem.bindTexture(atlas.texture.getId());pixels.upload(0,slot%GRID*RESOLUTION,slot/GRID*RESOLUTION,0,0,RESOLUTION,RESOLUTION,false,false);}}
    }
    public static void clear(){TILES.values().forEach(Tile::close);TILES.clear();RASTER_WORKER.purge();ATLASES.forEach(Atlas::close);ATLASES.clear();ENTITIES.clear();previousStamps=List.of();previousGroups=Map.of();owner=null;}
    public static Stamp stamp(BloodDecalEntity entity){
        int opacity=Math.round(RemainsFade.opacity(entity,0)*32)*255/32;long signature=Objects.hash(entity.position(),entity.getSize(),entity.getYRot(),entity.getVariant(),opacity);
        var cached=ENTITIES.get(entity);
        if(cached!=null&&cached.signature==signature&&cached.stamp.pieces.stream().allMatch(p->p.present(entity.level())))return cached.stamp;
        var pieces=BloodAppearance.geometry(entity.level(),entity.position(),Direction.UP,entity.getSize()*.5f,(float)Math.toRadians(entity.getYRot()),entity.getVariant());
        var stamp=new Stamp(entity.position(),Direction.UP,pieces,BloodAppearance.color(entity.getVariant()),opacity);
        ENTITIES.put(entity,new Cached(signature,stamp));return stamp;
    }
    public static void render(List<Stamp> stamps,PoseStack pose,MultiBufferSource.BufferSource buffers){render(stamps,pose,buffers,null);}
    public static void render(List<Stamp> stamps,PoseStack pose,MultiBufferSource.BufferSource buffers,net.minecraft.client.renderer.culling.Frustum frustum){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(owner!=mc.level){clear();owner=mc.level;ATLASES.add(new Atlas());}
        uploadsRemaining=4;
        boolean unchanged=stamps.size()==previousStamps.size();
        if(unchanged)for(int i=0;i<stamps.size();i++)if(stamps.get(i)!=previousStamps.get(i)){unchanged=false;break;}
        // Check the actual block and mimic every frame: cached stains must disappear
        // immediately when their support is removed, even while the game is paused.
        if(unchanged)for(var stamp:stamps)for(var piece:stamp.pieces)if(!piece.present(mc.level)){unchanged=false;break;}
        Map<Face,List<Fragment>> groups=unchanged?previousGroups:new LinkedHashMap<>();
        if(!unchanged)for(var stamp:stamps)for(var piece:stamp.pieces){
            if(!piece.present(mc.level)||piece.vertices().isEmpty())continue;
            var v=piece.vertices().get(0);Vec3 point=stamp.origin.add(v.x(),v.y(),v.z());
            var face=new Face(piece.support(),stamp.face,Math.round(normal(point,stamp.face)*4096));
            groups.computeIfAbsent(face,k->new ArrayList<>()).add(new Fragment(stamp,piece));
        }
        if(!unchanged){previousStamps=List.copyOf(stamps);previousGroups=groups;for(var key:List.copyOf(TILES.keySet()))if(!groups.containsKey(key)){TILES.remove(key).close();}}
        for(var entry:groups.entrySet()){
            var face=entry.getKey();if(frustum!=null&&!frustum.isVisible(new net.minecraft.world.phys.AABB(face.pos).inflate(.07)))continue;var fragments=entry.getValue();long signature=1;
            for(var fragment:fragments)signature=31*signature+System.identityHashCode(fragment.stamp)*31L+System.identityHashCode(fragment.piece);
            var tile=TILES.computeIfAbsent(face,k->new Tile());
            tile.update(face,fragments,signature);if(!tile.initialized)continue;
            var type=BloodRenderType.surface(tile.atlas.id);var vc=buffers.getBuffer(type);
            double normal=face.plane/4096.0+face.direction.getAxisDirection().getStep()*.003;
            double a=axisA(Vec3.atLowerCornerOf(face.pos),face.direction),b=axisB(Vec3.atLowerCornerOf(face.pos),face.direction);
            boolean flip=face.direction==Direction.DOWN||face.direction==Direction.SOUTH||face.direction==Direction.WEST;
            int[] order=flip?new int[]{0,3,2,1}:new int[]{0,1,2,3};
            double[] aa={a,a,a+1,a+1},bb={b,b+1,b+1,b};float[] u={0,0,1,1},v={0,1,1,0};
            int light=LevelRenderer.getLightColor(mc.level,face.pos.relative(face.direction));
            for(int i:order){var point=world(aa[i],bb[i],normal,face.direction);
                vc.vertex(pose.last().pose(),(float)point.x,(float)point.y,(float)point.z).color(255,255,255,255).uv(tile.u(u[i]),tile.v(v[i])).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),face.direction.getStepX(),face.direction.getStepY(),face.direction.getStepZ()).endVertex();}
        }
        buffers.endBatch();
    }
    private static int[] raster(Face face,List<Fragment> fragments){
        int[] image=new int[RESOLUTION*RESOLUTION];
        double a0=axisA(Vec3.atLowerCornerOf(face.pos),face.direction),b0=axisB(Vec3.atLowerCornerOf(face.pos),face.direction);
        // Rasterize each deposit separately: triangulation and clipping must not
        // create extra blood where two triangles share an edge.
        Map<Stamp,int[]> layers=new IdentityHashMap<>();
        List<Stamp> order=new ArrayList<>();
        for(var fragment:fragments){var stamp=fragment.stamp;var vertices=fragment.piece.vertices();
            int seed=Objects.hash(stamp.origin,stamp.color);
            boolean wall=stamp.face.getAxis()!=Direction.Axis.Y;
            var layer=layers.get(stamp);
            if(layer==null){layer=new int[RESOLUTION*RESOLUTION];layers.put(stamp,layer);order.add(stamp);}
            for(int i=1;i<vertices.size()-1;i++){
                var p=vertices.get(0);var q=vertices.get(i);var r=vertices.get(i+1);
                var pp=stamp.origin.add(p.x(),p.y(),p.z());var qq=stamp.origin.add(q.x(),q.y(),q.z());var rr=stamp.origin.add(r.x(),r.y(),r.z());
                double pa=axisA(pp,face.direction)-a0,pb=axisB(pp,face.direction)-b0,qa=axisA(qq,face.direction)-a0,qb=axisB(qq,face.direction)-b0,ra=axisA(rr,face.direction)-a0,rb=axisB(rr,face.direction)-b0;
                double denominator=(qb-rb)*(pa-ra)+(ra-qa)*(pb-rb);if(Math.abs(denominator)<1e-10)continue;
                int minX=Math.max(0,(int)Math.floor(Math.min(pa,Math.min(qa,ra))*RESOLUTION)),maxX=Math.min(RESOLUTION-1,(int)Math.ceil(Math.max(pa,Math.max(qa,ra))*RESOLUTION));
                int minY=Math.max(0,(int)Math.floor(Math.min(pb,Math.min(qb,rb))*RESOLUTION)),maxY=Math.min(RESOLUTION-1,(int)Math.ceil(Math.max(pb,Math.max(qb,rb))*RESOLUTION));
                for(int y=minY;y<=maxY;y++)for(int x=minX;x<=maxX;x++){
                    double a=(x+.5)/RESOLUTION,b=(y+.5)/RESOLUTION;
                    double first=((qb-rb)*(a-ra)+(ra-qa)*(b-rb))/denominator,second=((rb-pb)*(a-ra)+(pa-ra)*(b-rb))/denominator,third=1-first-second;
                    if(first<-.00001||second<-.00001||third<-.00001)continue;
                    double u=first*p.u()+second*q.u()+third*r.u(),v=first*p.v()+second*q.v()+third*r.v();
                    int alpha=BloodStampMask.sample(u,v,seed,wall)*stamp.alpha/255,previous=layer[y*RESOLUTION+x];
                    int red=(stamp.color>>16)&255,green=(stamp.color>>8)&255,blue=stamp.color&255;
                    int color=(alpha<<24)|(blue<<16)|(green<<8)|red;
                    if(alpha>(previous>>>24))layer[y*RESOLUTION+x]=color;
                }
            }
        }
        for(var stamp:order){var layer=layers.get(stamp);
            for(int y=0;y<RESOLUTION;y++)for(int x=0;x<RESOLUTION;x++){
                int deposit=layer[y*RESOLUTION+x];
                if((deposit>>>24)!=0)image[y*RESOLUTION+x]=BloodCoverage.over(image[y*RESOLUTION+x],deposit);
            }
        }
        return image;
    }
    private static double axisA(Vec3 p,Direction face){return face.getAxis()==Direction.Axis.X?p.z:p.x;}
    private static double axisB(Vec3 p,Direction face){return face.getAxis()==Direction.Axis.Y?p.z:p.y;}
    private static double normal(Vec3 p,Direction face){return switch(face.getAxis()){case X->p.x;case Y->p.y;case Z->p.z;};}
    private static Vec3 world(double a,double b,double n,Direction face){return switch(face.getAxis()){case X->new Vec3(n,b,a);case Y->new Vec3(a,n,b);case Z->new Vec3(a,b,n);};}
    private BloodSurfaceRenderer(){}
    private static final class BloodRenderType extends RenderType {
        private static final Map<ResourceLocation,RenderType> TYPES=new HashMap<>();
        private BloodRenderType(){super("zr_blood_dummy",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,()->{},()->{});}
        static RenderType surface(ResourceLocation id){return TYPES.computeIfAbsent(id,key->create("zr_blood_surface",DefaultVertexFormat.NEW_ENTITY,VertexFormat.Mode.QUADS,256,false,true,CompositeState.builder().setShaderState(RENDERTYPE_ENTITY_TRANSLUCENT_SHADER).setTextureState(new TextureStateShard(key,false,false)).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setCullState(NO_CULL).setLightmapState(LIGHTMAP).setOverlayState(OVERLAY).setWriteMaskState(COLOR_WRITE).createCompositeState(false)));}
    }
}
