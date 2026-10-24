package me.cryo.zombierool.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.block.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class MachineDeliveryRenderer implements BlockEntityRenderer<MachineDeliverySystem.Delivery> {
    public MachineDeliveryRenderer(BlockEntityRendererProvider.Context context){}
    @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers e){e.registerBlockEntityRenderer(MachineDeliverySystem.ENTITY.get(),MachineDeliveryRenderer::new);}
    @Override public boolean shouldRenderOffScreen(MachineDeliverySystem.Delivery d){return d.phase.equals("animating");}
    @Override public int getViewDistance(){return 256;}
    @Override public void render(MachineDeliverySystem.Delivery d,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!d.phase.equals("animating"))return;String id=d.displayed(partial);var facing=d.getBlockState().getValue(MachineDeliverySystem.DeliveryBlock.FACING);
        pose.pushPose();pose.translate(0,1+d.baseOffset()+d.height(partial),0);var renderer=Minecraft.getInstance().getBlockRenderer();
        if(id.equals(MachineDeliverySystem.PACK)){
            var state=PackAPunchSystem.PACK_A_PUNCH.get().defaultBlockState().setValue(PackAPunchSystem.PackAPunchBlock.FACING,facing);
            renderer.getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.cutout()),state,renderer.getBlockModel(state),1,1,1,light,overlay);
        }else{
            var state=PerksSystem.BLOCK.get().defaultBlockState().setValue(PerksSystem.PerksAColaBlock.FACING,facing).setValue(PerksSystem.PerksAColaBlock.PERK_TYPE,PerksSystem.PerkType.fromString(id)).setValue(PerksSystem.PerksAColaBlock.POWERED,true);
            renderer.renderSingleBlock(state,pose,buffers,light,overlay);
        }
        pose.popPose();
        if(d.falling)renderFlames(d,partial,pose,buffers);
    }
    private static void renderFlames(MachineDeliverySystem.Delivery d,float partial,PoseStack pose,MultiBufferSource buffers){
        var mc=Minecraft.getInstance();var sprite=mc.getTextureAtlas(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS).apply(new net.minecraft.resources.ResourceLocation("minecraft","block/fire_0"));
        var out=buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.client.renderer.texture.TextureAtlas.LOCATION_BLOCKS));
        pose.pushPose();pose.translate(.5,d.baseOffset()+d.height(partial),.5);
        double time=d.getLevel().getGameTime()+partial;float total=d.selected.equals(MachineDeliverySystem.PACK)?2f:3.3f;
        for(int layer=0;layer<Math.ceil(total/.65f);layer++){
            float y=layer*.65f-.1f,h=Math.min(1.2f,total-y),r=.68f+(float)Math.sin(time*.32+layer)*.07f;
            float[][] sides={{-r,-r,r,-r},{r,-r,r,r},{r,r,-r,r},{-r,r,-r,-r}};
            for(int side=0;side<4;side++){
                float[] q=sides[side];float u0=sprite.getU0(),u1=sprite.getU1();if((side+layer)%2==0){float swap=u0;u0=u1;u1=swap;}
                flameVertex(out,pose,q[0],y,q[1],u0,sprite.getV1());flameVertex(out,pose,q[2],y,q[3],u1,sprite.getV1());
                flameVertex(out,pose,q[2]*.83f,y+h,q[3]*.83f,u1,sprite.getV0());flameVertex(out,pose,q[0]*.83f,y+h,q[1]*.83f,u0,sprite.getV0());
            }
        }
        pose.popPose();
    }
    private static void flameVertex(com.mojang.blaze3d.vertex.VertexConsumer out,PoseStack pose,float x,float y,float z,float u,float v){out.vertex(pose.last().pose(),x,y,z).color(255,255,255,255).uv(u,v).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY).uv2(15728880).normal(pose.last().normal(),0,1,0).endVertex();}

}
