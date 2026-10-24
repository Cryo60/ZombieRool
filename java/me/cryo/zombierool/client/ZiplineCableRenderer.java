package me.cryo.zombierool.client;

import me.cryo.zombierool.block.system.TraversalSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class ZiplineCableRenderer {
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var consumer=mc.renderBuffers().bufferSource().getBuffer(RenderType.lines());
        for(var anchor:TraversalSystem.loaded(mc.level)){
            if(anchor.kind()!=TraversalSystem.Kind.ZIPLINE||!anchor.ready()||anchor.start().distanceToSqr(camera)>128*128)continue;
            var a=anchor.start().add(0,1.95,0);var b=anchor.end().add(0,1.95,0);var normal=b.subtract(a).normalize();
            consumer.vertex(pose.last().pose(),(float)a.x,(float)a.y,(float)a.z).color(82,92,87,255).normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
            consumer.vertex(pose.last().pose(),(float)b.x,(float)b.y,(float)b.z).color(82,92,87,255).normal(pose.last().normal(),(float)normal.x,(float)normal.y,(float)normal.z).endVertex();
        }
        mc.renderBuffers().bufferSource().endBatch();pose.popPose();
    }
}
