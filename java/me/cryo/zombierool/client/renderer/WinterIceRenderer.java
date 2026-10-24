package me.cryo.zombierool.client.renderer;

import net.minecraft.world.entity.Mob;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Visual ice only: no world block is created and the map's navigation remains unchanged. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class WinterIceRenderer {
    @SubscribeEvent public static void render(net.minecraftforge.client.event.RenderLivingEvent.Post<?,?> event){
        if(!(event.getEntity() instanceof Mob mob)||mob.getTicksFrozen()<130)return;
        var pose=event.getPoseStack();pose.pushPose();
        float width=mob.getBbWidth()+.12f;pose.scale(width,mob.getBbHeight()+.1f,width);pose.translate(-.5,-.02,-.5);
        net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(net.minecraft.world.level.block.Blocks.ICE.defaultBlockState(),pose,event.getMultiBufferSource(),event.getPackedLight(),net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,net.minecraftforge.client.model.data.ModelData.EMPTY,net.minecraft.client.renderer.RenderType.translucent());
        pose.popPose();
    }
}
