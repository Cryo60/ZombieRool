package me.cryo.zombierool;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;
import me.cryo.zombierool.configuration.HalloweenConfig;

public class MapEventManager {
	private static final String MARKER_FILE = "zombierool_map.cfg";
	private static final String KEYWORD_SPOOKY = "SPOOKY_AMBIENCE";
	private static final String KEYWORD_HALLOWEEN = "FORCE_HALLOWEEN";
	private static final int INTERVAL_SPOOKY = 60; 
	private static final int INTERVAL_SPOOKY_2D = 240; 
	private static final float CHANCE_SPOOKY = 0.4F; 
	private static final float CHANCE_SPOOKY_2D = 0.55F; 
	
	private static boolean serverSpookyActive = false;
	private static boolean serverHalloweenForced = false;
	private static int serverTickCounter = 0;
	private static String lastCheckedWorld = "";
	
	public static class MapConfigPacket {
	    private final boolean spookyActive;
	    private final boolean halloweenForced;
	
	    public MapConfigPacket(boolean spooky, boolean halloween) {
	        this.spookyActive = spooky;
	        this.halloweenForced = halloween;
	    }
	
	    public MapConfigPacket(FriendlyByteBuf buf) {
	        this.spookyActive = buf.readBoolean();
	        this.halloweenForced = buf.readBoolean();
	    }
	
	    public static void encode(MapConfigPacket msg, FriendlyByteBuf buf) {
	        buf.writeBoolean(msg.spookyActive);
	        buf.writeBoolean(msg.halloweenForced);
	    }
	
	    public static MapConfigPacket decode(FriendlyByteBuf buf) {
	        return new MapConfigPacket(buf);
	    }
	
	    public static void handle(MapConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
	        ctx.get().enqueueWork(() -> {
	            ClientHandler.handleMapConfig(msg.spookyActive, msg.halloweenForced);
	        });
	        ctx.get().setPacketHandled(true);
	    }
	}
	
	public static class PlaySoundPacket {
	    private final String soundName;
	
	    public PlaySoundPacket(String sound) {
	        this.soundName = sound;
	    }
	
	    public PlaySoundPacket(FriendlyByteBuf buf) {
	        this.soundName = buf.readUtf();
	    }
	
	    public static void encode(PlaySoundPacket msg, FriendlyByteBuf buf) {
	        buf.writeUtf(msg.soundName);
	    }
	
	    public static PlaySoundPacket decode(FriendlyByteBuf buf) {
	        return new PlaySoundPacket(buf);
	    }
	
	    public static void handle(PlaySoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
	        ctx.get().enqueueWork(() -> {
	            ClientHandler.playSound(msg.soundName);
	        });
	        ctx.get().setPacketHandled(true);
	    }
	}
	
