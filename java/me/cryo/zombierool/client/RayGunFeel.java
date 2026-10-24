package me.cryo.zombierool.client;

import com.mojang.math.Axis;
import me.cryo.zombierool.core.system.WeaponFacade;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Short recoil pulses recover independently of mouse input and do not accumulate camera drift. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class RayGunFeel {
    private record Pulse(double time,float pitch,float yaw){}
    private static final List<Pulse> PULSES=new ArrayList<>();
    private static Object ownerLevel;
    private static String weapon="";
    private static boolean markII;
    private RayGunFeel(){}
    private static double now(){var p=Minecraft.getInstance().player;return p==null?0:p.tickCount+Minecraft.getInstance().getFrameTime();}
    public static boolean kick(float pitch,float yaw){
        var mc=Minecraft.getInstance();if(mc.player==null)return false;
        var def=WeaponFacade.getDefinition(mc.player.getMainHandItem());
        if(def==null||!Set.of("zombierool:raygun","zombierool:raygunmarkii").contains(def.id))return false;
        if(ownerLevel!=mc.level||!weapon.equals(def.id))PULSES.clear();
        ownerLevel=mc.level;weapon=def.id;markII=def.id.endsWith("markii");
        PULSES.removeIf(p->now()-p.time>8);while(PULSES.size()>=6)PULSES.remove(0);
        PULSES.add(new Pulse(now(),pitch,yaw));return true;
    }
    private static float[] impulse(){
        var mc=Minecraft.getInstance();if(mc.player==null||ownerLevel!=mc.level){PULSES.clear();return new float[2];}
        var def=WeaponFacade.getDefinition(mc.player.getMainHandItem());
        if(def==null||!weapon.equals(def.id)){PULSES.clear();return new float[2];}
        double time=now();PULSES.removeIf(p->time-p.time>8);
        float pitch=0,yaw=0;
        for(var p:PULSES){double age=Math.max(0,time-p.time);float envelope=(float)((1-Math.exp(-age*7))*Math.exp(-age/(markII?1.15:1.65)));pitch+=p.pitch*envelope;yaw+=p.yaw*envelope;}
        return new float[]{Math.min(2.2f,pitch),yaw};
    }
    @SubscribeEvent public static void camera(ViewportEvent.ComputeCameraAngles event){
        var pulse=impulse();event.setPitch(event.getPitch()-pulse[0]);event.setYaw(event.getYaw()+pulse[1]);event.setRoll(event.getRoll()+pulse[1]*.35f);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void hand(RenderHandEvent event){
        if(event.getHand()!=InteractionHand.MAIN_HAND||event.isCanceled())return;
        var pulse=impulse();if(pulse[0]<=0)return;
        var mc=Minecraft.getInstance();float aim=mc.options.keyUse.isDown()?.45f:1;
        float kick=pulse[0]*aim;var pose=event.getPoseStack();
        pose.translate((markII?-.009:.014)*kick,-.012*kick,(markII?.08:.14)*kick);
        pose.mulPose(Axis.XP.rotationDegrees(-kick*(markII?5:10)));
        pose.mulPose(Axis.ZP.rotationDegrees(kick*(markII?-1.2f:2.3f)));
    }
    @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event){PULSES.clear();ownerLevel=null;weapon="";}
}
