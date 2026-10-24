package me.cryo.zombierool.client;

import me.cryo.zombierool.block.system.*;
import me.cryo.zombierool.gameplay.TraversalCurve;
import me.cryo.zombierool.gameplay.TraversalMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class TransportClient {
    public static void receive(TransportPackets.Jump packet){var level=Minecraft.getInstance().level;if(level!=null){var e=level.getEntity(packet.entityId());if(e!=null)TraversalMotion.receive(e,packet);}}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();me.cryo.zombierool.integration.TacZPauseClock.paused(mc.level!=null&&mc.isPaused());if(mc.isPaused())return;TraversalMotion.clientTick(mc.level);
        if(mc.level==null||mc.player==null||!mc.player.isCreative()||!(mc.player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem item)||!(item.getBlock() instanceof TraversalSystem.TraversalBlock)||mc.level.getGameTime()%10!=0)return;
        for(var a:TraversalSystem.loaded(mc.level)){
            if(a.getBlockPos().distSqr(mc.player.blockPosition())>144||!a.ready())continue;
            var route=new TraversalSystem.Route(a,false);Vec3 s=route.start(),p=route.peak(),e=route.end();
            for(int i=0;i<=24;i++){
                Vec3 point;
                if(a.kind()==TraversalSystem.Kind.WALL||a.kind()==TraversalSystem.Kind.ZIPLINE)point=s.lerp(e,i/24.0);
                else {var curve=TraversalCurve.sample(new TraversalCurve.Point(s.x,s.y,s.z),new TraversalCurve.Point(p.x,p.y,p.z),new TraversalCurve.Point(e.x,e.y,e.z),i/24.0);point=new Vec3(curve.x(),curve.y(),curve.z());}
                mc.level.addParticle(ParticleTypes.END_ROD,point.x,point.y+.05,point.z,0,0,0);
            }
        }
    }
}
