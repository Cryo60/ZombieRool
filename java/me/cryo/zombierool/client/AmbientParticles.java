package me.cryo.zombierool.client;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.*;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Ambient sheets use a sky-safe pass and retain opaque-world depth occlusion. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class AmbientParticles {
    private static final Set<Particle> LIVE=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Particle,Entry> ALL=new IdentityHashMap<>();
    private static final ArrayList<Entry> ORDER=new ArrayList<>();
    private static final Map<Long,Height> HEIGHTS=new HashMap<>();
    private record Height(int top,int visible,long expires){}
    private static final class Entry {final Particle particle;double distance;net.minecraft.world.phys.AABB bounds;Entry(Particle p){particle=p;}}
    private static Level owner;private static boolean rendering,deferred,dirty=true;private static int budget=384;
    private static long sortedTick=Long.MIN_VALUE;private static double sortedX,sortedY,sortedZ;
    private static void owner(Level level){if(owner==level)return;LIVE.clear();ALL.clear();ORDER.clear();HEIGHTS.clear();owner=level;dirty=true;sortedTick=Long.MIN_VALUE;}
    public static void emissionBudget(int density){int base=density<=2?128:density<=6?384:density<=15?640:896;int setting=Minecraft.getInstance().options.particles().get().ordinal();budget=Math.max(32,base>>setting);}
    public static void prepareFrame(boolean defer){deferred=defer;}
    public static int activeCount(){return ALL.size();}
    public static boolean hasDeferred(){return !LIVE.isEmpty();}
    public static boolean canSpawn(){return ALL.size()<budget;}
    public static void spawn(ParticleOptions type,double x,double y,double z,double vx,double vy,double vz){
        var mc=Minecraft.getInstance();owner(mc.level);if(mc.level==null||ALL.size()>=budget)return;
        var p=mc.particleEngine.createParticle(type,x,y,z,vx,vy,vz);
        if(p!=null){ALL.put(p,new Entry(p));dirty=true;}
        if(p instanceof TextureSheetParticle&&!(p instanceof me.cryo.zombierool.client.particle.BloodStainParticle)){var sheet=p.getRenderType();if(sheet==ParticleRenderType.PARTICLE_SHEET_OPAQUE||sheet==ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT||sheet==ParticleRenderType.PARTICLE_SHEET_LIT)LIVE.add(p);}
    }
    public static boolean defer(Particle particle){return deferred&&!rendering&&LIVE.contains(particle);}
    public static void renderDeferred(VertexConsumer out,Camera camera,float partial){
        if(LIVE.isEmpty())return;var at=camera.getPosition();long tick=owner==null?0:owner.getGameTime();
        double dx=at.x-sortedX,dy=at.y-sortedY,dz=at.z-sortedZ;
        if(dirty||tick!=sortedTick||dx*dx+dy*dy+dz*dz>.25){
            ORDER.clear();for(var p:LIVE){var entry=ALL.get(p);if(entry==null||!p.isAlive())continue;var box=p.getBoundingBox();double x=(box.minX+box.maxX)*.5-at.x,y=(box.minY+box.maxY)*.5-at.y,z=(box.minZ+box.maxZ)*.5-at.z;entry.distance=x*x+y*y+z*z;if(entry.distance>72*72)continue;entry.bounds=box.inflate(2);ORDER.add(entry);}
            ORDER.sort(Comparator.comparingDouble((Entry e)->e.distance).reversed());sortedTick=tick;sortedX=at.x;sortedY=at.y;sortedZ=at.z;dirty=false;
        }
        var frustum=new net.minecraft.client.renderer.culling.Frustum(com.mojang.blaze3d.systems.RenderSystem.getModelViewMatrix(),com.mojang.blaze3d.systems.RenderSystem.getProjectionMatrix());frustum.prepare(at.x,at.y,at.z);
        rendering=true;try{for(var entry:ORDER)if(entry.particle.isAlive()&&frustum.isVisible(entry.bounds))entry.particle.render(out,camera,partial);}finally{rendering=false;}
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();owner(mc.level);if(owner==null)return;var camera=mc.gameRenderer.getMainCamera().getPosition();ALL.entrySet().removeIf(row->{var p=row.getKey();var b=p.getBoundingBox();double x=(b.minX+b.maxX)*.5-camera.x,y=(b.minY+b.maxY)*.5-camera.y,z=(b.minZ+b.maxZ)*.5-camera.z;if(!p.isAlive()||x*x+y*y+z*z>80*80){p.remove();LIVE.remove(p);dirty=true;return true;}return false;});}
    /** Technical navigation volumes must not be mistaken for a roof. */
    public static int visibleHeight(Level level,int x,int z){
        owner(level);int top=level.getHeight(Heightmap.Types.MOTION_BLOCKING,x,z);long key=net.minecraft.world.level.ChunkPos.asLong(x,z),time=level.getGameTime();var cached=HEIGHTS.get(key);if(cached!=null&&cached.top==top&&time<cached.expires)return cached.visible;
        var pos=new BlockPos.MutableBlockPos(x,top-1,z);int visible=level.getMinBuildHeight();
        for(int y=top-1;y>=level.getMinBuildHeight();y--){pos.setY(y);var state=level.getBlockState(pos);if(me.cryo.zombierool.util.GoreCollision.ignored(state))continue;if(!state.getFluidState().isEmpty()||!state.getCollisionShape(level,pos).isEmpty()){visible=y+1;break;}}
        if(HEIGHTS.size()>=4096)HEIGHTS.clear();HEIGHTS.put(key,new Height(top,visible,time+20));return visible;
    }
    private AmbientParticles(){}
}