	@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.FORGE)
	public static class ServerEvents {
	    @SubscribeEvent
	    public static void onServerTick(TickEvent.ServerTickEvent event) {
	        if (event.phase != TickEvent.Phase.END) return;
	        if (event.getServer() == null) return;
	
	        if (serverTickCounter % 100 == 0) {
	            checkMapMarkerFile(event.getServer());
	        }
	
	        // CORRECTION : On vérifie si un changement est nécessaire avant d'appliquer
	        // Cela évite de spammer le système de config
	        if (serverHalloweenForced && !HalloweenConfig.isHalloweenForced()) {
	            HalloweenManager.setForceHalloweenMode(true);
	        } else if (!serverHalloweenForced && HalloweenConfig.isHalloweenForced()) {
	            HalloweenManager.setForceHalloweenMode(false);
	        }
	
	        if (serverSpookyActive) {
	            serverTickCounter++;
	            if (serverTickCounter % INTERVAL_SPOOKY == 0) {
	                if (Math.random() < CHANCE_SPOOKY) {
	                    broadcastSound("amb_spooky", event.getServer());
	                }
	            }
	            if (serverTickCounter % INTERVAL_SPOOKY_2D == 0) {
	                if (Math.random() < CHANCE_SPOOKY_2D) {
	                    broadcastSound("amb_spooky_2d", event.getServer());
	                }
	            }
	            if (serverTickCounter >= INTERVAL_SPOOKY_2D) {
	                serverTickCounter = 0;
	            }
	        }
	    }
	
	    @SubscribeEvent
	    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
	        if (event.getEntity() instanceof ServerPlayer player) {
	            me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendTo(
	                new MapConfigPacket(serverSpookyActive, serverHalloweenForced),
	                player.connection.connection,
	                NetworkDirection.PLAY_TO_CLIENT
	            );
	        }
	    }
	
	    private static void checkMapMarkerFile(net.minecraft.server.MinecraftServer server) {
	        try {
	            Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
	            File markerFile = new File(worldPath.toFile(), MARKER_FILE);
	            String currentWorld = worldPath.toString();
	
	            if (!currentWorld.equals(lastCheckedWorld)) {
	                lastCheckedWorld = currentWorld;
	                serverSpookyActive = false;
	                serverHalloweenForced = false;
	                serverTickCounter = 0;
	            }
	
	            if (!markerFile.exists()) {
	                if (serverSpookyActive || serverHalloweenForced) {
	                    serverSpookyActive = false;
	                    serverHalloweenForced = false;
	                    broadcastConfigToAllPlayers(server);
	                }
	                return;
	            }
	
	            boolean foundSpooky = false;
	            boolean foundHalloween = false;
	
	            try (BufferedReader reader = new BufferedReader(new FileReader(markerFile))) {
	                String line;
	                while ((line = reader.readLine()) != null) {
	                    String trimmedLine = line.trim().toUpperCase();
	                    if (trimmedLine.contains(KEYWORD_SPOOKY)) foundSpooky = true;
	                    if (trimmedLine.contains(KEYWORD_HALLOWEEN)) foundHalloween = true;
	                }
	            }
	
	            if (foundSpooky != serverSpookyActive || foundHalloween != serverHalloweenForced) {
	                serverSpookyActive = foundSpooky;
	                serverHalloweenForced = foundHalloween;
	                broadcastConfigToAllPlayers(server);
	            }
	
	        } catch (Exception e) {
	            // Erreur silencieuse
	        }
	    }
	
	    private static void broadcastSound(String soundName, net.minecraft.server.MinecraftServer server) {
	        PlaySoundPacket packet = new PlaySoundPacket(soundName);
	        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
	            me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendTo(
	                packet, 
	                player.connection.connection, 
	                NetworkDirection.PLAY_TO_CLIENT
	            );
	        }
	    }
	
	    private static void broadcastConfigToAllPlayers(net.minecraft.server.MinecraftServer server) {
	        MapConfigPacket packet = new MapConfigPacket(serverSpookyActive, serverHalloweenForced);
	        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
	            me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendTo(
	                packet, 
	                player.connection.connection, 
	                NetworkDirection.PLAY_TO_CLIENT
	            );
	        }
	    }
	}
	
	@OnlyIn(Dist.CLIENT)
	public static class ClientHandler {
	    private static boolean clientSpookyActive = false;
	    private static boolean clientHalloweenForced = false;
	
	    public static void handleMapConfig(boolean spooky, boolean halloween) {
	        clientSpookyActive = spooky;
	        clientHalloweenForced = halloween;
	    }
	
	    public static void playSound(String soundName) {
	        Minecraft mc = Minecraft.getInstance();
	        if (mc.player == null) return;
	
	        try {
	            ResourceLocation soundLocation = new ResourceLocation("zombierool", soundName);
	            SoundEvent soundEvent = ForgeRegistries.SOUND_EVENTS.getValue(soundLocation);
	            if (soundEvent != null) {
	                SimpleSoundInstance soundInstance = new SimpleSoundInstance(
	                    soundEvent,
	                    SoundSource.AMBIENT,
	                    1.0F,
	                    1.0F,
	                    mc.player.getRandom(),
	                    mc.player.getX(),
	                    mc.player.getY(),
	                    mc.player.getZ()
	                );
	                mc.getSoundManager().play(soundInstance);
	            }
	        } catch (Exception e) {
	            // Ignoré
	        }
	    }
	
	    public static boolean isSpookyActive() {
	        return clientSpookyActive;
	    }
	
	    public static boolean isHalloweenForced() {
	        return clientHalloweenForced;
	    }
	}
	
	public static String getStatus() {
	    StringBuilder status = new StringBuilder();
	    status.append("=== Map Event Manager Status ===\n");
	    status.append("Server Spooky: ").append(serverSpookyActive ? "ACTIVE" : "INACTIVE").append("\n");
	    status.append("Server Halloween: ").append(serverHalloweenForced ? "ACTIVE" : "INACTIVE").append("\n");
	    return status.toString();
	}
}