package me.cryo.zombierool.gameplay;

import java.util.*;
import me.cryo.zombierool.config.WorldConfig;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CDebugSettingsPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.*;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/** Session-only debug controls. They never change a player's game mode. */
@Mod.EventBusSubscriber(modid="zombierool")
public final class DebugPlayerControls {
    private static final Map<UUID,State> STATES = new HashMap<>();
    private static final Map<UUID,Integer> SENT_LIMITS = new HashMap<>();
    public static boolean clientGod, clientNoclip;
    public static int clientPerkLimit = 4;
    private static final class State {
        boolean god,noclip,mayfly,flying,phasing; float speed; double x,y,z;
    }
    public static boolean god(Player player) { return player.level().isClientSide ? clientGod : STATES.containsKey(player.getUUID()) && STATES.get(player.getUUID()).god; }
    public static boolean noclip(Player player) { return player.level().isClientSide ? clientNoclip : STATES.containsKey(player.getUUID()) && STATES.get(player.getUUID()).noclip; }
    public static void setGod(ServerPlayer player,boolean enabled) {
        STATES.computeIfAbsent(player.getUUID(),id->new State()).god=enabled;
        if(enabled){player.clearFire();player.fallDistance=0;}
        sync(player);
    }
    public static void setNoclip(ServerPlayer player,boolean enabled) {
        State state=STATES.computeIfAbsent(player.getUUID(),id->new State());
        if(state.noclip==enabled)return;
        var abilities=player.getAbilities();
        if(enabled){
            state.mayfly=abilities.mayfly;state.flying=abilities.flying;state.speed=abilities.getFlyingSpeed();
            state.x=player.getX();state.y=player.getY();state.z=player.getZ();
            player.stopRiding();abilities.mayfly=true;
        }else{
            moveOutOfSolid(player,state);
            abilities.mayfly=player.isCreative()||player.isSpectator()||state.mayfly;
            abilities.flying=player.isSpectator()||abilities.mayfly&&state.flying;abilities.setFlyingSpeed(state.speed);
        }
        state.noclip=enabled;state.phasing=enabled&&abilities.flying;player.noPhysics=state.phasing||player.isSpectator();player.fallDistance=0;player.onUpdateAbilities();sync(player);
    }
    private static void moveOutOfSolid(ServerPlayer player,State state){
            // Never strand a survival player inside a wall when collision is restored.
            if(!player.serverLevel().noCollision(player,player.getBoundingBox())){
                BlockPos origin=player.blockPosition();boolean found=false;
                for(int radius=1;radius<=6&&!found;radius++)for(BlockPos pos:BlockPos.betweenClosed(origin.offset(-radius,-radius,-radius),origin.offset(radius,radius,radius))){
                    var box=player.getBoundingBox().move(pos.getX()+.5-player.getX(),pos.getY()-player.getY(),pos.getZ()+.5-player.getZ());
                    if(player.serverLevel().getWorldBorder().isWithinBounds(pos)&&player.serverLevel().noCollision(player,box)){
                        player.teleportTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);found=true;break;
                    }
                }
                if(!found)player.teleportTo(state.x,state.y,state.z);
            }
    }
    public static void applyCollision(Player player){if(noclip(player)){player.noPhysics=player.isSpectator()||player.getAbilities().flying;player.fallDistance=0;if(player instanceof ServerPlayer&&player.getAbilities().flying)STATES.get(player.getUUID()).phasing=true;}}
    public static void reset(ServerPlayer player){
        if(noclip(player))setNoclip(player,false);
        STATES.remove(player.getUUID());player.noPhysics=player.isSpectator();sync(player);
    }
    public static void sync(ServerPlayer player){
        int limit=WorldConfig.get(player.serverLevel()).getMaxPerks();SENT_LIMITS.put(player.getUUID(),limit);
        NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new S2CDebugSettingsPacket(god(player),noclip(player),limit));
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void attack(LivingAttackEvent event){if(event.getEntity() instanceof Player p&&(god(p)||noclip(p)))event.setCanceled(true);}
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void damage(LivingDamageEvent event){if(event.getEntity() instanceof Player p&&(god(p)||noclip(p)))event.setCanceled(true);}
    @SubscribeEvent public static void tick(TickEvent.PlayerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        applyCollision(event.player);
        if(event.player instanceof ServerPlayer p){
            if(god(p)||noclip(p)){p.clearFire();p.fallDistance=0;}
            if(noclip(p)){
                p.getAbilities().mayfly=true;
                State state=STATES.get(p.getUUID());
                if(state.phasing&&!p.getAbilities().flying&&!p.serverLevel().noCollision(p,p.getBoundingBox())){
                    // Turning flight off inside a solid block must restore a safe walking position.
                    moveOutOfSolid(p,state);
                }
                state.phasing=p.getAbilities().flying;
            }
            if(p.tickCount%20==0&&!Objects.equals(SENT_LIMITS.get(p.getUUID()),WorldConfig.get(p.serverLevel()).getMaxPerks()))sync(p);
        }
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p){STATES.remove(p.getUUID());sync(p);}}
    // Forge posts logout before PlayerList saves abilities, so restoration is persisted normally.
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p){reset(p);SENT_LIMITS.remove(p.getUUID());}}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent e){if(e.getEntity() instanceof ServerPlayer p)reset(p);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){for(var p:e.getServer().getPlayerList().getPlayers())reset(p);STATES.clear();SENT_LIMITS.clear();}
    @Mod.EventBusSubscriber(modid="zombierool",value=net.minecraftforge.api.distmarker.Dist.CLIENT)
    public static final class ClientEvents {
        @SubscribeEvent public static void logout(net.minecraftforge.client.event.ClientPlayerNetworkEvent.LoggingOut e){clientGod=false;clientNoclip=false;clientPerkLimit=4;}
    }
    private DebugPlayerControls(){}
}
