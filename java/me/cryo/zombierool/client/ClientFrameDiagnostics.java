package me.cryo.zombierool.client;

import me.cryo.zombierool.ZombieroolMod;
import me.cryo.zombierool.entity.MapActorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Record rare in-game stalls without sampling entities or querying the graphics driver each frame. */
@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT)
public final class ClientFrameDiagnostics {
    private static final long STALL_NS = 250_000_000L;
    private static long lastFrame, renderStart, lastReport, previousGc;

    private static long gcMillis() {
        long total = 0;
        for (var collector : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) {
            total += Math.max(0, collector.getCollectionTime());
        }
        return total;
    }

    @SubscribeEvent
    public static void render(TickEvent.RenderTickEvent event) {
        var mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (mc.level == null || mc.player == null || mc.screen != null || !mc.isWindowActive()
                || mc.player.tickCount < 100) {
            lastFrame = 0;
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            renderStart = now;
            return;
        }
        if (lastFrame == 0) previousGc = gcMillis();
        long gap = lastFrame == 0 ? 0 : now - lastFrame;
        lastFrame = now;
        if (gap < STALL_NS || now - lastReport < 5_000_000_000L) return;
        lastReport = now;
        int entities = 0, actors = 0, corpses = 0, gore = 0, blood = 0, living = 0;
        for (var entity : mc.level.entitiesForRendering()) {
            entities++;
            if (entity instanceof MapActorEntity) actors++;
            if (entity instanceof LivingEntity body && !body.isAlive()) corpses++;
            if (entity instanceof LivingEntity) living++;
            if (entity instanceof me.cryo.zombierool.entity.GorePieceEntity) gore++;
            if (entity instanceof me.cryo.zombierool.entity.BloodDecalEntity) blood++;
        }
        long gc = gcMillis();
        var runtime = Runtime.getRuntime();
        ZombieroolMod.LOGGER.warn(
                "[Client stall] frameGapMs={} renderMs={} gcSinceLastReportMs={} entities={} mapActors={} corpses={} heapMiB={}/{} living={} gore={} blood={} fogComposite={} halloween={} renderStats={}",
                gap / 1_000_000, Math.max(0, now - renderStart) / 1_000_000, Math.max(0, gc - previousGc),
                entities, actors, corpses, (runtime.totalMemory() - runtime.freeMemory()) / 1_048_576,
                runtime.maxMemory() / 1_048_576, living, gore, blood, ClientEnvironmentEffects.usesCompositedFog(),
                HalloweenManager.isHalloweenPeriod(), mc.levelRenderer.getEntityStatistics());
        previousGc = gc;
    }
}
