package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import me.cryo.zombierool.block.system.CerberusSystem;
import me.cryo.zombierool.entity.AbstractZombieRoolEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.ArrayList;

/** The retained body uses the same client clock as the jaws, even between entity packets. */
public final class CerberusCorpsePose {
    private CerberusCorpsePose() {}
    public static CerberusSystem.Head head(AbstractZombieRoolEntity body){
        return body.isCerberusFeeding()&&body.level().getBlockEntity(body.getCerberusHead()) instanceof CerberusSystem.Head head&&head.phase==2?head:null;
    }
    public static Vec3 offset(AbstractZombieRoolEntity body,float partial){
        var head=head(body);if(head==null)return Vec3.ZERO;
        double t=Math.max(0,head.getLevel().getGameTime()+partial-head.phaseStarted);
        return head.feedingPosition(body,t).subtract(new Vec3(Mth.lerp(partial,body.xOld,body.getX()),Mth.lerp(partial,body.yOld,body.getY()),Mth.lerp(partial,body.zOld,body.getZ())));
    }
    public static void rotate(AbstractZombieRoolEntity body,PoseStack pose,float partial){
        var head=head(body);float yaw=body.getYRot(),pitch=Mth.lerp(partial,body.xRotO,body.getXRot());
        if(head!=null){double t=Math.max(0,head.getLevel().getGameTime()+partial-head.phaseStarted);yaw=head.getBlockState().getValue(CerberusSystem.WallBlock.FACING).toYRot()-(float)Math.toDegrees(CerberusSystem.headYaw(2,t));pitch=90-(float)Math.toDegrees(CerberusSystem.headPitch(2,t));}
        pose.mulPose(Axis.YP.rotationDegrees(body instanceof me.cryo.zombierool.entity.CrawlerEntity?-yaw:180-yaw));
        if(body instanceof me.cryo.zombierool.entity.CrawlerEntity){pose.mulPose(Axis.XP.rotationDegrees(pitch-90));pose.translate(0,-.6,0);}
        else pose.mulPose(Axis.XP.rotationDegrees(pitch));
    }
    public static MultiBufferSource eatingBuffer(AbstractZombieRoolEntity body,PoseStack pose,float partial,MultiBufferSource buffers){
        var head=head(body);if(head==null)return buffers;
        double t=Math.max(0,head.getLevel().getGameTime()+partial-head.phaseStarted);
        Vec3 relative=head.mouth(t).subtract(head.feedingPosition(body,t));
        var point=pose.last().pose().transform(new Vector4f((float)relative.x,(float)relative.y,(float)relative.z,1));
        double yaw=CerberusSystem.headYaw(2,t),pitch=CerberusSystem.headPitch(2,t);
        var direction=Vec3.atLowerCornerOf(head.getBlockState().getValue(CerberusSystem.WallBlock.FACING).getNormal());
        Vec3 side=new Vec3(-direction.z,0,direction.x);
        Vec3 axis=direction.scale(Math.cos(yaw)*Math.cos(pitch)).add(side.scale(-Math.sin(yaw)*Math.cos(pitch))).add(0,-Math.sin(pitch),0);
        var normal=pose.last().normal().transform(new Vector3f((float)axis.x,(float)axis.y,(float)axis.z)).normalize();
        float distance=normal.x*point.x+normal.y*point.y+normal.z*point.z;
        return type->new BiteClip(buffers.getBuffer(type),normal,distance);
    }
    /** Clip swallowed surfaces at the teeth, rather than shrinking or popping the whole corpse. */
    private static final class BiteClip implements VertexConsumer {
        private final VertexConsumer target;
        private final Vector3f plane;
        private final float distance;
        private final ArrayList<float[]> quad=new ArrayList<>(4);
        private float[] vertex=new float[14];
        private int[] defaultColor;
        BiteClip(VertexConsumer target,Vector3f plane,float distance){this.target=target;this.plane=plane;this.distance=distance;}
        public VertexConsumer vertex(double x,double y,double z){vertex[0]=(float)x;vertex[1]=(float)y;vertex[2]=(float)z;return this;}
        public VertexConsumer color(int r,int g,int b,int a){vertex[3]=r;vertex[4]=g;vertex[5]=b;vertex[6]=a;return this;}
        public VertexConsumer uv(float u,float v){vertex[7]=u;vertex[8]=v;return this;}
        public VertexConsumer overlayCoords(int u,int v){vertex[9]=u;vertex[10]=v;return this;}
        public VertexConsumer uv2(int u,int v){vertex[11]=u;vertex[12]=v;return this;}
        private float nx,ny,nz;
        public VertexConsumer normal(float x,float y,float z){nx=x;ny=y;nz=z;return this;}
        public void defaultColor(int r,int g,int b,int a){defaultColor=new int[]{r,g,b,a};}
        public void unsetDefaultColor(){defaultColor=null;}
        private float side(float[] v){return v[0]*plane.x+v[1]*plane.y+v[2]*plane.z-distance;}
        public void endVertex(){
            if(defaultColor!=null)for(int i=0;i<4;i++)vertex[3+i]=defaultColor[i];
            quad.add(vertex);vertex=new float[14];if(quad.size()<4)return;
            var clipped=new ArrayList<float[]>(5);
            var previous=quad.get(3);float previousSide=side(previous);
            for(var current:quad){
                float currentSide=side(current);
                if((previousSide>=0)!=(currentSide>=0)){
                    float f=previousSide/(previousSide-currentSide);var crossing=new float[14];
                    for(int i=0;i<14;i++)crossing[i]=previous[i]+f*(current[i]-previous[i]);clipped.add(crossing);
                }
                if(currentSide>=0)clipped.add(current);
                previous=current;previousSide=currentSide;
            }
            if(clipped.size()==4)for(var v:clipped)emit(v);
            else for(int i=1;i+1<clipped.size();i++){emit(clipped.get(0));emit(clipped.get(i));emit(clipped.get(i+1));emit(clipped.get(i+1));}
            quad.clear();
        }
        private void emit(float[] v){target.vertex(v[0],v[1],v[2]).color(Math.round(v[3]),Math.round(v[4]),Math.round(v[5]),Math.round(v[6])).uv(v[7],v[8]).overlayCoords(Math.round(v[9]),Math.round(v[10])).uv2(Math.round(v[11]),Math.round(v[12])).normal(nx,ny,nz).endVertex();}
    }
}
