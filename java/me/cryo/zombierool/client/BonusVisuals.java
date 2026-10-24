package me.cryo.zombierool.client;
import java.util.*;
import me.cryo.zombierool.network.packet.S2CBonusVisualPacket.Entry;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraftforge.client.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class BonusVisuals {
 public static final Set<String> TYPES=Set.of("max_ammo","insta_kill","nuke","double_points","carpenter","gold_rush","on_the_house","wish","zombie_blood");
 private static List<Entry> rows=List.of();private static long received;
 public static void receive(List<Entry> incoming){rows=List.copyOf(incoming);var l=Minecraft.getInstance().level;received=l==null?0:l.getGameTime();}
 @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut e){rows=List.of();}
 @SubscribeEvent public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;var mc=Minecraft.getInstance();if(mc.level==null||mc.level.getGameTime()-received>25)return;
  var cam=e.getCamera().getPosition();double time=mc.level.getGameTime()+e.getPartialTick();var buffers=mc.renderBuffers().bufferSource();
  for(var row:rows){if(!TYPES.contains(row.type())||cam.distanceToSqr(row.x(),row.y(),row.z())>4096)continue;int left=row.remaining()-(int)(mc.level.getGameTime()-received);if(left<=0||left<60&&(left/4)%2==0)continue;
   var pose=e.getPoseStack();pose.pushPose();pose.translate(row.x()-cam.x,row.y()+.8+Math.sin(time*.075)*.09-cam.y,row.z()-cam.z);
   pose.mulPose(Axis.YP.rotationDegrees((float)(time*1.7+row.uuid().hashCode()%360)));pose.scale(.9f,.9f,.9f);pose.translate(-.5,-.5,-.5);
   var model=mc.getModelManager().getModel(new ResourceLocation("zombierool","bonus/"+row.type()));
   mc.getBlockRenderer().getModelRenderer().renderModel(pose.last(),buffers.getBuffer(RenderType.entityCutoutNoCull(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)),null,model,1,1,1,LightTexture.FULL_BRIGHT,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
   pose.popPose();
   // One restrained green halo per drop; no cloud of overlapping icon particles.
   pose.pushPose();pose.translate(row.x()-cam.x,row.y()+.07-cam.y,row.z()-cam.z);var v=buffers.getBuffer(RenderType.lightning());var m=pose.last().pose();
   for(int i=0;i<24;i++){double a=i*Math.PI/12,b=(i+1)*Math.PI/12;float pulse=(float)(.6+.08*Math.sin(time*.06));float ax=(float)Math.cos(a),az=(float)Math.sin(a),bx=(float)Math.cos(b),bz=(float)Math.sin(b);
    v.vertex(m,ax*pulse,0,az*pulse).color(85,255,38,70).endVertex();v.vertex(m,bx*pulse,0,bz*pulse).color(85,255,38,70).endVertex();v.vertex(m,bx*(pulse+.13f),0,bz*(pulse+.13f)).color(30,190,12,0).endVertex();v.vertex(m,ax*(pulse+.13f),0,az*(pulse+.13f)).color(30,190,12,0).endVertex();
   }pose.popPose();
  }buffers.endBatch();
 }
 @Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
 public static final class Models {
  @SubscribeEvent public static void register(ModelEvent.RegisterAdditional e){for(String id:TYPES)e.register(new ResourceLocation("zombierool","bonus/"+id));}
 }
}
