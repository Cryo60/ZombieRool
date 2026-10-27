package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.*;

/** Hands follow the grip; elbows and shoulders remain attached to the camera-space body. */
public final class MeleeArms {
    private static final ModelPart[][] PARTS=new ModelPart[2][3];
    private static void init(){
        if(PARTS[0][0]!=null)return;
        for(int side=0;side<2;side++){
            int u=side==0?40:32,v=side==0?16:48;
            PARTS[side][0]=part(CubeListBuilder.create().texOffs(u,v+8).addBox(-1.7f,-1.5f,-1.8f,3.4f,3,3.6f));
            PARTS[side][1]=part(CubeListBuilder.create().texOffs(u,v+8).addBox(-1.6f,0,-1.6f,3.2f,6,3.2f));
            PARTS[side][2]=part(CubeListBuilder.create().texOffs(u,v).addBox(-1.8f,0,-1.8f,3.6f,6,3.6f));
        }
    }
    private static ModelPart part(CubeListBuilder cubes){var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("arm",cubes,PartPose.ZERO);return LayerDefinition.create(mesh,64,64).bakeRoot();}
    public static Vector3f hand(PoseStack pose,Matrix4f original,MultiBufferSource buffers,ResourceLocation skin,int light,int side){
        init();PARTS[side>0?0:1][0].render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(skin)),light,OverlayTexture.NO_OVERLAY);
        return new Matrix4f(original).invert().mul(pose.last().pose()).transformPosition(new Vector3f());
    }
    public static void connect(PoseStack pose,MultiBufferSource buffers,ResourceLocation skin,int light,Vector3f wrist,int side){
        // The lower anchor stays outside the view even when the blade crosses the screen.
        var elbow=new Vector3f(side*.64f+wrist.x*.12f,-.83f+wrist.y*.12f,-.35f);
        var shoulder=new Vector3f(side*.42f,-1.35f,.15f);
        segment(pose,buffers,skin,light,wrist,elbow,PARTS[side>0?0:1][1]);
        segment(pose,buffers,skin,light,elbow,shoulder,PARTS[side>0?0:1][2]);
    }
    private static void segment(PoseStack pose,MultiBufferSource buffers,ResourceLocation skin,int light,Vector3f from,Vector3f to,ModelPart mesh){
        var delta=new Vector3f(to).sub(from);float length=delta.length();if(length<.001f)return;
        pose.pushPose();pose.translate(from.x,from.y,from.z);
        pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),delta.div(length)));
        pose.scale(1,length/.375f,1);
        mesh.render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(skin)),light,OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }
    private MeleeArms(){}
}
