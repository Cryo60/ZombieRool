package me.cryo.zombierool;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Path;
import java.util.function.Supplier;

public class MapEventManager {
    private static final int INTERVAL_SPOOKY = 60; 
    private static final int INTERVAL_SPOOKY_2D = 240; 

    private static final float CHANCE_SPOOKY = 0.4F; 
    private static final float CHANCE_SPOOKY_2D = 0.55F; 

    private static boolean serverSpookyActive = false;
    private static boolean serverHalloweenForced = false;
    private static int serverTickCounter = 0;
    private static String lastCheckedWorld = "";

    public static class S2CMapConfigPacket {
        private final boolean spookyActive;
        private final boolean halloweenForced;
        private final boolean allowDownMovement;

        public S2CMapConfigPacket(boolean spooky, boolean halloween, boolean allowDownMovement) {
            this.spookyActive = spooky;
            this.halloweenForced = halloween;
            this.allowDownMovement = allowDownMovement;
        }

        public S2CMapConfigPacket(FriendlyByteBuf buf) {
            this.spookyActive = buf.readBoolean();
            this.halloweenForced = buf.readBoolean();
            this.allowDownMovement = buf.readBoolean();
        }

        public static void encode(S2CMapConfigPacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.spookyActive);
            buf.writeBoolean(msg.halloweenForced);
            buf.writeBoolean(msg.allowDownMovement);
        }

        public static S2CMapConfigPacket decode(FriendlyByteBuf buf) {
            return new S2CMapConfigPacket(buf);
        }

        public static void handle(S2CMapConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ClientHandler.handleMapConfig(msg.spookyActive, msg.halloweenForced, msg.allowDownMovement);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class S2CPlaySoundPacket {
        private final String soundName;

        public S2CPlaySoundPacket(String sound) {
            this.soundName = sound;
        }

        public S2CPlaySoundPacket(FriendlyByteBuf buf) {
            this.soundName = buf.readUtf();
        }

        public static void encode(S2CPlaySoundPacket msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.soundName);
        }

        public static S2CPlaySoundPacket decode(FriendlyByteBuf buf) {
            return new S2CPlaySoundPacket(buf);
        }

        public static void handle(S2CPlaySoundPacket msg, Supplier<NetworkEvent.Context> ctx) {
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

            if (serverHalloweenForced && !(me.cryo.zombierool.configuration.ZRClientConfig.getHalloweenMode() == me.cryo.zombierool.configuration.ZRClientConfig.HalloweenMode.FORCE_ON)) {
                HalloweenManager.setForceHalloweenMode(true);
            } else if (!serverHalloweenForced && (me.cryo.zombierool.configuration.ZRClientConfig.getHalloweenMode() == me.cryo.zombierool.configuration.ZRClientConfig.HalloweenMode.FORCE_ON)) {
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
                boolean allowDownMovement = false;
                if (player.serverLevel() != null) {
                    allowDownMovement = WorldConfig.get(player.serverLevel()).isAllowDownMovement();
                }
                me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendTo(
                    new S2CMapConfigPacket(serverSpookyActive, serverHalloweenForced, allowDownMovement),
                    player.connection.connection,
                    NetworkDirection.PLAY_TO_CLIENT
                );
            }
        }

        private static void checkMapMarkerFile(net.minecraft.server.MinecraftServer server) {
            try {
                Path worldPath = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
                File newMetaFile = new File(worldPath.toFile(), "zombierool_map.json");
                File legacyCfgFile = new File(worldPath.toFile(), "zombierool_map.cfg");

                String currentWorld = worldPath.toString();
                if (!currentWorld.equals(lastCheckedWorld)) {
                    lastCheckedWorld = currentWorld;
                    serverSpookyActive = false;
                    serverHalloweenForced = false;
                    serverTickCounter = 0;
                }

                boolean foundSpooky = false;
                boolean foundHalloween = false;

                if (newMetaFile.exists()) {
                    try (FileReader reader = new FileReader(newMetaFile)) {
                        com.google.gson.JsonObject json = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                        if (json.has("overrides")) {
                            com.google.gson.JsonObject overrides = json.getAsJsonObject("overrides");
                            if (overrides.has("spooky_ambience")) foundSpooky = overrides.get("spooky_ambience").getAsBoolean();
                            if (overrides.has("force_halloween")) foundHalloween = overrides.get("force_halloween").getAsBoolean();
                        } else {
                            if (json.has("spooky_ambience") && json.get("spooky_ambience").getAsBoolean()) foundSpooky = true;
                            if (json.has("force_halloween") && json.get("force_halloween").getAsBoolean()) foundHalloween = true;
                        }
                    }
                } else if (legacyCfgFile.exists()) {
                    try (BufferedReader reader = new BufferedReader(new FileReader(legacyCfgFile))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String trimmedLine = line.trim().toUpperCase();
                            if (trimmedLine.contains("SPOOKY_AMBIENCE")) foundSpooky = true;
                            if (trimmedLine.contains("FORCE_HALLOWEEN")) foundHalloween = true;
                        }
                    }
                }

                if (foundSpooky != serverSpookyActive || foundHalloween != serverHalloweenForced) {
                    serverSpookyActive = foundSpooky;
                    serverHalloweenForced = foundHalloween;
                    broadcastConfigToAllPlayers(server);
                }

            } catch (Exception e) {}
        }

        private static void broadcastSound(String soundName, net.minecraft.server.MinecraftServer server) {
            S2CPlaySoundPacket packet = new S2CPlaySoundPacket(soundName);
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                me.cryo.zombierool.network.NetworkHandler.INSTANCE.sendTo(
                    packet, 
                    player.connection.connection, 
                    NetworkDirection.PLAY_TO_CLIENT
                );
            }
        }

        private static void broadcastConfigToAllPlayers(net.minecraft.server.MinecraftServer server) {
            boolean allowDownMovement = false;
            if (server.overworld() != null) {
                allowDownMovement = WorldConfig.get(server.overworld()).isAllowDownMovement();
            }
            S2CMapConfigPacket packet = new S2CMapConfigPacket(serverSpookyActive, serverHalloweenForced, allowDownMovement);
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
        public static boolean allowDownMovement = false;

        public static void handleMapConfig(boolean spooky, boolean halloween, boolean allowDownMvmt) {
            clientSpookyActive = spooky;
            clientHalloweenForced = halloween;
            allowDownMovement = allowDownMvmt;
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
            } catch (Exception e) {}
        }
    }
}