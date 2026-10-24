package me.cryo.zombierool.gameplay;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** One population snapshot per tick, updated immediately for spawns and removals. */
@net.minecraftforge.fml.common.Mod.EventBusSubscriber(modid="zombierool")
public final class ActorPopulation {
    public static final int MAX_ALIVE=512;
    public static final int MARKER_LIMIT=480; // Leave space for scripted encounters and bosses.
    private static final Map<ServerLevel,Snapshot> LEVELS=new WeakHashMap<>();
    private static final class Snapshot {
        long tick=Long.MIN_VALUE;
        final Map<UUID,String> actors=new HashMap<>();
        final Map<String,Integer> markers=new HashMap<>();
        void add(Entity e){if(!MapActors.owns(e)||!e.isAlive()||actors.containsKey(e.getUUID()))return;String marker=e instanceof me.cryo.zombierool.entity.MapActorEntity a?a.marker():e.getPersistentData().getString("MapMarker");actors.put(e.getUUID(),marker);markers.merge(marker,1,Integer::sum);}
        void remove(Entity e){String marker=actors.remove(e.getUUID());if(marker!=null)markers.computeIfPresent(marker,(k,n)->n<=1?null:n-1);}
    }
    private static Snapshot get(ServerLevel level){var s=LEVELS.computeIfAbsent(level,k->new Snapshot());if(s.tick!=level.getGameTime()){s.tick=level.getGameTime();s.actors.clear();s.markers.clear();for(var e:level.getAllEntities())s.add(e);}return s;}
    public static int alive(ServerLevel level){return get(level).actors.size();}
    public static int marked(ServerLevel level){var s=get(level);return s.actors.size()-s.markers.getOrDefault("",0);}
    public static int alive(ServerLevel level,String marker){return get(level).markers.getOrDefault(marker,0);}
    public static void spawned(Entity e){if(e.level() instanceof ServerLevel level){var s=get(level);s.add(e);}}
    @SubscribeEvent public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent e){if(e.getLevel() instanceof ServerLevel level){var s=LEVELS.get(level);if(s!=null)s.remove(e.getEntity());}}
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(e.getEntity().level() instanceof ServerLevel level){var s=LEVELS.get(level);if(s!=null)s.remove(e.getEntity());}}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel level)LEVELS.remove(level);}
    private ActorPopulation(){}
}
