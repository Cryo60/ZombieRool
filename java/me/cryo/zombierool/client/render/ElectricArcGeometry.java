package me.cryo.zombierool.client.render;

import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Bounded, deterministic branched lightning. Rebuilt ten times per second, not every frame. */
public final class ElectricArcGeometry {
    public record Segment(Vec3 from,Vec3 to,boolean branch) {}
    private ElectricArcGeometry() {}
    public static List<Segment> create(Vec3 start,Vec3 end,long seed){
        Vec3 axis=end.subtract(start);double length=axis.length();if(length<.02)return List.of();
        Vec3 forward=axis.normalize(),side=Math.abs(forward.y)>.8?new Vec3(1,0,0):forward.cross(new Vec3(0,1,0)).normalize();
        Vec3 other=forward.cross(side).normalize();Random random=new Random(seed);List<Segment> result=new ArrayList<>();
        int steps=Math.min(32,Math.max(4,(int)Math.ceil(length*4)));
        for(int lane=0;lane<5;lane++){
            double angle=lane*Math.PI/2;Vec3 offset=lane==0?Vec3.ZERO:side.scale(Math.cos(angle)*.34).add(other.scale(Math.sin(angle)*.34));
            Vec3 previous=start.add(offset);List<Vec3> points=new ArrayList<>();points.add(previous);
            for(int i=1;i<=steps;i++){
                double t=i/(double)steps,envelope=Math.sin(Math.PI*t);
                Vec3 point=start.lerp(end,t).add(offset).add(side.scale((random.nextDouble()-.5)*.42*envelope)).add(other.scale((random.nextDouble()-.5)*.42*envelope));
                result.add(new Segment(previous,point,false));points.add(point);previous=point;
            }
            for(int b=0;b<2;b++){
                int node=1+random.nextInt(Math.max(1,steps-2));Vec3 root=points.get(node);
                Vec3 destination=start.lerp(end,Math.min(1,(node+2)/(double)steps)).add(side.scale((random.nextDouble()-.5)*1.1)).add(other.scale((random.nextDouble()-.5)*1.1));
                Vec3 bend=root.lerp(destination,.5).add(side.scale((random.nextDouble()-.5)*.15));
                result.add(new Segment(root,bend,true));result.add(new Segment(bend,destination,true));
            }
        }
        return List.copyOf(result);
    }
}
