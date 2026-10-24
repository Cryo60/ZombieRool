package me.cryo.zombierool.client;
import me.cryo.zombierool.block.system.TeleporterSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.phys.Vec3;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class TeleporterElectricityRenderer {
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_ENTITIES)return;
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var type=RenderType.entityTranslucentEmissive(new net.minecraft.resources.ResourceLocation("zombierool","textures/misc/electric_white.png"));
        var vertices=mc.renderBuffers().bufferSource().getBuffer(type);
        long time=mc.level.getGameTime()/2;
        for(var pad:TeleporterSystem.loaded(mc.level)){
            if(!pad.busy||pad.chargeUntil<=mc.level.getGameTime()||pad.role().equals("SWITCH")||pad.getBlockPos().distToCenterSqr(camera)>64*64)continue;
            var origin=Vec3.atBottomCenterOf(pad.getBlockPos());
            var random=new java.util.Random(time*7811+pad.getBlockPos().asLong());
            for(int arc=0;arc<12;arc++){
                double a=arc*Math.PI/6;
                Vec3 from=origin.add(Math.cos(a)*1.4,.14,Math.sin(a)*1.4);
                Vec3 end=origin.add(Math.cos(a+2)*.55,2.8+random.nextDouble()*.8,Math.sin(a+2)*.55);
                Vec3 previous=from;
                for(int step=1;step<=10;step++){
                    Vec3 next=step==10?end:from.lerp(end,step/10.0).add((random.nextDouble()-.5)*.65,(random.nextDouble()-.5)*.18,(random.nextDouble()-.5)*.65);
                    bolt(vertices,pose.last().pose(),camera,previous,next,1);
                    if(step==4||step==7){
                        Vec3 branch=next.add((random.nextDouble()-.5)*1.2,-.45+random.nextDouble()*.9,(random.nextDouble()-.5)*1.2);
                        Vec3 bend=next.lerp(branch,.5).add((random.nextDouble()-.5)*.3,.15,(random.nextDouble()-.5)*.3);
                        bolt(vertices,pose.last().pose(),camera,next,bend,.6f);bolt(vertices,pose.last().pose(),camera,bend,branch,.4f);
                    }
                    previous=next;
                }
            }
        }
        mc.renderBuffers().bufferSource().endBatch();pose.popPose();
    }
    private static void bolt(com.mojang.blaze3d.vertex.VertexConsumer vertices,org.joml.Matrix4f pose,Vec3 camera,Vec3 from,Vec3 to,float size){
        Vec3 side=to.subtract(from).cross(camera.subtract(from)).normalize();
        if(side.lengthSqr()<.5)side=new Vec3(1,0,0);
        for(int layer=0;layer<3;layer++){
            Vec3 offset=side.scale((layer==0?.13:layer==1?.045:.012)*size);
            float alpha=layer==0?.1f:layer==1?.35f:1f;
            for(Vec3 point:new Vec3[]{from.add(offset),from.subtract(offset),to.subtract(offset),to.add(offset)})
                vertices.vertex(pose,(float)point.x,(float)point.y,(float)point.z).color(layer==2?.93f:.3f,layer==2?.98f:.55f,1f,alpha)
                        .uv(.5f,.5f).overlayCoords(net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY)
                        .uv2(15728880).normal(0,1,0).endVertex();
        }
    }
}
