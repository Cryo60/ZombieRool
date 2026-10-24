package me.cryo.zombierool.client;

import com.mojang.blaze3d.vertex.*;
import me.cryo.zombierool.client.render.ElectricLightningRenderType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Full-bright energy pulses and expanding impact rings, independent of the particle quality setting. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class RayGunVisuals {
    private record Pulse(String kind,Vec3 from,Vec3 to,boolean pap,double born){}
    private static final List<Pulse> PULSES=new ArrayList<>();
    private static Object level;
    private RayGunVisuals(){}
    private static double time(){var mc=Minecraft.getInstance();return mc.level.getGameTime()+mc.getFrameTime();}
    public static void add(String kind,Vec3 from,Vec3 to,boolean pap){
        var mc=Minecraft.getInstance();if(mc.level==null)return;
        if(level!=mc.level){PULSES.clear();level=mc.level;}
        while(PULSES.size()>=128)PULSES.remove(0);
        PULSES.add(new Pulse(kind,from,to,pap,time()));
        if(kind.endsWith("IMPACT")&&mc.player!=null){double distance=mc.player.getEyePosition().distanceTo(to);if(distance<5)ScreenShakeHandler.startShake(3,(float)(.12*(1-distance/5)),ScreenShakeHandler.ShakeType.IMPACT);}
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event){
        if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        var mc=Minecraft.getInstance();if(mc.level==null||level!=mc.level){PULSES.clear();return;}
        double time=time();PULSES.removeIf(p->time-p.born>Math.max(5,p.from.distanceTo(p.to)/10+2)||time<p.born);
        if(PULSES.isEmpty())return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();var out=buffers.getBuffer(ElectricLightningRenderType.BLUE_ARCS);
        for(var pulse:PULSES){
            double age=Math.max(0,time-pulse.born);float r=pulse.pap?1:.08f,g=pulse.pap?.12f:1,b=pulse.pap?.04f:.20f;
            if(pulse.kind.endsWith("IMPACT")){
                if(age>4)continue;float alpha=(float)(1-age/4);double size=.10+age*(pulse.kind.equals("RAYGUN_IMPACT")?.26:.11);
                Vec3 normal=camera.subtract(pulse.to).normalize();
                ring(pose,out,pulse.to,normal,size,.035,r,g,b,alpha*.8f);
                ring(pose,out,pulse.to,normal,size*.6,.018,.7f,1,.75f,alpha*.8f);
                continue;
            }
            Vec3 axis=pulse.to.subtract(pulse.from);double length=axis.length();if(length<.001)continue;axis=axis.scale(1/length);
            boolean mkII=pulse.kind.equals("RAYGUN_MK2");
            if(!mkII&&age>1.5)continue;
            double travel=mkII?Math.min(length,age*10+1):length;
            if(mkII&&age*10>length+2)continue;
            Vec3 end=pulse.from.add(axis.scale(travel));Vec3 start=mkII?end.subtract(axis.scale(Math.min(travel,1.2))):pulse.from;
            line(pose,out,start,end,camera,mkII?.055:.045,r,g,b,.27f);
            line(pose,out,start,end,camera,mkII?.018:.013,.7f,1,.75f,.95f);
            if(!mkII){ring(pose,out,end,axis,.105,.018,r,g,b,.9f);ring(pose,out,start.lerp(end,.45),axis,.08,.012,r,g,b,.6f);}
        }
        pose.popPose();buffers.endBatch(ElectricLightningRenderType.BLUE_ARCS);
    }
    private static void ring(PoseStack pose,VertexConsumer out,Vec3 center,Vec3 normal,double radius,double width,float r,float g,float b,float alpha){
        Vec3 side=normal.cross(Math.abs(normal.y)>.9?new Vec3(1,0,0):new Vec3(0,1,0)).normalize(),up=normal.cross(side).normalize();
        for(int i=0;i<24;i++){
            double a=i*Math.PI/12,c=(i+1)*Math.PI/12;
            Vec3 v=side.scale(Math.cos(a)).add(up.scale(Math.sin(a))),w=side.scale(Math.cos(c)).add(up.scale(Math.sin(c)));
            vertex(pose,out,center.add(v.scale(radius-width)),r,g,b,alpha);vertex(pose,out,center.add(v.scale(radius+width)),r,g,b,alpha);
            vertex(pose,out,center.add(w.scale(radius+width)),r,g,b,alpha);vertex(pose,out,center.add(w.scale(radius-width)),r,g,b,alpha);
        }
    }
    private static void line(PoseStack pose,VertexConsumer out,Vec3 start,Vec3 end,Vec3 camera,double width,float r,float g,float b,float alpha){
        Vec3 side=end.subtract(start).cross(camera.subtract(start)).normalize().scale(width);
        vertex(pose,out,start.add(side),r,g,b,alpha);vertex(pose,out,start.subtract(side),r,g,b,alpha);vertex(pose,out,end.subtract(side),r,g,b,alpha);vertex(pose,out,end.add(side),r,g,b,alpha);
    }
    private static void vertex(PoseStack pose,VertexConsumer out,Vec3 point,float r,float g,float b,float a){out.vertex(pose.last().pose(),(float)point.x,(float)point.y,(float)point.z).color(r,g,b,a).endVertex();}
}
