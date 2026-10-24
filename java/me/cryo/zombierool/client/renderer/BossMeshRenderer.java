package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import me.cryo.zombierool.entity.SpecialEnemyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import java.io.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

/** Original polygon meshes and weighted COD skeletal animation; no cuboid substitution. */
public final class BossMeshRenderer extends EntityRenderer<SpecialEnemyEntity> {
    private final String kind;
    private Mesh mesh;
    private Object resourceManager;
    public BossMeshRenderer(EntityRendererProvider.Context context,String kind){super(context);this.kind=kind;shadowRadius=.65f;}
    private Mesh model(){
        var resources=Minecraft.getInstance().getResourceManager();
        if(mesh==null||resources!=resourceManager){
            var id=new ResourceLocation("zombierool","models/entities/"+kind+".zrmesh.gz");
            try(var input=new DataInputStream(new BufferedInputStream(new GZIPInputStream(resources.getResource(id).orElseThrow().open())))){mesh=Mesh.read(input);resourceManager=resources;}
            catch(IOException ex){throw new IllegalStateException("Cannot load boss mesh "+id,ex);}
        }
        return mesh;
    }
    @Override public ResourceLocation getTextureLocation(SpecialEnemyEntity entity){return model().surfaces[0].texture;}
    public int boneCount(){return model().bones;}
    public int triangleCount(){return Arrays.stream(model().surfaces).mapToInt(s->s.indices.length/3).sum();}
    public Set<String> animationNames(){return Set.copyOf(model().clips.keySet());}
    @Override public void render(SpecialEnemyEntity entity,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        var model=model();String name=entity.bossAnimation();float ticks=entity.bossAnimationAge(partial);
        boolean loop=name.equals("idle")||name.equals("walk")||name.equals("run")||name.equals("flame")||name.equals("grab");
        if(entity.deathTime>0){name="death";ticks=entity.deathTime+partial;loop=false;}
        else if(name.equals("idle")){
            float speed=entity.walkAnimation.speed(partial);
            if(speed>.015f)name=speed>.35f?"run":"walk";
            ticks=entity.tickCount+partial;
        }
        var clip=model.clips.getOrDefault(name,model.clips.get("idle"));
        float frame=ticks*clip.fps/20f;
        frame=loop?frame%clip.frames:Math.min(frame,clip.frames-1);
        int a=(int)frame,b=loop?(a+1)%clip.frames:Math.min(a+1,clip.frames-1);float alpha=frame-a;
        float[] skin=model.interpolated;
        int size=model.bones*12;
        for(int i=0;i<size;i++)skin[i]=Mth.lerp(alpha,clip.transforms[a*size+i],clip.transforms[b*size+i]);
        pose.pushPose();pose.mulPose(Axis.YP.rotationDegrees(180-Mth.rotLerp(partial,entity.yBodyRotO,entity.yBodyRot)));
        var matrix=pose.last();int overlay=OverlayTexture.pack(0,entity.hurtTime>0||entity.deathTime>0);
        for(var surface:model.surfaces){
            var consumer=buffers.getBuffer(RenderType.entityCutoutNoCull(surface.texture));
            for(int i=0;i<surface.vertices.length;i++){
                var v=surface.vertices[i];int offset=i*6;float x=0,y=0,z=0,nx=0,ny=0,nz=0;
                for(int j=0;j<v.bones.length;j++){
                    int k=v.bones[j]*12;float w=v.weights[j];
                    x+=(skin[k]*v.x+skin[k+1]*v.y+skin[k+2]*v.z+skin[k+3])*w;
                    y+=(skin[k+4]*v.x+skin[k+5]*v.y+skin[k+6]*v.z+skin[k+7])*w;
                    z+=(skin[k+8]*v.x+skin[k+9]*v.y+skin[k+10]*v.z+skin[k+11])*w;
                    nx+=(skin[k]*v.nx+skin[k+1]*v.ny+skin[k+2]*v.nz)*w;
                    ny+=(skin[k+4]*v.nx+skin[k+5]*v.ny+skin[k+6]*v.nz)*w;
                    nz+=(skin[k+8]*v.nx+skin[k+9]*v.ny+skin[k+10]*v.nz)*w;
                }
                float norm=Mth.sqrt(nx*nx+ny*ny+nz*nz);if(norm<.0001f)norm=1;
                surface.posed[offset]=x;surface.posed[offset+1]=y;surface.posed[offset+2]=z;surface.posed[offset+3]=nx/norm;surface.posed[offset+4]=ny/norm;surface.posed[offset+5]=nz/norm;
            }
            // Minecraft's entity pipeline uses quads; a repeated final corner forms a triangle.
            for(int i=0;i<surface.indices.length;i+=3)for(int corner=0;corner<4;corner++){
                int id=surface.indices[i+Math.min(corner,2)],offset=id*6;var v=surface.vertices[id];var p=surface.posed;
                consumer.vertex(matrix.pose(),p[offset],p[offset+1],p[offset+2]).color(255,255,255,255).uv(v.u,v.v).overlayCoords(overlay).uv2(surface.emissive?LightTexture.FULL_BRIGHT:light).normal(matrix.normal(),p[offset+3],p[offset+4],p[offset+5]).endVertex();
            }
        }
        pose.popPose();super.render(entity,yaw,partial,pose,buffers,light);
    }
    private record Vertex(float x,float y,float z,float nx,float ny,float nz,float u,float v,int[] bones,float[] weights){}
    private record Clip(float fps,int frames,float[] transforms){}
    private static final class Surface {
        final ResourceLocation texture;final Vertex[] vertices;final int[] indices;final float[] posed;final boolean emissive;
        Surface(ResourceLocation texture,Vertex[] vertices,int[] indices){this.texture=texture;this.vertices=vertices;this.indices=indices;posed=new float[vertices.length*6];emissive=texture.getPath().contains("unlit");}
    }
    private record Mesh(int bones,Surface[] surfaces,Map<String,Clip> clips,float[] interpolated){
        static int count(DataInputStream in,int maximum)throws IOException{int n=in.readInt();if(n<1||n>maximum)throw new IOException("Invalid mesh count "+n);return n;}
        static Mesh read(DataInputStream in)throws IOException{
            if(in.readInt()!=0x5A524D31)throw new IOException("Invalid boss mesh version");int bones=count(in,256);var surfaces=new Surface[count(in,64)];
            for(int s=0;s<surfaces.length;s++){
                var texture=new ResourceLocation(in.readUTF());int nv=count(in,200000),ni=count(in,600000);if(ni%3!=0)throw new IOException("Invalid triangles");var vertices=new Vertex[nv];var indices=new int[ni];
                for(int v=0;v<nv;v++){
                    float x=in.readFloat(),y=in.readFloat(),z=in.readFloat(),nx=in.readFloat(),ny=in.readFloat(),nz=in.readFloat(),u=in.readFloat(),vv=in.readFloat();int weights=in.readUnsignedByte();if(weights<1||weights>16)throw new IOException("Invalid weights");var bs=new int[weights];var ws=new float[weights];
                    for(int w=0;w<weights;w++){bs[w]=in.readUnsignedShort();ws[w]=in.readFloat();if(bs[w]>=bones||!Float.isFinite(ws[w]))throw new IOException("Invalid bone weight");}
                    vertices[v]=new Vertex(x,y,z,nx,ny,nz,u,vv,bs,ws);
                }
                for(int i=0;i<ni;i++){indices[i]=in.readInt();if(indices[i]<0||indices[i]>=nv)throw new IOException("Invalid mesh vertex");}
                surfaces[s]=new Surface(texture,vertices,indices);
            }
            var clips=new HashMap<String,Clip>();int nc=count(in,32);
            for(int i=0;i<nc;i++){String name=in.readUTF();float fps=in.readFloat();int frames=count(in,2000);if(fps<=0||fps>240)throw new IOException("Invalid frame rate");var transforms=new float[frames*bones*12];for(int j=0;j<transforms.length;j++){transforms[j]=in.readFloat();if(!Float.isFinite(transforms[j]))throw new IOException("Invalid animation transform");}clips.put(name,new Clip(fps,frames,transforms));}
            if(!clips.keySet().containsAll(Set.of("idle","walk","run","spawn","melee","death")))throw new IOException("Missing boss animations");return new Mesh(bones,surfaces,clips,new float[bones*12]);
        }
    }
}
