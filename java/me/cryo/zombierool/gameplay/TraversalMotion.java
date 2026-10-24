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
        final net.minecraft.world.entity.LivingEntity mob;
        public final Vec3 start,peak,end;
        public final int duration;
        public final int style;
        int elapsed;
        int attackCooldown;
        final boolean noAi,noGravity,noPhysics,onGround;
        Flight(net.minecraft.world.entity.LivingEntity mob,Vec3 start,Vec3 peak,Vec3 end,int duration,int elapsed,int style) {
            this.mob=mob;this.start=start;this.peak=peak;this.end=end;this.duration=duration;this.elapsed=elapsed;this.style=style;
            noAi=mob instanceof Mob m&&m.isNoAi();noGravity=mob.isNoGravity();noPhysics=mob.noPhysics;onGround=mob.onGround();
        }
    }
    private TraversalMotion(){}
    public static boolean active(Entity entity){return FLIGHTS.getOrDefault(entity.level(),Map.of()).get(entity.getId())!=null && FLIGHTS.get(entity.level()).get(entity.getId()).mob==entity;}
    public static Flight flight(Entity entity){return FLIGHTS.getOrDefault(entity.level(),Map.of()).get(entity.getId());}
    public static boolean previousNoAi(Mob mob){
        Flight f=flight(mob);
        return f!=null?f.noAi:mob.getPersistentData().contains(RESTORE)?mob.getPersistentData().getCompound(RESTORE).getBoolean("noAi"):mob.isNoAi();
    }
    public static void cancel(Entity entity){
        Flight f=flight(entity);if(f!=null){finish(f,false);return;}
        // A serialized/reloaded route may retain its lock after the in-memory flight is gone.
        // Explicit cancellation restores flags without moving the entity back to an old route start.
        if(!entity.level().isClientSide&&entity.getPersistentData().contains(RESTORE)){
            var saved=entity.getPersistentData().getCompound(RESTORE);
            if(entity instanceof Mob mob)mob.setNoAi(!mob.isAlive()||saved.getBoolean("noAi"));
            entity.setNoGravity(saved.getBoolean("noGravity"));entity.noPhysics=saved.getBoolean("noPhysics");
            entity.getPersistentData().remove(RESTORE);if(CrawlerLeap.isCrawler(entity))entity.refreshDimensions();
        }
    }
    public static void start(Mob mob,TraversalSystem.Anchor route){start(mob,new TraversalSystem.Route(route,false));}
    public static void start(net.minecraft.world.entity.LivingEntity entity,TraversalSystem.Route route) {
        if(entity instanceof me.cryo.zombierool.entity.SpecialEnemyEntity boss&&boss.isBoss()&&entity.getPersistentData().getBoolean("zr_boss_machine"))return;
        if(!(entity.level() instanceof ServerLevel)||active(entity)||entity instanceof Mob mob&&FiestaFreeze.frozen(mob))return;
        if(entity instanceof Mob mob){if(!BossTraversalHistory.allows(mob,route))return;BossTraversalHistory.started(mob,route);}
        int style=route.anchor().kind()==TraversalSystem.Kind.WALL?2:route.anchor().kind()==TraversalSystem.Kind.ZIPLINE?1:0;
        if(entity instanceof me.cryo.zombierool.entity.SpecialEnemyEntity geist&&CrawlerLeap.isCrawler(geist))geist.crawlerLeap.reset();
        Flight flight=new Flight(entity,route.start(),route.peak(),route.end(),route.anchor().duration,0,style);
        begin(flight);
        if(style==1)entity.level().playSound(null,entity.blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.ZIPLINE_ATTACH.get(),net.minecraft.sounds.SoundSource.BLOCKS,.7f,1);
        NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(()->entity),packet(flight));
        if(entity instanceof net.minecraft.server.level.ServerPlayer player)NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),packet(flight));
        LuaScriptManager.callEvent("OnTraversalStarted",entity.getUUID().toString(),route.anchor().getBlockPos().getX(),route.anchor().getBlockPos().getY(),route.anchor().getBlockPos().getZ());
    }
    private static void begin(Flight f) {
        FLIGHTS.computeIfAbsent(f.mob.level(),l->new HashMap<>()).put(f.mob.getId(),f);
        if(!f.mob.level().isClientSide){
            CompoundTag restore=new CompoundTag();restore.putBoolean("noAi",f.noAi);restore.putBoolean("noGravity",f.noGravity);restore.putBoolean("noPhysics",f.noPhysics);restore.putDouble("x",f.start.x);restore.putDouble("y",f.start.y);restore.putDouble("z",f.start.z);f.mob.getPersistentData().put(RESTORE,restore);
            if(f.mob instanceof Mob m){m.getNavigation().stop();m.setNoAi(true);}
        }
        f.mob.setNoGravity(true);f.mob.noPhysics=true;f.mob.setOnGround(false);f.mob.setDeltaMovement(Vec3.ZERO);f.mob.fallDistance=0;
        if(CrawlerLeap.isCrawler(f.mob))f.mob.refreshDimensions();
        if(f.mob.level().isClientSide&&f.style==1)net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,()->()->me.cryo.zombierool.client.ZiplineSounds.start(f.mob));
    }
    public static void receive(Entity entity,TransportPackets.Jump packet) {
        if(!(entity instanceof net.minecraft.world.entity.LivingEntity mob)||!mob.getUUID().equals(packet.uuid()))return;
        Flight old=flight(mob);if(old!=null)restore(old);
        if(packet.duration()<=0){mob.setPos(packet.end().x,packet.end().y,packet.end().z);return;}
        begin(new Flight(mob,packet.start(),packet.peak(),packet.end(),packet.duration(),packet.elapsed(),packet.style()));
    }
    public static TransportPackets.Jump packet(Flight f){return new TransportPackets.Jump(f.mob.getId(),f.mob.getUUID(),f.start,f.peak,f.end,f.duration,f.elapsed,f.style);}
    private static TraversalCurve.Point point(Vec3 p){return new TraversalCurve.Point(p.x,p.y,p.z);}
    public static Vec3 renderPosition(Entity entity,float partialTick){
        Flight f=flight(entity);if(f==null||entity instanceof Mob mob&&FiestaFreeze.frozen(mob))return entity.position();
        double t=Math.max(0,Math.min(1,(f.elapsed-1+partialTick)/f.duration));
        if(f.style!=0)return f.start.lerp(f.end,t);
        var p=TraversalCurve.sample(point(f.start),point(f.peak),point(f.end),t);return new Vec3(p.x(),p.y(),p.z());
    }
    private static void tick(Level level) {
        for(Flight f:List.copyOf(FLIGHTS.getOrDefault(level,Map.of()).values())) {
            if(!f.mob.isAlive()||f.mob.isRemoved()){finish(f,false);continue;}
            if(f.mob instanceof Mob mob&&FiestaFreeze.frozen(mob))continue;
            f.elapsed++;
            double progress=Math.min(1,(double)f.elapsed/f.duration);
            var position=f.style==0?TraversalCurve.sample(point(f.start),point(f.peak),point(f.end),progress):point(f.start.lerp(f.end,progress));
            f.mob.setNoGravity(true);f.mob.noPhysics=true;f.mob.setOnGround(false);
            Vec3 previous=f.mob.position();
            f.mob.setPos(position.x(),position.y(),position.z());
            if(!level.isClientSide&&f.style==1)attackPassingPlayers(f,previous);
            if(f.mob instanceof net.minecraft.server.level.ServerPlayer player)player.serverLevel().getChunkSource().move(player);f.mob.setDeltaMovement(Vec3.ZERO);f.mob.fallDistance=0;
            double dx=(f.style==2?f.peak.x:f.end.x)-f.start.x,dz=(f.style==2?f.peak.z:f.end.z)-f.start.z;
            float yaw=(float)(Math.toDegrees(Math.atan2(dz,dx))-90);
            if(f.mob instanceof Mob){f.mob.setYRot(yaw);f.mob.yBodyRot=yaw;f.mob.yHeadRot=yaw;}
            if(f.elapsed>=f.duration)finish(f,true);
        }
    }
    private static void attackPassingPlayers(Flight f,Vec3 previous) {
        if(!(f.mob instanceof Mob mob)||mob instanceof me.cryo.zombierool.entity.WhiteKnightEntity
                ||(!(mob instanceof me.cryo.zombierool.entity.AbstractZombieRoolEntity)&&!(mob instanceof me.cryo.zombierool.entity.MapActorEntity)))return;
        if(f.attackCooldown>0){f.attackCooldown--;return;}
        Vec3 delta=mob.position().subtract(previous);
        for(var player:mob.level().players()){
            if(!(player instanceof net.minecraft.server.level.ServerPlayer sp)||!me.cryo.zombierool.block.system.TeleporterSystem.validPlayer(sp)
                    ||me.cryo.zombierool.player.PlayerDownManager.isPlayerDown(player.getUUID())
                    ||me.cryo.zombierool.bonuses.BonusManager.isZombieBloodActive(player)||!mob.canAttack(player))continue;
            double t=delta.lengthSqr()<1e-8?0:net.minecraft.util.Mth.clamp(player.position().subtract(previous).dot(delta)/delta.lengthSqr(),0,1);
            Vec3 nearest=previous.add(delta.scale(t));
            double dx=nearest.x-player.getX(),dz=nearest.z-player.getZ();
            double reach=mob.getBbWidth()+player.getBbWidth()/2+.6;
            if(dx*dx+dz*dz>reach*reach||Math.abs(nearest.y-player.getY())>1.5)continue;
            if(mob.level().clip(new net.minecraft.world.level.ClipContext(nearest.add(0,.6,0),player.position().add(0,.6,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mob)).getType()!=net.minecraft.world.phys.HitResult.Type.MISS)continue;
            mob.doHurtTarget(player);f.attackCooldown=20;break;
        }
    }
    private static void restore(Flight f) {
        FLIGHTS.getOrDefault(f.mob.level(),new HashMap<>()).remove(f.mob.getId());
        if(CrawlerLeap.isCrawler(f.mob))f.mob.refreshDimensions();
        boolean dead=!f.mob.isAlive();
        f.mob.setNoGravity(dead?false:f.noGravity);f.mob.noPhysics=dead?false:f.noPhysics;f.mob.setOnGround(dead?false:f.onGround);f.mob.setDeltaMovement(Vec3.ZERO);f.mob.fallDistance=0;
        if(!f.mob.level().isClientSide){if(f.mob instanceof Mob m)m.setNoAi(dead||f.noAi);f.mob.getPersistentData().remove(RESTORE);}
    }
    private static void finish(Flight f,boolean arrived) {
        restore(f);
        if(!f.mob.level().isClientSide&&f.mob instanceof Mob mob){mob.getNavigation().stop();me.cryo.zombierool.hotfix.ReachablePlayerTargetGoal.invalidate(mob);}
        if(!f.mob.level().isClientSide) {
            if(f.mob instanceof net.minecraft.server.level.ServerPlayer player)player.connection.teleport(player.getX(),player.getY(),player.getZ(),player.getYRot(),player.getXRot());
            NetworkHandler.INSTANCE.send(PacketDistributor.TRACKING_ENTITY.with(()->f.mob),new TransportPackets.Jump(f.mob.getId(),f.mob.getUUID(),f.mob.position(),f.mob.position(),f.mob.position(),0,0,f.style));
            if(f.mob instanceof net.minecraft.server.level.ServerPlayer player)NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new TransportPackets.Jump(player.getId(),player.getUUID(),player.position(),player.position(),player.position(),0,0,f.style));
            if(arrived){
                if(f.style==1&&f.mob instanceof net.minecraft.server.level.ServerPlayer player)me.cryo.zombierool.career.CareerManager.progressGameplay(player,me.cryo.zombierool.career.CareerManager.ChallengeType.ZIPLINE_TRIPS,1);
                if(f.style==1)f.mob.level().playSound(null,f.mob.blockPosition(),me.cryo.zombierool.init.ZombieroolModSounds.ZIPLINE_ARRIVE.get(),net.minecraft.sounds.SoundSource.BLOCKS,.65f,1);
                LuaScriptManager.callEvent("OnTraversalFinished",f.mob.getUUID().toString());
            }
        }
    }
    public static void reset(Level level){for(Flight f:List.copyOf(FLIGHTS.getOrDefault(level,Map.of()).values()))finish(f,false);FLIGHTS.remove(level);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){Flight f=flight(e.getEntity());if(f!=null)finish(f,false);}
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e){if(e.phase==TickEvent.Phase.END)for(ServerLevel level:e.getServer().getAllLevels())tick(level);}
    public static void clientTick(Level level){if(level!=null)tick(level);}
    @SubscribeEvent public static void tracking(PlayerEvent.StartTracking e){Flight f=flight(e.getTarget());if(f!=null)NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->(net.minecraft.server.level.ServerPlayer)e.getEntity()),packet(f));}
    @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.HIGH) public static void join(EntityJoinLevelEvent e){
        if(!e.getLevel().isClientSide && e.getEntity() instanceof net.minecraft.world.entity.LivingEntity mob && mob.getPersistentData().contains(RESTORE)){
            CompoundTag n=mob.getPersistentData().getCompound(RESTORE);if(mob instanceof Mob m)m.setNoAi(n.getBoolean("noAi"));mob.setNoGravity(n.getBoolean("noGravity"));mob.noPhysics=n.getBoolean("noPhysics");mob.setPos(n.getDouble("x"),n.getDouble("y"),n.getDouble("z"));mob.getPersistentData().remove(RESTORE);
        }
    }
    @SubscribeEvent public static void unload(LevelEvent.Unload e){if(e.getLevel() instanceof Level level){for(Flight f:List.copyOf(FLIGHTS.getOrDefault(level,Map.of()).values()))restore(f);FLIGHTS.remove(level);}}
}
