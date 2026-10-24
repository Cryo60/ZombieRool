package me.cryo.zombierool.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.util.*;

/** Actual animated geometry in a camera-centered sky scene; never enters world collision or chunk storage. */
public final class SkyObjectsRenderer {
    public static ShaderInstance shader;
    private static final class Face {
        final Vector3f[] points={new Vector3f(),new Vector3f(),new Vector3f(),new Vector3f()};
        final float[] uv=new float[8];float r,g,b,a,distance;
    }
    private final List<Face> faces=new ArrayList<>();
    private final List<Face> pool=new ArrayList<>();
    private static final SkyObjectsRenderer RENDER_MESH=new SkyObjectsRenderer();
    private static final SkyObjectsRenderer COUNT_MESH=new SkyObjectsRenderer();
    Matrix4f transform=new Matrix4f();
    float opacity=1;
    private static final float RAD=(float)(Math.PI/180);
    private SkyObjectsRenderer(){}
    public static int geometryCount(CompoundTag settings,float seconds){return build(settings,seconds,COUNT_MESH).faces.size();}
    private static SkyObjectsRenderer build(CompoundTag settings,float seconds,SkyObjectsRenderer mesh){
        mesh.faces.clear();for(Tag tag:settings.getList("skyObjects",10)){var n=(CompoundTag)tag;if(!n.getBoolean("enabled")||n.getDouble("opacity")<=0||n.getString("type").equals("necropolis"))continue;
            float t=seconds*(float)n.getDouble("speed")+(float)n.getDouble("phase")/20f;
            float yaw=((float)n.getDouble("yaw")+seconds*(float)n.getDouble("orbit"))*RAD,elevation=(float)n.getDouble("elevation")*RAD,d=(float)n.getDouble("distance");
            String type=n.getString("type");
            if(type.equals("meteor")){var flight=me.cryo.zombierool.gameplay.SkyObjects.meteorFlight(t,n.getString("id"));yaw+=flight.azimuthOffset()*RAD;elevation=(float)Math.max(-85,Math.min(85,n.getDouble("elevation")+flight.elevationOffset()))*RAD;}
            var center=new Vector3f((float)Math.sin(yaw)*(float)Math.cos(elevation)*d,(float)Math.sin(elevation)*d,(float)Math.cos(yaw)*(float)Math.cos(elevation)*d);
            center.y+=(float)Math.sin(t*.23)*(float)n.getDouble("bob");
            mesh.transform=new Matrix4f().translate(center).rotateY(yaw).rotateX(type.equals("dogfight")?0:-elevation).rotateY(((float)n.getDouble("rotation")+seconds*(float)n.getDouble("spin"))*RAD).rotateX((float)n.getDouble("tilt")*RAD).rotateZ((float)n.getDouble("roll")*RAD).scale((float)n.getDouble("size"));mesh.opacity=(float)n.getDouble("opacity");
            switch(n.getString("type")){case "moon"->{int phase=(int)Math.round(n.getDouble("lunarPhase"));float light=n.contains("brightness")?f(n,"brightness"):1;mesh.sprite(new Vector3f(),new Vector3f(1,0,0),new Vector3f(0,1,0),.5f,.5f,0,new float[]{4+(phase%4)/4f,(phase/4)/2f,.25f,.5f},light*f(n,"tintR")/5,light*f(n,"tintG")/5,light*f(n,"tintB")/5,1,false,true);}case "earth","nuclear_cloud","dogfight","eclipse","meteor"->OriginalSkyEffects.render(mesh,n.getString("type"),t);case "floating_rocks"->mesh.rocks(t);case "custom","necropolis"->{for(Tag b:n.getString("type").equals("necropolis")?me.cryo.zombierool.gameplay.SkyObjects.necropolisGeometry():n.getList("boxes",10)){var box=(CompoundTag)b;mesh.box(f(box,"x"),f(box,"y"),f(box,"z"),f(box,"sx"),f(box,"sy"),f(box,"sz"),f(box,"r"),f(box,"g"),f(box,"b"),f(box,"alpha"),f(box,"emissive"));}}default->{}}
        }return mesh;
    }
    private static float f(CompoundTag n,String key){return (float)n.getDouble(key);}
    public static void render(CompoundTag settings,Matrix4f projection,Matrix4f view,int depth,int hands){
        if(shader==null||Minecraft.getInstance().level==null||settings.getList("skyObjects",10).isEmpty())return;
        float seconds=(Minecraft.getInstance().level.getGameTime()+Minecraft.getInstance().getFrameTime())/20f;var mesh=build(settings,seconds,RENDER_MESH);if(mesh.faces.isEmpty())return;
        mesh.faces.sort(Comparator.comparingDouble((Face face)->face.distance).reversed());
        var stack=RenderSystem.getModelViewStack();RenderSystem.backupProjectionMatrix();stack.pushPose();stack.setIdentity();stack.mulPoseMatrix(view);RenderSystem.applyModelViewMatrix();
        // Decouple the scenery's clipping planes from the world's chunk render distance.
        var skyProjection=new Matrix4f(projection);skyProjection.m22(-1.0001f).m32(-.100005f);RenderSystem.setProjectionMatrix(skyProjection,VertexSorting.DISTANCE_TO_ORIGIN);
        try{
            RenderSystem.disableDepthTest();RenderSystem.depthMask(false);RenderSystem.disableCull();RenderSystem.enableBlend();RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ONE,com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);RenderSystem.setShaderColor(1,1,1,1);RenderSystem.setShader(()->shader);
            shader.setSampler("MoonTexture",Minecraft.getInstance().getTextureManager().getTexture(new net.minecraft.resources.ResourceLocation("minecraft","textures/environment/moon_phases.png")).getId());
            shader.setSampler("SkyAtlas",Minecraft.getInstance().getTextureManager().getTexture(new net.minecraft.resources.ResourceLocation("zombierool","textures/environment/sky_scene_atlas.png")).getId());RenderSystem.setShaderTexture(0,Math.max(0,depth));RenderSystem.setShaderTexture(1,Math.max(0,hands));shader.safeGetUniform("Occlusion").set(depth>=0?1f:0f);var target=Minecraft.getInstance().getMainRenderTarget();shader.safeGetUniform("ScreenSize").set((float)target.width,(float)target.height);
            var out=Tesselator.getInstance().getBuilder();out.begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
            for(var face:mesh.faces)for(int i=0;i<4;i++){var point=face.points[i];out.vertex(point.x,point.y,point.z).uv(face.uv[i*2],face.uv[i*2+1]).color(face.r,face.g,face.b,face.a).endVertex();}Tesselator.getInstance().end();
        }finally{stack.popPose();RenderSystem.applyModelViewMatrix();RenderSystem.restoreProjectionMatrix();}
    }
    private void quad(Vector3f a,Vector3f b,Vector3f c,Vector3f d,float r,float g,float blue,float alpha){
        if(faces.size()>=48000)return;Vector3f[] p={a,b,c,d};float dist=0;for(var v:p){transform.transformPosition(v);dist+=v.lengthSquared();}face(p,new float[]{.0005f,.0005f,.0005f,.0005f,.0005f,.0005f,.0005f,.0005f},r,g,blue,alpha*opacity,dist*.25f);
    }
    private static Vector3f p(float x,float y,float z){return new Vector3f(x,y,z);}
    private void box(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b,float a,float emission){
        float[] lo={x-sx/2,y-sy/2,z-sz/2},hi={x+sx/2,y+sy/2,z+sz/2};
        int[][] idx={{0,2,3,1},{4,5,7,6},{0,1,5,4},{2,6,7,3},{0,4,6,2},{1,3,7,5}};float[] light={.57f,.8f,.5f,1f,.65f,.85f};
        for(int side=0;side<6;side++){Vector3f[] v=new Vector3f[4];for(int i=0;i<4;i++){int n=idx[side][i];v[i]=p((n&1)==0?lo[0]:hi[0],(n&2)==0?lo[1]:hi[1],(n&4)==0?lo[2]:hi[2]);}float shade=light[side]*(1-emission)+emission;quad(v[0],v[1],v[2],v[3],r*shade,g*shade,b*shade,a);}
    }
    private void solid(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b){box(x,y,z,sx,sy,sz,r,g,b,1,0);}
    private void rocks(float time){
        var random=new Random(9471);for(int i=0;i<22;i++){float x=(random.nextFloat()-.5f)*1.7f,z=(random.nextFloat()-.5f)*1.2f;float radius=1-Math.min(1,(x*x+z*z)*.7f),height=.2f+radius*.7f;float shade=.27f+random.nextFloat()*.15f;solid(x,-height/2,z,.28f+random.nextFloat()*.3f,height,.28f+random.nextFloat()*.3f,shade,shade*.91f,shade*.83f);solid(x,.03f,z,.3f,.07f,.3f,.25f,.29f,.23f);}
        for(int i=0;i<6;i++){float a=i*1.047f+time*.05f;solid(1.3f*(float)Math.cos(a),-.5f+(float)Math.sin(time*.3+i)*.18f,.8f*(float)Math.sin(a),.13f,.2f,.16f,.32f,.29f,.28f);}
    }
    void textured(Vector3f[] points,float[] uv,float r,float g,float b,float a,boolean additive){
        if(faces.size()>=48000||a<.0005f)return;float distance=0;var face=nextFace(r,g,b,a*opacity);
        System.arraycopy(uv,0,face.uv,0,8);
        for(int i=0;i<4;i++){transform.transformPosition(points[i],face.points[i]);distance+=face.points[i].lengthSquared();if(additive)face.uv[i*2]+=2;}
        face.distance=distance*.25f;
    }
    void sprite(Vector3f center,Vector3f right,Vector3f up,float sx,float sy,float rotation,float[] rect,float r,float g,float b,float alpha,boolean additive,boolean billboard){
        if(alpha<=.0005f||faces.size()>=48000)return;
        var c=transform.transformPosition(new Vector3f(center));
        if(billboard){var normal=new Vector3f(c).negate().normalize();right=new Vector3f(0,1,0).cross(normal);if(right.lengthSquared()<.00001f)right.set(1,0,0);right.normalize();up=new Vector3f(normal).cross(right).normalize();float scale=transform.getScale(new Vector3f()).x;sx*=scale;sy*=scale;}
        else{right=transform.transformDirection(new Vector3f(right));up=transform.transformDirection(new Vector3f(up));}
        float cos=(float)Math.cos(rotation),sin=(float)Math.sin(rotation);var horizontal=new Vector3f(right).mul(cos).fma(sin,up).mul(sx);var vertical=new Vector3f(up).mul(cos).fma(-sin,right).mul(sy);
        var face=nextFace(r,g,b,alpha*opacity);var p=face.points;
        p[0].set(c).sub(horizontal).sub(vertical);p[1].set(c).add(horizontal).sub(vertical);p[2].set(c).add(horizontal).add(vertical);p[3].set(c).sub(horizontal).add(vertical);
        float u0=rect[0]+(additive?2:0),v0=rect[1],u1=u0+rect[2],v1=v0+rect[3];var uv=face.uv;
        uv[0]=u0;uv[1]=v1;uv[2]=u1;uv[3]=v1;uv[4]=u1;uv[5]=v0;uv[6]=u0;uv[7]=v0;
        float distance=0;for(var point:p)distance+=point.lengthSquared();face.distance=distance*.25f;
    }
    private void face(Vector3f[] points,float[] uv,float r,float g,float b,float a,float distance){
        var face=nextFace(r,g,b,a);
        for(int i=0;i<4;i++)face.points[i].set(points[i]);System.arraycopy(uv,0,face.uv,0,8);
        face.distance=distance;
    }
    private Face nextFace(float r,float g,float b,float a){int index=faces.size();if(index==pool.size())pool.add(new Face());var face=pool.get(index);face.r=r;face.g=g;face.b=b;face.a=a;faces.add(face);return face;}
    public static void clear(){RENDER_MESH.faces.clear();RENDER_MESH.pool.clear();COUNT_MESH.faces.clear();COUNT_MESH.pool.clear();}
}
