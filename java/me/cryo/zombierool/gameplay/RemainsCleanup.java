package me.cryo.zombierool.gameplay;

import java.util.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.*;

/** Global three-minute sweep; starts are staggered over thirty seconds, each fading for three seconds. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class RemainsCleanup {
    public static final int INTERVAL=3600,SPREAD=600,FADE=60;
    private static final Map<ServerLevel,Long> next=new WeakHashMap<>();
    public static final int MAX_GORE=1024,MAX_BLOOD=512,SOFT_CORPSES=256;
    private static final Map<ServerLevel,Index> INDEX=new WeakHashMap<>();
    private static final class Index {
        final Set<UUID> gore=new LinkedHashSet<>(),blood=new LinkedHashSet<>(),corpses=new LinkedHashSet<>();
        void add(Entity e){if(e instanceof me.cryo.zombierool.entity.GorePieceEntity)gore.add(e.getUUID());else if(e instanceof me.cryo.zombierool.entity.BloodDecalEntity)blood.add(e.getUUID());else if(remains(e))corpses.add(e.getUUID());}
        void remove(UUID id){gore.remove(id);blood.remove(id);corpses.remove(id);}
    }
    private static Index index(ServerLevel level){var found=INDEX.get(level);if(found!=null)return found;var created=new Index();INDEX.put(level,created);for(var e:level.getAllEntities())created.add(e);return created;}
    public static boolean allowGore(ServerLevel level){return index(level).gore.size()<MAX_GORE;}
    public static boolean allowBlood(ServerLevel level){return index(level).blood.size()<MAX_BLOOD;}
    @SubscribeEvent public static void joined(net.minecraftforge.event.entity.EntityJoinLevelEvent e){if(e.getLevel() instanceof ServerLevel level&&remains(e.getEntity()))index(level).add(e.getEntity());}
    @SubscribeEvent public static void died(net.minecraftforge.event.entity.living.LivingDeathEvent e){if(e.getEntity().level() instanceof ServerLevel level&&!(e.getEntity() instanceof net.minecraft.world.entity.player.Player))index(level).corpses.add(e.getEntity().getUUID());}
    @SubscribeEvent public static void left(net.minecraftforge.event.entity.EntityLeaveLevelEvent e){if(e.getLevel() instanceof ServerLevel level){var i=INDEX.get(level);if(i!=null)i.remove(e.getEntity().getUUID());}}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload e){if(e.getLevel() instanceof ServerLevel level){INDEX.remove(level);next.remove(level);}}
    private static void fade(Entity e,long start){e.getPersistentData().putLong("zr_cleanup_start",start);e.getPersistentData().putLong("zr_cleanup_end",start+FADE);CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(()->e),new Fade(e.getUUID(),start,start+FADE));}
    private static List<Entity> bodies(ServerLevel level){var i=index(level);var result=new ArrayList<Entity>();for(var set:List.of(i.gore,i.blood,i.corpses))for(var id:set){var e=level.getEntity(id);if(e!=null&&remains(e)&&!e.isRemoved())result.add(e);}return result;}
    private record Fade(UUID uuid,long start,long end){}
    private static final net.minecraftforge.network.simple.SimpleChannel CHANNEL=NetworkRegistry.newSimpleChannel(new net.minecraft.resources.ResourceLocation("zombierool","remains_cleanup"),()->"1","1"::equals,"1"::equals);
    public static void register(){CHANNEL.registerMessage(0,Fade.class,(m,b)->{b.writeUUID(m.uuid);b.writeLong(m.start);b.writeLong(m.end);},b->new Fade(b.readUUID(),b.readLong(),b.readLong()),(m,c)->{c.get().enqueueWork(()->net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.RemainsFade.receive(m.uuid,m.start,m.end)));c.get().setPacketHandled(true);},Optional.of(NetworkDirection.PLAY_TO_CLIENT));}
    public static boolean remains(Entity entity){return entity instanceof me.cryo.zombierool.entity.BloodDecalEntity||entity instanceof me.cryo.zombierool.entity.GorePieceEntity||entity instanceof LivingEntity living&&!(living instanceof net.minecraft.world.entity.player.Player)&&living.isDeadOrDying();}
    public static int schedule(ServerLevel level){var bodies=bodies(level);bodies.removeIf(e->e.getPersistentData().contains("zr_cleanup_end"));Collections.shuffle(bodies,new Random(level.getGameTime()));long now=level.getGameTime();for(int i=0;i<bodies.size();i++)fade(bodies.get(i),now+(long)i*SPREAD/Math.max(1,bodies.size()));return bodies.size();}
    private static void pressure(ServerLevel level,Set<UUID> set,int limit){int excess=set.size()-limit;if(excess<=0)return;int scheduled=0;for(var id:set){var e=level.getEntity(id);if(e==null)continue;long end=e.getPersistentData().getLong("zr_cleanup_end");if(end>0&&end<=level.getGameTime()+FADE+20){if(--excess<=0)break;continue;}fade(e,level.getGameTime()+scheduled%20);if(++scheduled>=64||--excess<=0)break;}}
    @SubscribeEvent public static void tick(TickEvent.LevelTickEvent event){if(event.phase!=TickEvent.Phase.END||!(event.level instanceof ServerLevel level)||level.getGameTime()%5!=0||WaveManager.isPausedByPlayer())return;long now=level.getGameTime();long due=next.computeIfAbsent(level,k->now+INTERVAL);if(now>=due){schedule(level);next.put(level,now+INTERVAL);}var i=index(level);var removed=new ArrayList<Entity>();for(var set:List.of(i.gore,i.blood,i.corpses))for(var iterator=set.iterator();iterator.hasNext();){var e=level.getEntity(iterator.next());if(e==null||!remains(e)||e.isRemoved()){iterator.remove();continue;}if(e.getPersistentData().contains("zr_cleanup_end")&&now>=e.getPersistentData().getLong("zr_cleanup_end")){iterator.remove();removed.add(e);}}removed.forEach(Entity::discard);if(now%20==0){pressure(level,i.gore,MAX_GORE*3/4);pressure(level,i.blood,MAX_BLOOD*3/4);pressure(level,i.corpses,SOFT_CORPSES);}}
    @SubscribeEvent public static void tracking(net.minecraftforge.event.entity.player.PlayerEvent.StartTracking event){var e=event.getTarget();if(event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player&&e.getPersistentData().contains("zr_cleanup_end"))CHANNEL.send(PacketDistributor.PLAYER.with(()->player),new Fade(e.getUUID(),e.getPersistentData().getLong("zr_cleanup_start"),e.getPersistentData().getLong("zr_cleanup_end")));}
    private RemainsCleanup(){}
}
