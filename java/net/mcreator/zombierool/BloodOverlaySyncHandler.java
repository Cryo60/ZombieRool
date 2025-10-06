package net.mcreator.zombierool.event;

import net.mcreator.zombierool.WorldConfig;
import net.mcreator.zombierool.client.BloodOverlayManager;
import net.mcreator.zombierool.network.NetworkHandler;
import net.mcreator.zombierool.network.SyncBloodOverlaysPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
public class BloodOverlaySyncHandler {

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            System.out.println("DEBUG: Player logged in, syncing overlays");
            syncOverlaysToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            System.out.println("DEBUG: Player changed dimension, syncing overlays");
            syncOverlaysToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            System.out.println("DEBUG: Player respawned, syncing overlays");
            syncOverlaysToPlayer(player);
        }
    }

    private static void syncOverlaysToPlayer(ServerPlayer player) {
        if (player.level() instanceof ServerLevel serverLevel) {
            WorldConfig config = WorldConfig.get(serverLevel);
            Map<String, String> overlays = config.getBloodOverlays();
            System.out.println("DEBUG: Syncing " + overlays.size() + " overlays to player");
            
            if (!overlays.isEmpty()) {
                SyncBloodOverlaysPacket packet = new SyncBloodOverlaysPacket(overlays);
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), packet);
            }
        }
    }
    
    // CLIENT SIDE
    @Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static class ClientHandler {
        @SubscribeEvent
        public static void onClientDisconnect(ClientPlayerNetworkEvent.LoggingOut event) {
            BloodOverlayManager.clearAll();
        }
    }
}