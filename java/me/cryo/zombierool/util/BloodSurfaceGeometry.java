package me.cryo.zombierool.util;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.*;

/** Clips a stain to actual coplanar collision faces, preserving its texture coordinates. */
public final class BloodSurfaceGeometry {
    public record Vertex(double x,double y,double z,float u,float v) {}
    public record Piece(BlockPos support,BlockState state,BlockState mimic,List<Vertex> vertices) {
        public boolean present(Level level){
            if(!level.getBlockState(support).equals(state))return false;
            var current=level.getBlockEntity(support) instanceof me.cryo.zombierool.block.system.MimicSystem.IMimicContainer container?container.getMimic():null;
            return java.util.Objects.equals(mimic,current);
        }
    }
    private record Point(double a,double b,float u,float v) {}
    public static List<Piece> clip(Level level,Vec3 center,Direction face,float half,float angle,float aspect){
        if(level==null||half<=0)return List.of();
        double ca=a(center,face),cb=b(center,face),plane=n(center,face),cos=Math.cos(angle),sin=Math.sin(angle);
        var polygon=new ArrayList<Point>();
        float[] us={0,0,1,1},vs={0,1,1,0};
        for(int i=0;i<4;i++){double x=(i<2?-half:half),y=((i==0||i==3)?-half:half)*aspect;polygon.add(new Point(ca+x*cos-y*sin,cb+x*sin+y*cos,us[i],vs[i]));}
        double minA=polygon.stream().mapToDouble(Point::a).min().orElse(ca),maxA=polygon.stream().mapToDouble(Point::a).max().orElse(ca);
        double minB=polygon.stream().mapToDouble(Point::b).min().orElse(cb),maxB=polygon.stream().mapToDouble(Point::b).max().orElse(cb);
        // Move inside the supporting face so an exact integer plane selects the solid cell.
        double supportN=plane-face.getAxisDirection().getStep()*.05;
        var pieces=new ArrayList<Piece>();
        for(int ia=(int)Math.floor(minA);ia<=Math.floor(maxA);ia++)for(int ib=(int)Math.floor(minB);ib<=Math.floor(maxB);ib++){
            Vec3 probe=world(ia+.5,ib+.5,supportN,face);BlockPos pos=BlockPos.containing(probe);if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);if(GoreCollision.ignored(state))continue;
            for(AABB raw:state.getCollisionShape(level,pos).toAabbs()){
                AABB box=raw.move(pos);double surface=face.getAxisDirection()==Direction.AxisDirection.POSITIVE?max(box,face.getAxis()):min(box,face.getAxis());
                if(Math.abs(surface-plane)>.065)continue;
                var clipped=boundary(polygon,0,minA(box,face),true);clipped=boundary(clipped,0,maxA(box,face),false);
                clipped=boundary(clipped,1,minB(box,face),true);clipped=boundary(clipped,1,maxB(box,face),false);
                if(clipped.size()<3||area(clipped)<1e-8)continue;
                var vertices=new ArrayList<Vertex>();for(Point p:clipped){Vec3 v=world(p.a,p.b,surface,face);vertices.add(new Vertex(v.x-center.x,v.y-center.y,v.z-center.z,p.u,p.v));}
                pieces.add(new Piece(pos.immutable(),state,level.getBlockEntity(pos) instanceof me.cryo.zombierool.block.system.MimicSystem.IMimicContainer container?container.getMimic():null,List.copyOf(vertices)));
            }
        }return List.copyOf(pieces);
    }
    private static double area(List<Point> p){double sum=0;for(int i=0;i<p.size();i++){Point a=p.get(i),b=p.get((i+1)%p.size());sum+=a.a*b.b-b.a*a.b;}return Math.abs(sum)*.5;}
    private static ArrayList<Point> boundary(List<Point> input,int axis,double bound,boolean lower){
        var out=new ArrayList<Point>();if(input.isEmpty())return out;Point previous=input.get(input.size()-1);double pv=axis==0?previous.a:previous.b;boolean pin=lower?pv>=bound:pv<=bound;
        for(Point current:input){double cv=axis==0?current.a:current.b;boolean cin=lower?cv>=bound:cv<=bound;
            if(cin!=pin){double t=(bound-pv)/(cv-pv);out.add(new Point(previous.a+(current.a-previous.a)*t,previous.b+(current.b-previous.b)*t,(float)(previous.u+(current.u-previous.u)*t),(float)(previous.v+(current.v-previous.v)*t)));}
            if(cin)out.add(current);previous=current;pv=cv;pin=cin;
        }return out;
    }
    private static double a(Vec3 v,Direction f){return f.getAxis()==Direction.Axis.X?v.z:v.x;}
    private static double b(Vec3 v,Direction f){return f.getAxis()==Direction.Axis.Y?v.z:v.y;}
    private static double n(Vec3 v,Direction f){return switch(f.getAxis()){case X->v.x;case Y->v.y;case Z->v.z;};}
    private static Vec3 world(double a,double b,double n,Direction f){return switch(f.getAxis()){case X->new Vec3(n,b,a);case Y->new Vec3(a,n,b);case Z->new Vec3(a,b,n);};}
    private static double min(AABB b,Direction.Axis a){return switch(a){case X->b.minX;case Y->b.minY;case Z->b.minZ;};}
    private static double max(AABB b,Direction.Axis a){return switch(a){case X->b.maxX;case Y->b.maxY;case Z->b.maxZ;};}
    private static Direction.Axis axisA(Direction f){return f.getAxis()==Direction.Axis.X?Direction.Axis.Z:Direction.Axis.X;}
    private static Direction.Axis axisB(Direction f){return f.getAxis()==Direction.Axis.Y?Direction.Axis.Z:Direction.Axis.Y;}
    private static double minA(AABB b,Direction f){return min(b,axisA(f));}private static double maxA(AABB b,Direction f){return max(b,axisA(f));}
    private static double minB(AABB b,Direction f){return min(b,axisB(f));}private static double maxB(AABB b,Direction f){return max(b,axisB(f));}
    private BloodSurfaceGeometry(){}
}
