package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.entity.SoulOrbEntity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public final class TrackedSoulRenderer extends EntityRenderer<SoulOrbEntity> {
    public TrackedSoulRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public ResourceLocation getTextureLocation(SoulOrbEntity orb){return SoulGlow.TEXTURE;}
    @Override public void render(SoulOrbEntity orb,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
        pose.pushPose();
        var at=orb.renderPosition(partial);double x=net.minecraft.util.Mth.lerp(partial,orb.xOld,orb.getX()),y=net.minecraft.util.Mth.lerp(partial,orb.yOld,orb.getY()),z=net.minecraft.util.Mth.lerp(partial,orb.zOld,orb.getZ());
        pose.translate(at.x-x,at.y-y,at.z-z);pose.mulPose(entityRenderDispatcher.cameraOrientation());
        var v=buffers.getBuffer(SoulGlow.TYPE);
        float pulse=1+.08f*(float)Math.sin((orb.tickCount+partial)*.7);
        SoulGlow.circle(v,pose.last(),.32f*pulse,255,0,0,38);
        SoulGlow.circle(v,pose.last(),.22f*pulse,255,4,4,110);
        SoulGlow.circle(v,pose.last(),.13f,255,12,12,235);
        SoulGlow.circle(v,pose.last(),.055f,255,160,135,255);
        pose.popPose();
    }
}
