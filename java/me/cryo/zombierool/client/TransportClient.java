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
        var mc=Minecraft.getInstance();TraversalMotion.clientTick(mc.level);
        if(mc.level==null||mc.player==null||!mc.player.isCreative()||!mc.player.getMainHandItem().is(TraversalSystem.ITEM.get())||mc.level.getGameTime()%10!=0)return;
        var center=mc.player.blockPosition();
        for(int x=-12;x<=12;x++)for(int z=-12;z<=12;z++)for(int y=-4;y<=6;y++){
            var a=TraversalSystem.entry(mc.level,center.offset(x,y,z));if(a==null||!a.ready())continue;
            Vec3 s=a.start(),p=a.peak(),e=a.end();
            for(int i=0;i<=24;i++){
                var point=TraversalCurve.sample(new TraversalCurve.Point(s.x,s.y,s.z),new TraversalCurve.Point(p.x,p.y,p.z),new TraversalCurve.Point(e.x,e.y,e.z),i/24.0);
                mc.level.addParticle(ParticleTypes.END_ROD,point.x(),point.y()+.05,point.z(),0,0,0);
            }
        }
    }
}
