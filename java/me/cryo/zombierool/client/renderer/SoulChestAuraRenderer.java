package me.cryo.zombierool.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import me.cryo.zombierool.block.system.SoulChestSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;

/** Slow, dark wisps remain visible even when particle settings are minimal. */
public final class SoulChestAuraRenderer implements BlockEntityRenderer<SoulChestSystem.Chest> {
    public SoulChestAuraRenderer(BlockEntityRendererProvider.Context context){}
    @Override public void render(SoulChestSystem.Chest chest,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(!chest.auraActive())return;
        double time=chest.getLevel().getGameTime()+partial;
        var vertices=buffers.getBuffer(SoulGlow.TYPE);
        for(int i=0;i<8;i++){
            double angle=time*.026+i*Math.PI/4;
            double height=.15+((time*.009+i*.125)%1)*.9;
            pose.pushPose();
            pose.translate(.5+Math.cos(angle)*.58,height,.5+Math.sin(angle)*.58);
            pose.mulPose(Minecraft.getInstance().getEntityRenderDispatcher().cameraOrientation());
            SoulGlow.circle(vertices,pose.last(),.2f,16,3,24,65);
            SoulGlow.circle(vertices,pose.last(),.07f,110,9,135,110);
            pose.popPose();
        }
    }
}
