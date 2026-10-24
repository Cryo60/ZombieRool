package me.cryo.zombierool.client;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.client.event.RenderHandEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Original T5 camera-space shoulder, elbow and wrist paths; player skin, two articulated segments. */
public final class LunarFirstPersonAnimation {
    private record Clip(float fps,float[][][] frames){}
    private static final Map<String,Clip> CLIPS=new HashMap<>();
    private static final Map<String,ModelPart> PARTS=new HashMap<>();
    private static Clip load(String name){
        try(var raw=LunarFirstPersonAnimation.class.getResourceAsStream("/assets/zombierool/zr_animation_data/lunar/"+name+".json");var reader=new InputStreamReader(raw,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var rows=root.getAsJsonArray("frames");float[][][] frames=new float[rows.size()][6][3];
            for(int i=0;i<frames.length;i++)for(int j=0;j<6;j++)for(int k=0;k<3;k++)frames[i][j][k]=rows.get(i).getAsJsonArray().get(j).getAsJsonArray().get(k).getAsFloat();return new Clip(root.get("fps").getAsFloat(),frames);
        }catch(Exception e){throw new IllegalStateException("Cannot load T5 helmet animation "+name,e);}
    }
    private static ModelPart arm(String key){
        boolean left=key.startsWith("left"),lower=key.endsWith("lower"),slim=key.contains("slim");int w=slim?3:4;
        var mesh=new MeshDefinition();var cube=CubeListBuilder.create().texOffs(left?32:40,(left?48:16)+(lower?6:0)).addBox(-w/2f,0,-2,w,6,4);
        mesh.getRoot().addOrReplaceChild("segment",cube,PartPose.ZERO);return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    private static Vector3f point(Clip clip,float frame,int joint){int a=Math.min(clip.frames.length-1,(int)frame),b=Math.min(a+1,clip.frames.length-1);float t=frame-a;var x=clip.frames[a][joint];var y=clip.frames[b][joint];return new Vector3f(x[0]+(y[0]-x[0])*t,x[1]+(y[1]-x[1])*t,x[2]+(y[2]-x[2])*t);}
    public static void render(RenderHandEvent e){
        e.setCanceled(true);if(e.getHand()!=InteractionHand.MAIN_HAND)return;var mc=Minecraft.getInstance();var player=mc.player;if(player==null)return;
        String name=ThirdPersonAnimHandler.currentAnim(player.getUUID());var clip=CLIPS.computeIfAbsent(name,LunarFirstPersonAnimation::load);float tick=ThirdPersonAnimHandler.animationTick(player.getUUID())+e.getPartialTick();float frame=Math.min(clip.frames.length-1,tick*clip.fps/20);
        var buffers=mc.renderBuffers().bufferSource();var pose=e.getPoseStack();pose.pushPose();
        boolean slim=player.getModelName().equals("slim");
        for(int side=0;side<2;side++)for(int segment=0;segment<2;segment++){
            var from=point(clip,frame,side*3+segment);var to=point(clip,frame,side*3+segment+1);var delta=new Vector3f(to).sub(from);float length=delta.length();if(length<.001)continue;
            pose.pushPose();pose.translate(from.x,from.y,from.z);pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),delta.normalize()));pose.scale(1,length/(6f/16f),1);
            String key=(side==0?"right":"left")+(slim?"_slim":"")+(segment==1?"_lower":"_upper");PARTS.computeIfAbsent(key,LunarFirstPersonAnimation::arm).render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation())),e.getPackedLight(),OverlayTexture.NO_OVERLAY);pose.popPose();
        }
        if(LunarEquipmentClient.showProp(player)){
            var right=point(clip,frame,2);var left=point(clip,frame,5);var center=right.add(left).mul(.5f);
            pose.pushPose();pose.translate(center.x,center.y-.15,center.z-.12);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180));if(me.cryo.zombierool.gameplay.LunarEquipment.style(player)!=me.cryo.zombierool.gameplay.LunarEquipment.SuitStyle.LUNAR)pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));pose.scale(.75f,.75f,.75f);LunarEquipmentClient.renderHelmet(pose,buffers,e.getPackedLight());pose.popPose();
        }
        pose.popPose();buffers.endBatch();
    }
    private LunarFirstPersonAnimation(){}
}
