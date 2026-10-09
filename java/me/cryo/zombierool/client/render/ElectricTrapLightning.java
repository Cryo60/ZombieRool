package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.block.system.MapDeviceSystem;
import me.cryo.zombierool.gameplay.TrapContact;
import net.minecraft.client.renderer.*;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Native full-bright lightning geometry: cobalt halo, cyan arc and pale blue core. */
public final class ElectricTrapLightning {
    private record Cache(long frame,Direction facing,float range,List<ElectricArcGeometry.Segment> segments) {}
    private static final Map<MapDeviceSystem.Device,Cache> CACHE=new WeakHashMap<>();
    private ElectricTrapLightning() {}
    public static void render(MapDeviceSystem.Device device,PoseStack pose,MultiBufferSource buffers){
        var level=device.getLevel();if(level==null)return;
        long frame=level.getGameTime()/2;Direction facing=device.getBlockState().getValue(MapDeviceSystem.DeviceBlock.FACING);
        Cache cached=CACHE.get(device);
        if(cached==null||cached.frame!=frame||cached.facing!=facing||cached.range!=device.range){
            Vec3 origin=Vec3.atCenterOf(device.getBlockPos()),axis=Vec3.atLowerCornerOf(facing.getNormal());
            Vec3 end=TrapContact.beamEnd(level,device.getBlockPos(),facing,device.range).subtract(Vec3.atLowerCornerOf(device.getBlockPos()));
            Vec3 start=new Vec3(.5,.5,.5).subtract(axis.scale(.25));
            var arcs=ElectricArcGeometry.create(start,end,device.getBlockPos().asLong()^(frame*0x9E3779B97F4A7C15L));
            cached=new Cache(frame,facing,device.range,arcs);CACHE.put(device,cached);
        }
        VertexConsumer vertices=buffers.getBuffer(ElectricLightningRenderType.BLUE_ARCS);
        for(var segment:cached.segments){
            float width=segment.branch()?.65f:1;
            tube(pose,vertices,segment,.075f*width,.12f,.28f,1,.14f);
            tube(pose,vertices,segment,.032f*width,.18f,.65f,1,.5f);
            tube(pose,vertices,segment,.011f*width,.7f,.93f,1,.95f);
        }
    }
    private static void tube(PoseStack pose,VertexConsumer vertices,ElectricArcGeometry.Segment segment,float width,float red,float green,float blue,float alpha){
        Vec3 forward=segment.to().subtract(segment.from()).normalize();
        Vec3 side=Math.abs(forward.y)>.8?forward.cross(new Vec3(1,0,0)).normalize():forward.cross(new Vec3(0,1,0)).normalize();
        Vec3 up=forward.cross(side).normalize();Vec3[] ring={side.add(up).scale(width),side.subtract(up).scale(width),side.add(up).scale(-width),up.subtract(side).scale(width)};
        for(int i=0;i<4;i++){int j=(i+1)%4;vertex(pose,vertices,segment.from().add(ring[i]),red,green,blue,alpha);vertex(pose,vertices,segment.from().add(ring[j]),red,green,blue,alpha);vertex(pose,vertices,segment.to().add(ring[j]),red,green,blue,alpha);vertex(pose,vertices,segment.to().add(ring[i]),red,green,blue,alpha);}
    }
    private static void vertex(PoseStack pose,VertexConsumer vertices,Vec3 p,float r,float g,float b,float a){vertices.vertex(pose.last().pose(),(float)p.x,(float)p.y,(float)p.z).color(r,g,b,a).endVertex();}
}
