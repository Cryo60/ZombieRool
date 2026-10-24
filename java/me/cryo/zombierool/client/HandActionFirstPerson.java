package me.cryo.zombierool.client;

import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Camera-space forearms; third-person shoulder anchors never move to frame a viewmodel. */
// Retired: hand actions use the original PlayerAnimator animations again.
public final class HandActionFirstPerson {
    private record Clip(float fps,float duration,float[][][] frames){}
    private static final Map<String,Clip> CLIPS=new HashMap<>();
    private static final Map<String,ModelPart> PARTS=new HashMap<>();
    public static boolean supports(String name){return Set.of("grenade_cook","grenade_throw","stielhandgranate_cook","stielhandgranate_throw","knife_sweep","bowie_sweep").contains(name==null?"":name);}
    private static Clip load(String name){
        try(var raw=HandActionFirstPerson.class.getResourceAsStream("/assets/zombierool/zr_animation_data/hands/"+name+".json");var reader=new InputStreamReader(raw,StandardCharsets.UTF_8)){
            var root=JsonParser.parseReader(reader).getAsJsonObject();var rows=root.getAsJsonArray("frames");float[][][] frames=new float[rows.size()][6][3];
            for(int i=0;i<frames.length;i++)for(int j=0;j<6;j++)for(int k=0;k<3;k++)frames[i][j][k]=rows.get(i).getAsJsonArray().get(j).getAsJsonArray().get(k).getAsFloat();
            return new Clip(root.get("fps").getAsFloat(),root.get("duration").getAsFloat(),frames);
        }catch(Exception e){throw new IllegalStateException("Cannot load hand action "+name,e);}
    }
    private static ModelPart arm(String key){
        boolean left=key.startsWith("left"),lower=key.endsWith("lower"),slim=key.contains("slim");int width=slim?3:4;
        var mesh=new MeshDefinition();mesh.getRoot().addOrReplaceChild("segment",CubeListBuilder.create().texOffs(left?32:40,(left?48:16)+(lower?6:0)).addBox(-width/2f,0,-2,width,6,4),PartPose.ZERO);
        return LayerDefinition.create(mesh,64,64).bakeRoot();
    }
    private static Vector3f point(Clip clip,float frame,int joint){int a=Math.min(clip.frames.length-1,(int)frame),b=Math.min(a+1,clip.frames.length-1);float t=frame-a;var x=clip.frames[a][joint];var y=clip.frames[b][joint];return new Vector3f(x[0]+(y[0]-x[0])*t,x[1]+(y[1]-x[1])*t,x[2]+(y[2]-x[2])*t);}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGHEST,receiveCanceled=true)
    public static void render(RenderHandEvent event){
        var mc=Minecraft.getInstance();var player=mc.player;if(player==null)return;
        String name=ThirdPersonAnimHandler.currentAnim(player.getUUID());if(!supports(name)||!ThirdPersonAnimHandler.isAnimationPlaying(player.getUUID()))return;
        event.setCanceled(true);if(event.getHand()!=InteractionHand.MAIN_HAND)return;
        var clip=CLIPS.computeIfAbsent(name,HandActionFirstPerson::load);float tick=ThirdPersonAnimHandler.animationTick(player.getUUID())+event.getPartialTick();float frame=Math.min(clip.frames.length-1,tick*clip.fps/20);
        var pose=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();boolean slim=player.getModelName().equals("slim");
        var wrists=new Vector3f[2];var elbows=new Vector3f[2];
        for(int side=0;side<2;side++){
            var wrist=point(clip,frame,side*3+2);var elbow=point(clip,frame,side*3+1);wrists[side]=wrist;elbows[side]=elbow;
            // Keep the elbow junction below the viewport; only the forearm enters the view.
            elbow.y=Math.min(elbow.y,-.65f);
            if(name.endsWith("_throw")){
                float recovery=Math.max(0,Math.min(1,(tick/20-(clip.frames.length-1)/clip.fps)/.2f));
                wrist.y-=recovery*.6f;elbow.y-=recovery*.4f;
            }
            // The source rig parks unused hands behind its camera; do not bring those into view.
            if(wrist.z>-.12f)continue;
            var shoulder=new Vector3f(side==0?.48f:-.48f,-.8f,.12f);
            for(int segment=0;segment<2;segment++){
                var from=segment==0?shoulder:elbow;var to=segment==0?elbow:wrist;var delta=new Vector3f(to).sub(from);float length=delta.length();if(length<.001f)continue;
                pose.pushPose();pose.translate(from.x,from.y,from.z);pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),delta.normalize()));pose.scale(.55f,length/(6f/16f),.55f);
                String key=(side==0?"right":"left")+(slim?"_slim":"")+(segment==0?"_upper":"_lower");PARTS.computeIfAbsent(key,HandActionFirstPerson::arm).render(pose,buffers.getBuffer(RenderType.entityCutoutNoCull(player.getSkinTextureLocation())),event.getPackedLight(),OverlayTexture.NO_OVERLAY);pose.popPose();
            }
        }
        boolean throwing=name.endsWith("_throw");int side=name.equals("bowie_sweep")?1:0;
        if(wrists[side].z<-.12f&&(!throwing||tick<5)){
            boolean knife=name.endsWith("sweep");var item=name.equals("bowie_sweep")?me.cryo.zombierool.core.registry.ZRRegistry.BOWIE_KNIFE:name.equals("knife_sweep")?me.cryo.zombierool.core.registry.ZRRegistry.ANIM_KNIFE:name.startsWith("stiel")?me.cryo.zombierool.core.registry.ZRRegistry.ANIM_STIELHANDGRANATE:me.cryo.zombierool.core.registry.ZRRegistry.ANIM_GRENADE;
            var direction=knife?new Vector3f(wrists[side]).sub(elbows[side]).normalize():new Vector3f(0,1,0);float anchor=knife?.16f:name.startsWith("stiel")?.12f:.2f;var location=new Vector3f(wrists[side]).fma(anchor,direction);
            pose.pushPose();pose.translate(location.x,location.y,location.z);pose.mulPose(new Quaternionf().rotationTo(new Vector3f(0,1,0),direction));pose.scale(.5f,.5f,.5f);
            mc.getItemRenderer().renderStatic(new ItemStack(item),ItemDisplayContext.NONE,event.getPackedLight(),OverlayTexture.NO_OVERLAY,pose,buffers,mc.level,0);pose.popPose();
        }
        buffers.endBatch();
    }
    private HandActionFirstPerson(){}
}
