package me.cryo.zombierool.network.packet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import me.cryo.zombierool.WorldConfig;
import me.cryo.zombierool.network.NetworkHandler;
import java.util.function.Supplier;
public class UpdateConfigPacket {
    public final CompoundTag data;
    public UpdateConfigPacket(CompoundTag data) {
        this.data = data;
    }
    public static void encode(UpdateConfigPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.data);
    }
    public static UpdateConfigPacket decode(FriendlyByteBuf buf) {
        return new UpdateConfigPacket(buf.readNbt());
    }
    public static void handle(UpdateConfigPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if(player == null || !player.hasPermissions(2)) return;
            ServerLevel level = player.serverLevel();
            WorldConfig config = WorldConfig.get(level);
            String action = msg.data.getString("action");
            if (action.equals("save_all")) {
                config.loadEditable(msg.data);
                
                java.io.File worldDir = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).toFile();
                String mapId = config.getMapId();
                if (mapId == null || mapId.trim().isEmpty() || mapId.equals("zr_")) {
                    String folderName = worldDir.getName();
                    String safeName = folderName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "");
                    mapId = "zr_" + safeName;
                    config.setMapId(mapId);
                } else if (!mapId.startsWith("zr_")) {
                    mapId = "zr_" + mapId;
                    config.setMapId(mapId);
                }

                config.setDirty();
                
                try {
                    java.io.File mapJson = new java.io.File(worldDir, "zombierool_map.json");
                    com.google.gson.JsonObject json = new com.google.gson.JsonObject();
                    if (mapJson.exists()) {
                        try (java.io.FileReader reader = new java.io.FileReader(mapJson)) {
                            com.google.gson.JsonElement parsed = com.google.gson.JsonParser.parseReader(reader);
                            if (parsed != null && parsed.isJsonObject()) {
                                json = parsed.getAsJsonObject();
                            }
                        } catch (Exception ignored){}
                    }
                    
                    json.addProperty("id", config.getMapId());
                    
                    com.google.gson.JsonObject rpJson = new com.google.gson.JsonObject();
                    rpJson.addProperty("url", config.getResourcePackUrl() != null ? config.getResourcePackUrl() : "");
                    rpJson.addProperty("name", config.getResourcePackName() != null ? config.getResourcePackName() : "");
                    json.add("resource_pack", rpJson);
                    
                    com.google.gson.JsonObject overridesJson = new com.google.gson.JsonObject();
                    overridesJson.addProperty("spooky_ambience", config.isSpookyAmbience());
                    overridesJson.addProperty("force_halloween", config.isForceHalloween());
                    json.add("overrides", overridesJson);
                    
                    json.remove("resource_pack_url");
                    json.remove("resource_pack_name");
                    json.remove("spooky_ambience");
                    json.remove("force_halloween");

                    try (java.io.FileWriter writer = new java.io.FileWriter(mapJson)) {
                        new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SetFogPresetPacket(
                    config.getFogPreset(), config.getCustomFogR(), config.getCustomFogG(), config.getCustomFogB(), config.getCustomFogNear(), config.getCustomFogFar()
                ));
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SetEyeColorPacket(config.getEyeColorPreset()));
                if (config.getDayNightMode().equals("day")) level.setDayTime(6000);
                else if (config.getDayNightMode().equals("night")) level.setDayTime(18000);
                if (config.areParticlesEnabled() && config.getParticleTypeId() != null) {
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWeatherPacket(true, config.getParticleTypeId().toString(), config.getParticleDensity(), config.getParticleMode()));
                } else {
                    NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWeatherPacket(false, "", "", ""));
                }
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aConfiguration du monde enregistrée avec succès !"));
            } else if (action.equals("reset_general")) {
                config.resetGeneral();
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SetEyeColorPacket(config.getEyeColorPreset()));
                level.setDayTime(18000);
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SyncWeatherPacket(false, "", "", ""));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aParamètres généraux réinitialisés."));
            } else if (action.equals("reset_mobs")) {
                config.resetMobs();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aParamètres des monstres réinitialisés."));
            } else if (action.equals("reset_waves")) {
                config.resetWaves();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aParamètres des vagues réinitialisés."));
            } else if (action.equals("reset_loot")) {
                config.resetLoot();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aParamètres des loots et exclusions réinitialisés."));
            } else if (action.equals("reset_fog")) {
                config.resetFog();
                NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new SetFogPresetPacket("normal", 0,0,0, 0.5f, 18.0f));
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aParamètres de brouillard réinitialisés."));
            } else if (action.equals("wave_cmd")) {
                String cmd = msg.data.getString("cmd");
                if (cmd.equals("start")) {
                    player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "zombierool start");
                } else if (cmd.equals("end")) {
                    player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "zombierool end");
                } else if (cmd.startsWith("setWave ")) {
                    player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), "zombierool " + cmd);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}