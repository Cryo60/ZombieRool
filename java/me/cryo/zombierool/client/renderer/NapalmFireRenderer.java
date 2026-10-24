package me.cryo.zombierool.client.renderer;

import me.cryo.zombierool.entity.NapalmFireEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public final class NapalmFireRenderer extends EntityRenderer<NapalmFireEntity>{
    public NapalmFireRenderer(EntityRendererProvider.Context context){super(context);}
    @Override public ResourceLocation getTextureLocation(NapalmFireEntity e){return net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS;}
    @Override public void render(NapalmFireEntity e,float yaw,float partial,PoseStack pose,MultiBufferSource buffer,int light){
        var consumer=buffer.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(e)));
        var sprite=net.minecraft.client.resources.model.ModelBakery.FIRE_0.sprite();
        // Concentric rings fill the same four-block disc as the damage check.
        // These animated sprite planes never place Minecraft fire blocks.
        for(int ring=0;ring<=5;ring++){
            float radius=ring*.72f;int count=ring==0?1:(int)Math.ceil(2*Math.PI*radius/.62);
            for(int i=0;i<count;i++){
                float angle=(float)(i*2*Math.PI/count+ring*.37),x=(float)Math.cos(angle)*radius,z=(float)Math.sin(angle)*radius;
                float height=.65f+.24f*(float)Math.sin(i*2.4+ring+e.tickCount*.14+partial*.14);
                pose.pushPose();pose.translate(x,.025,z);pose.mulPose(com.mojang.math.Axis.YP.rotation(angle));
                for(int cross=0;cross<2;cross++){
                    var matrix=pose.last().pose();var normal=pose.last().normal();
                    consumer.vertex(matrix,-.34f,0,0).color(255,255,255,255).uv(sprite.getU0(),sprite.getV1()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
                    consumer.vertex(matrix,.34f,0,0).color(255,255,255,255).uv(sprite.getU1(),sprite.getV1()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
                    consumer.vertex(matrix,.34f,height,0).color(255,255,255,255).uv(sprite.getU1(),sprite.getV0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
                    consumer.vertex(matrix,-.34f,height,0).color(255,255,255,255).uv(sprite.getU0(),sprite.getV0()).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(15728880).normal(normal,0,0,1).endVertex();
                    pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(90));
                }
                pose.popPose();
            }
        }
    }
}
