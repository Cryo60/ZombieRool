package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.block.system.TraversalSystem;
import me.cryo.zombierool.block.system.TransportPackets;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.scripting.LuaScriptManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** The chosen curve is the sole movement source, on both sides of the connection. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class TraversalMotion {
    private static final Map<Level,Map<Integer,Flight>> FLIGHTS=new WeakHashMap<>();
    private static final String RESTORE="zr_traversal_restore";
    public static final class Flight {
        final Mob mob;
        public final Vec3 start,peak,end;
        public final int duration;
        int elapsed;
        final boolean noAi,noGravity,noPhysics,onGround;
        Flight(Mob mob,Vec3 start,Vec3 peak,Vec3 end,int duration,int elapsed) {
            this.mob=mob;this.start=start;this.peak=peak;this.end=end;this.duration=duration;this.elapsed=elapsed;
            noAi=mob.isNoAi();noGravity=mob.isNoGravity();noPhysics=mob.noPhysics;onGround=mob.onGround();
        }
    }
    private TraversalMotion(){}
    public static boolean active(Entity entity){return FLIGHTS.getOrDefault(entity.level(),Map.of()).get(entity.getId())!=null && FLIGHTS.get(entity.level()).get(entity.getId()).mob==entity;}
    public static Flight flight(Entity entity){return FLIGHTS.getOrDefault(entity.level(),Map.of()).get(entity.getId());}
    public static void start(Mob mob,TraversalSystem.Anchor route) {
        if(!(mob.level() instanceof ServerLevel)||active(mob))return;
        Flight flight=new Flight(mob,route.start(),route.peak(),route.end(),route.duration,0);
        begin(flight);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(()->mob),packet(flight));
        LuaScriptManager.callEvent("OnTraversalStarted",mob.getUUID().toString(),route.getBlockPos().getX(),route.getBlockPos().getY(),route.getBlockPos().getZ());
    }
    private static void begin(Flight f) {
        FLIGHTS.computeIfAbsent(f.mob.level(),l->new HashMap<>()).put(f.mob.getId(),f);
        if(!f.mob.level().isClientSide){
            CompoundTag restore=new CompoundTag();restore.putBoolean("noAi",f.noAi);restore.putBoolean("noGravity",f.noGravity);restore.putBoolean("noPhysics",f.noPhysics);restore.putDouble("x",f.start.x);restore.putDouble("y",f.start.y);restore.putDouble("z",f.start.z);f.mob.getPersistentData().put(RESTORE,restore);
            f.mob.getNavigation().stop();f.mob.setNoAi(true);
        }
        f.mob.setNoGravity(true);f.mob.noPhysics=true;f.mob.setOnGround(false);f.mob.setDeltaMovement(Vec3.ZERO);f.mob.fallDistance=0;
    }
    public static void receive(Entity entity,TransportPackets.Jump packet) {
        if(!(entity instanceof Mob mob)||!mob.getUUID().equals(packet.uuid()))return;
        Flight old=flight(mob);if(old!=null)restore(old);
        if(packet.duration()<=0){mob.setPos(packet.end().x,packet.end().y,packet.end().z);return;}
        begin(new Flight(mob,packet.start(),packet.peak(),packet.end(),packet.duration(),packet.elapsed()));
    }
    public static TransportPackets.Jump packet(Flight f){return new TransportPackets.Jump(f.mob.getId(),f.mob.getUUID(),f.start,f.peak,f.end,f.duration,f.elapsed);}
    private static TraversalCurve.Point point(Vec3 p){return new TraversalCurve.Point(p.x,p.y,p.z);}
    private static void tick(Level level) {
        for(Flight f:List.copyOf(FLIGHTS.getOrDefault(level,Map.of()).values())) {
            if(!f.mob.isAlive()||f.mob.isRemoved()){finish(f,false);continue;}
            f.elapsed++;
            var position=TraversalCurve.sample(point(f.start),point(f.peak),point(f.end),Math.min(1,(double)f.elapsed/f.duration));
            f.mob.setPos(position.x(),position.y(),position.z());f.mob.setDeltaMovement(Vec3.ZERO);f.mob.fallDistance=0;
            double dx=f.end.x-f.start.x,dz=f.end.z-f.start.z;
            float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
            f.mob.setYRot(yaw);f.mob.yBodyRot=yaw;f.mob.yHeadRot=yaw;
            if(f.elapsed>=f.duration)finish(f,true);
        }
    }
    private static void restore(Flight f) {
        FLIGHTS.getOrDefault(f.mob.level(),new HashMap<>()).remove(f.mob.getId());
        f.mob.setNoGravity(f.noGravity);f.mob.noPhysics=f.noPhysics;f.mob.setOnGround(f.onGround);f.mob.setDeltaMovement(Vec3.ZERO);f.mob.fallDistance=0;
        if(!f.mob.level().isClientSide){f.mob.setNoAi(f.noAi);f.mob.getPersistentData().remove(RESTORE);}
    }
    private static void finish(Flight f,boolean arrived) {
        restore(f);
        if(!f.mob.level().isClientSide) {
            NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(()->f.mob),new TransportPackets.Jump(f.mob.getId(),f.mob.getUUID(),f.mob.position(),f.mob.position(),f.mob.position(),0,0));
            if(arrived)LuaScriptManager.callEvent("OnTraversalFinished",f.mob.getUUID().toString());
        }
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)for(ServerLevel level:e.getServer().getAllLevels())tick(level);}
    public static void clientTick(Level level){if(level!=null)tick(level);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){Flight f=flight(e.getTarget());if(f!=null)NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->(net.minecraft.server.level.ServerPlayer)e.getEntity()),packet(f));}
    @SubscribeEvent public static void join(EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide && e.getEntity() instanceof Mob mob && mob.getPersistentData().contains(RESTORE)){
            CompoundTag n=mob.getPersistentData().getCompound(RESTORE);mob.setNoAi(n.getBoolean("noAi"));mob.setNoGravity(n.getBoolean("noGravity"));mob.noPhysics=n.getBoolean("noPhysics");mob.setPos(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));mob.getPersistentData().remove(RESTORE);
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof Level level){for(Flight f:List.copyOf(FLIGHTS.getOrDefault(level,Map.of()).values()))restore(f);FLIGHTS.remove(level);}}
}
