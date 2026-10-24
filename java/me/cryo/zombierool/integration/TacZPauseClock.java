package me.cryo.zombierool.integration;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.ModList;
import java.util.WeakHashMap;

/** Shares the integrated client's true pause duration without loading client classes on servers. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class TacZPauseClock {
    private static volatile long accumulatedNs,pauseStartedNs;
    private static final WeakHashMap<net.minecraft.server.MinecraftServer,Long> accounted=new WeakHashMap<>();
    private TacZPauseClock(){}
    public static synchronized void paused(boolean paused){
        if(paused&&pauseStartedNs==0)pauseStartedNs=System.nanoTime();
        else if(!paused&&pauseStartedNs!=0){accumulatedNs+=System.nanoTime()-pauseStartedNs;pauseStartedNs=0;}
    }
    public static synchronized long totalMillis(){return (accumulatedNs+(pauseStartedNs==0?0:System.nanoTime()-pauseStartedNs))/1_000_000;}
    /** Actions received on resume must not inherit time from before they existed. */
    public static long compensateTimestamp(long timestamp,long pauseMillis,long resumedAt){
        if(timestamp==-1)return -1;
        return timestamp+Math.min(Math.max(0,pauseMillis),Math.max(0,resumedAt-timestamp));
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(e.phase!=TickEvent.Phase.START||e.getServer().isDedicatedServer()||!ModList.get().isLoaded("tacz"))return;
        long total=totalMillis();Long old=accounted.put(e.getServer(),total);long delta=old==null?0:total-old;if(delta<=0)return;
        for(var level:e.getServer().getAllLevels())for(var entity:level.getAllEntities())
            if(entity instanceof net.minecraft.world.entity.LivingEntity living)TacZIntegration.compensatePause(living,delta);
    }
}
