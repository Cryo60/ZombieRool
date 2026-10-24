package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

/** Standard emissive entity pipeline: no face culling and no weather framebuffer. */
final class SoulGlow {
    static final ResourceLocation TEXTURE=new ResourceLocation("minecraft","textures/block/white_concrete.png");
    static final RenderType TYPE=RenderType.entityTranslucentEmissive(TEXTURE,false);
    static void circle(VertexConsumer v,PoseStack.Pose pose,float radius,int r,int g,int b,int alpha){
        for(int i=0;i<32;i++){
            double a=i*Math.PI/16,c=(i+1)*Math.PI/16;
            vertex(v,pose,0,0,r,g,b,alpha);
            vertex(v,pose,(float)Math.cos(a)*radius,(float)Math.sin(a)*radius,r,g,b,alpha);
            vertex(v,pose,(float)Math.cos(c)*radius,(float)Math.sin(c)*radius,r,g,b,alpha);
            vertex(v,pose,0,0,r,g,b,alpha);
        }
    }
    private static void vertex(VertexConsumer v,PoseStack.Pose pose,float x,float y,int r,int g,int b,int alpha){
        v.vertex(pose.pose(),x,y,0).color(r,g,b,alpha).uv(.5f,.5f).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(pose.normal(),0,0,1).endVertex();
    }
}
