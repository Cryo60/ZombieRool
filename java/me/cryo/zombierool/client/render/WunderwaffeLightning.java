package me.cryo.zombierool.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.ArrayDeque;

/** Brief full-bright bolts remain visible even with minimal particles. */
@Mod.EventBusSubscriber(modid="zombierool",value=Dist.CLIENT)
public final class WunderwaffeLightning {
    private static final class Bolt {
        final Vec3 from,to;final boolean pap;final long born;
        long geometryTick=Long.MIN_VALUE;
        java.util.List<ElectricArcGeometry.Segment> geometry=java.util.List.of();
        Bolt(Vec3 from,Vec3 to,boolean pap,long born){this.from=from;this.to=to;this.pap=pap;this.born=born;}
        java.util.List<ElectricArcGeometry.Segment> segments(long now){
            if(geometryTick!=now/2){geometryTick=now/2;geometry=ElectricArcGeometry.create(from,to,born*31+geometryTick,1);}
            return geometry;
        }
    }
    private static final ArrayDeque<Bolt> BOLTS=new ArrayDeque<>();
    private static net.minecraft.client.multiplayer.ClientLevel world;
    private WunderwaffeLightning() {}
    @SubscribeEvent public static void disconnected(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) {
        BOLTS.clear();world=null;
    }
    public static void add(Vec3 from,Vec3 to,boolean pap) {
        var level=Minecraft.getInstance().level;
        if (level!=world) { BOLTS.clear();world=level; }
        if (level==null || from.distanceToSqr(to)<.0001) return;
        if (BOLTS.size()>=48) BOLTS.removeFirst();
        BOLTS.addLast(new Bolt(from,to,pap,level.getGameTime()));
    }
    @SubscribeEvent public static void render(RenderLevelStageEvent event) {
        if (event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        var mc=Minecraft.getInstance();
        if (mc.level!=world) { BOLTS.clear();world=mc.level; }
        if (mc.level==null) return;
        long now=mc.level.getGameTime();
        BOLTS.removeIf(bolt->now-bolt.born>=6 || now<bolt.born);
        if (BOLTS.isEmpty()) return;
        var pose=event.getPoseStack();var camera=event.getCamera().getPosition();
        pose.pushPose();pose.translate(-camera.x,-camera.y,-camera.z);
        var buffers=mc.renderBuffers().bufferSource();
        var vertices=buffers.getBuffer(ElectricLightningRenderType.BLUE_ARCS);
        for (var bolt:BOLTS) {
            float fade=1-(now-bolt.born+event.getPartialTick())/6f;
            for (var segment:bolt.segments(now)) {
                ElectricTrapLightning.tube(pose,vertices,segment,.045f,bolt.pap?1:.15f,bolt.pap?.15f:.55f,bolt.pap?.1f:1,.45f*fade);
                ElectricTrapLightning.tube(pose,vertices,segment,.012f,1,.95f,1,fade);
            }
        }
        buffers.endBatch(ElectricLightningRenderType.BLUE_ARCS);pose.popPose();
    }
}
