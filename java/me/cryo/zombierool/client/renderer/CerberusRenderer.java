package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import java.util.*;
import me.cryo.zombierool.block.system.CerberusSystem;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector3f;

/** Original faceted wolf: tapered muzzle, articulated jaw, pointed fur and individual fangs. */
public final class CerberusRenderer implements BlockEntityRenderer<CerberusSystem.Head> {
    private static final ResourceLocation FUR=new ResourceLocation("zombierool:textures/entities/cerberus_fur.png"),WHITE=new ResourceLocation("minecraft:textures/block/white_concrete.png"),MARK=new ResourceLocation("zombierool:textures/block/cerberus_mark.png");
    private record Face(Vector3f a,Vector3f b,Vector3f c,Vector3f d){}
    private final List<Face> head=new ArrayList<>(),jaw=new ArrayList<>(),nose=new ArrayList<>(),teeth=new ArrayList<>(),jawTeeth=new ArrayList<>(),eyes=new ArrayList<>(),cracks=new ArrayList<>(),palate=new ArrayList<>(),tongue=new ArrayList<>();
    public CerberusRenderer(BlockEntityRendererProvider.Context context){
        loft(head,new float[][]{{4,7.8f,6.4f,0},{-1,7,6,0},{-5,5.8f,4.7f,-.6f},{-10,4.5f,3.4f,-.8f}});
        loft(head,new float[][]{{-8,3.7f,1.7f,.5f},{-14,3.3f,1.5f,.6f},{-20,2.6f,1.25f,.9f}});
        loft(nose,new float[][]{{-19.7f,2.7f,1.25f,.9f},{-21,2.45f,1.05f,.8f}});
        for(int side:new int[]{-1,1}){
            cone(head,v(side*4.5f,-4.3f,-4),v(side*5.8f,-12,-1),2.6f,4);
            cone(head,v(side*4.6f,-1.4f,-9),v(side*6,-3.2f,-12),1.7f,4);
            cone(head,v(side*4.2f,-2.6f,-8.5f),v(side*2.4f,-2,-12),1.25f,4);
            float s=side;
            quad(eyes,v(s*4.6f,-1.6f,-10.5f),v(s*2.65f,-1.2f,-12),v(s*2.8f,-.55f,-12.1f),v(s*4.4f,-.75f,-10.6f));
            // Emissive eye quads must remain visible from either side of the muzzle.
            quad(eyes,v(s*4.4f,-.75f,-10.6f),v(s*2.8f,-.55f,-12.1f),v(s*2.65f,-1.2f,-12),v(s*4.6f,-1.6f,-10.5f));
            for(int i=0;i<6;i++){
                float z=-11-i*1.5f,x=side*(3.15f-i*.09f);
                cone(teeth,v(x,2.15f,z),v(x*.9f,3.6f+(i==1?1.5f:0),z-.15f),i==1?.65f:.42f,5);
                cone(jawTeeth,v(x,.2f,z+8),v(x*.9f,-1.1f-(i==3?.9f:0),z+7.8f),.38f,5);
            }
            for(int i=0;i<4;i++){
                float z=-11-i*1.8f,x=side*(3.8f-i*.24f),y=.6f+i*.16f;
                quad(cracks,v(x,y,z),v(x+side*.07f,y+.12f,z),v(x+side*.05f,y+.65f,z-.9f),v(x,y+.52f,z-.9f));
            }
        }
        var random=new Random(612);
        for(int i=0;i<16;i++){
            double a=i*Math.PI*2/16;float z=-3+random.nextFloat()*5,w=6.8f+random.nextFloat(),h=5.4f;
            Vector3f start=v((float)Math.cos(a)*w,(float)Math.sin(a)*h,z),end=v(start.x*1.25f,start.y*1.25f,z+3.2f+random.nextFloat()*2);
            cone(head,start,end,.85f+random.nextFloat()*.5f,3);
        }
        loft(jaw,new float[][]{{0,3.6f,1.15f,1.1f},{-6,3.25f,1,1.2f},{-13,2.6f,.7f,.85f}});
        quad(palate,v(-3.1f,2.1f,-10),v(3.1f,2.1f,-10),v(2.6f,2.1f,-20),v(-2.6f,2.1f,-20));
        quad(tongue,v(-2.8f,.05f,-2),v(2.8f,.05f,-2),v(2.2f,.05f,-12),v(-2.2f,.05f,-12));
        for(int i=0;i<5;i++){float x=(i-2)*.8f;cone(teeth,v(x,2.1f,-20),v(x,3.15f,-20.2f),.35f,4);cone(jawTeeth,v(x,.1f,-12.7f),v(x,-.85f,-12.9f),.3f,4);}
    }
    private static Vector3f v(float x,float y,float z){return new Vector3f(x,y,z);}
    private static void quad(List<Face> mesh,Vector3f a,Vector3f b,Vector3f c,Vector3f d){mesh.add(new Face(a,b,c,d));}
    private static void loft(List<Face> mesh,float[][] rings){
        var vertices=new Vector3f[rings.length][8];
        for(int j=0;j<rings.length;j++)for(int i=0;i<8;i++){double a=Math.PI/8+i*Math.PI/4;vertices[j][i]=v((float)Math.cos(a)*rings[j][1],rings[j][3]+(float)Math.sin(a)*rings[j][2],rings[j][0]);}
        for(int j=1;j<rings.length;j++)for(int i=0;i<8;i++){int n=(i+1)%8;quad(mesh,vertices[j-1][i],vertices[j][i],vertices[j][n],vertices[j-1][n]);}
        for(int cap:new int[]{0,rings.length-1})for(int i=0;i<8;i++){var center=v(0,rings[cap][3],rings[cap][0]);quad(mesh,center,vertices[cap][i],vertices[cap][(i+1)%8],center);}
    }
    private static void cone(List<Face> mesh,Vector3f base,Vector3f tip,float radius,int sides){
        var axis=new Vector3f(tip).sub(base).normalize();var u=new Vector3f(axis).cross(Math.abs(axis.y)>.9?v(1,0,0):v(0,1,0)).normalize();var w=new Vector3f(axis).cross(u).normalize();var ring=new Vector3f[sides];
        for(int i=0;i<sides;i++){double a=i*Math.PI*2/sides;ring[i]=new Vector3f(base).add(new Vector3f(u).mul((float)Math.cos(a)*radius)).add(new Vector3f(w).mul((float)Math.sin(a)*radius));}
        for(int i=0;i<sides;i++)quad(mesh,ring[i],ring[(i+1)%sides],tip,tip);
    }
    private static void draw(List<Face> mesh,PoseStack pose,VertexConsumer consumer,int light,float red,float green,float blue,boolean textured){
        int index=0;
        for(var face:mesh){
            var normal=new Vector3f(face.b).sub(face.a).cross(new Vector3f(face.c).sub(face.a)).normalize();float u=textured?(index%4)*.2f:0,v=textured?(index/4%4)*.2f:0,width=textured?.2f:1;
            vertex(consumer,pose,face.a,normal,u,v,light,red,green,blue);vertex(consumer,pose,face.b,normal,u,v+width,light,red,green,blue);vertex(consumer,pose,face.c,normal,u+width,v+width,light,red,green,blue);vertex(consumer,pose,face.d,normal,u+width,v,light,red,green,blue);index++;
        }
    }
    private static void vertex(VertexConsumer c,PoseStack pose,Vector3f p,Vector3f normal,float u,float v,int light,float r,float g,float b){c.vertex(pose.last().pose(),p.x/16,p.y/16,p.z/16).color(r,g,b,1).uv(u,v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(pose.last().normal(),normal.x,normal.y,normal.z).endVertex();}
    @Override public void render(CerberusSystem.Head dog,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        double time=dog.getLevel().getGameTime()+partial,elapsed=Math.max(0,time-dog.phaseStarted);
        if(dog.completed){
            pose.pushPose();pose.translate(.5,.5,.5);pose.mulPose(Axis.YP.rotationDegrees(180-dog.getBlockState().getValue(CerberusSystem.WallBlock.FACING).toYRot()));var mark=buffers.getBuffer(RenderType.eyes(MARK));var normal=v(0,0,-1);float glow=.85f+(float)Math.sin(time*.06)*.1f;
            vertex(mark,pose,v(-8,-8,7.90f),normal,1,1,15728880,1,glow,glow);vertex(mark,pose,v(-8,8,7.90f),normal,1,0,15728880,1,glow,glow);vertex(mark,pose,v(8,8,7.90f),normal,0,0,15728880,1,glow,glow);vertex(mark,pose,v(8,-8,7.90f),normal,0,1,15728880,1,glow,glow);pose.popPose();
        }
        if(!dog.revealed)return;
        float extension=dog.completed?(float)Math.min(1,Math.max(0,(CerberusSystem.DEPARTURE_TICKS-elapsed)/CerberusSystem.WITHDRAW_TICKS)):(float)Math.min(1,Math.max(0,time-dog.appearedAt)/CerberusSystem.EMERGE_TICKS);
        if(extension<=0)return;extension=extension*extension*(3-2*extension);
        pose.pushPose();pose.translate(.5,.8,.5);pose.mulPose(Axis.YP.rotationDegrees(180-dog.getBlockState().getValue(CerberusSystem.WallBlock.FACING).toYRot()));
        flames(pose,buffers,time,extension);
        pose.translate(0,0,-.15+(1-extension)*3.7);pose.scale(2.2f,-2.2f,2.2f);
        pose.mulPose(Axis.YP.rotation(CerberusSystem.headYaw(dog.phase,elapsed)));pose.mulPose(Axis.XP.rotation(CerberusSystem.headPitch(dog.phase,elapsed)));
        double bark=time-dog.idleStarted;
        if(dog.phase==0){pose.mulPose(Axis.YP.rotation(CerberusSystem.barkMotion(bark,1)));pose.mulPose(Axis.XP.rotation(CerberusSystem.barkMotion(bark,0)));pose.mulPose(Axis.ZP.rotation(CerberusSystem.barkMotion(bark,2)));}
        draw(head,pose,buffers.getBuffer(RenderType.entityCutoutNoCull(FUR)),light,.8f,.8f,.8f,true);
        var solid=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));draw(nose,pose,solid,light,.055f,.045f,.04f,false);draw(palate,pose,solid,light,.19f,.025f,.015f,false);draw(teeth,pose,solid,light,.88f,.75f,.49f,false);
        var fire=buffers.getBuffer(RenderType.eyes(WHITE));draw(eyes,pose,fire,15728880,1,.48f,.035f,false);draw(cracks,pose,fire,15728880,1,.18f,.01f,false);
        float jawAngle=CerberusSystem.jawAngle(dog.phase,elapsed);if(dog.phase==0&&bark>=0&&bark<8)jawAngle+=.42f*(float)Math.sin(bark*Math.PI/8);
        pose.pushPose();pose.translate(0,2.5/16,-8.0/16);pose.mulPose(Axis.XP.rotation(jawAngle));
        draw(jaw,pose,buffers.getBuffer(RenderType.entityCutoutNoCull(FUR)),light,.8f,.8f,.8f,true);solid=buffers.getBuffer(RenderType.entityCutoutNoCull(WHITE));draw(tongue,pose,solid,light,.25f,.02f,.012f,false);draw(jawTeeth,pose,solid,light,.88f,.75f,.49f,false);pose.popPose();pose.popPose();
    }
    /** Large animated sheets frame the head; sparks remain a secondary particle effect. */
    private static void flames(PoseStack pose,MultiBufferSource buffers,double time,float extension){
        var atlas=Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS);
        var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        for(int i=0;i<12;i++){
            double angle=i*Math.PI*2/12;float x=(float)Math.cos(angle)*1.12f,y=(float)Math.sin(angle)*.85f-.38f,z=i%3==0?-.85f:.34f;
            float height=extension*(1.05f+(float)(.5+.5*Math.sin(time*.23+i*2.1))*.65f),width=.62f;
            var sprite=atlas.getSprite(new ResourceLocation("minecraft","block/fire_"+(i%2)));
            for(int cross=0;cross<2;cross++){
                pose.pushPose();pose.translate(x,y,z);pose.mulPose(Axis.YP.rotationDegrees(cross*90+(float)Math.sin(time*.06+i)*12));
                var normal=v(0,0,-1);
                vertex(consumer,pose,v(-width*8,0,0),normal,sprite.getU0(),sprite.getV1(),15728880,1,1,1);
                vertex(consumer,pose,v(-width*8,height*16,0),normal,sprite.getU0(),sprite.getV0(),15728880,1,1,1);
                vertex(consumer,pose,v(width*8,height*16,0),normal,sprite.getU1(),sprite.getV0(),15728880,1,1,1);
                vertex(consumer,pose,v(width*8,0,0),normal,sprite.getU1(),sprite.getV1(),15728880,1,1,1);pose.popPose();
            }
        }
    }
    @Override public boolean shouldRenderOffScreen(CerberusSystem.Head dog){return true;}
    @Override public int getViewDistance(){return 64;}
}
