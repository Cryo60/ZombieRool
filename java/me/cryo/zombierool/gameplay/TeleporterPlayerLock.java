package me.cryo.zombierool.gameplay;

import java.util.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;

/** Short, authoritative action lock; the stay in a destination room is never locked. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class TeleporterPlayerLock {
    public static final int TUNNEL_TICKS=48,DAZE_TICKS=18;
    private record Hold(net.minecraft.world.level.Level level,Vec3 position,float yaw,float pitch,long until){}
    // Entity equality uses numeric IDs, shared by client/server players in solo mode.
    private static final Map<Player,Hold> HOLDS=Collections.synchronizedMap(new IdentityHashMap<>());
    private TeleporterPlayerLock(){}
    public static void update(Player player,int chargeTicks){if(chargeTicks==0)hold(player,TUNNEL_TICKS);else release(player);}
    public static void hold(Player player,int ticks){
        release(player);if(ticks<=0||!player.isAlive()||player.isSpectator())return;
        player.stopRiding();player.stopUsingItem();player.setSprinting(false);player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;
        HOLDS.put(player,new Hold(player.level(),player.position(),player.getYRot(),player.getXRot(),player.level().getGameTime()+ticks));
        if(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer){EquipmentSystem.cancel(serverPlayer);me.cryo.zombierool.handlers.LethalWeaponManager.cancel(serverPlayer);BrutusMachineLocks.cancelUnlock(serverPlayer);if(me.cryo.zombierool.player.PlayerDownManager.isPlayerReviving(player.getUUID()))me.cryo.zombierool.player.PlayerDownManager.cancelRevive(serverPlayer.serverLevel(),player.getUUID());}
        for(var stack:player.getHandSlots())if(stack.getItem() instanceof me.cryo.zombierool.core.system.WeaponSystem.BaseGunItem gun){
            var tag=stack.getOrCreateTag();if(tag.getBoolean(gun.TAG_IS_RELOADING))player.getCooldowns().removeCooldown(gun);tag.putBoolean(gun.TAG_IS_RELOADING,false);tag.putInt(gun.TAG_RELOAD_TIMER,0);tag.putInt(gun.TAG_BURST_SHOTS_LEFT,0);tag.putInt(gun.TAG_BURST_DELAY,0);
        }
        me.cryo.zombierool.integration.TacZIntegration.stopTeleporterActions(player);
    }
    public static boolean active(Entity entity){
        if(!(entity instanceof Player player))return false;
        Hold hold=HOLDS.get(player);if(hold==null)return false;
        // Explicit server teleports/dimension changes cancel a previous anchor.
        if(player.level()!=hold.level||!player.isAlive()||player.isSpectator()||player.isCreative()||player.level().getGameTime()>=hold.until||player.position().distanceToSqr(hold.position)>.0025){release(player);return false;}
        return true;
    }
    public static void release(Player player){HOLDS.remove(player);}
    public static void reset(net.minecraft.world.level.Level level){synchronized(HOLDS){HOLDS.entrySet().removeIf(e->e.getValue().level==level);}}
    public static void enforce(Player player){
        if(!active(player))return;
        Hold hold=HOLDS.get(player);player.setDeltaMovement(Vec3.ZERO);player.fallDistance=0;player.setSprinting(false);player.stopUsingItem();
        player.setYRot(hold.yaw);player.setXRot(hold.pitch);player.setYHeadRot(hold.yaw);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void tick(TickEvent.PlayerTickEvent event){enforce(event.player);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void interact(PlayerInteractEvent event){if(active(event.getEntity())&&event.isCancelable())event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void attack(AttackEntityEvent event){if(active(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void use(net.minecraftforge.event.entity.living.LivingEntityUseItemEvent.Start event){if(active(event.getEntity()))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void breakBlock(net.minecraftforge.event.level.BlockEvent.BreakEvent event){if(active(event.getPlayer()))event.setCanceled(true);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){release(event.getEntity());}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){release(event.getOriginal());release(event.getEntity());}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){release(event.getEntity());}
    @SubscribeEvent public static void death(net.minecraftforge.event.entity.living.LivingDeathEvent event){if(event.getEntity() instanceof Player player)release(player);}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.LevelEvent.Unload event){if(event.getLevel() instanceof net.minecraft.world.level.Level level)reset(level);}
}
