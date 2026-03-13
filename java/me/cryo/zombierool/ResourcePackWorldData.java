package me.cryo.zombierool.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;

public class ResourcePackWorldData {
    private static final Gson GSON = new Gson();
    private String resourcePackUrl;
    private String resourcePackName;
    private boolean loaded = false;

    private ResourcePackWorldData() {}

    public static ResourcePackWorldData get(MinecraftServer server) {
        if (server == null) return null;
        ResourcePackWorldData data = new ResourcePackWorldData();
        data.load(server);
        return data;
    }

    private void load(MinecraftServer server) {
        try {
            File worldDir = server.getWorldPath(LevelResource.ROOT).toFile();
            File newMetaFile = new File(worldDir, "zombierool_map.json");
            File oldMetaFile = new File(worldDir, "zombierool_resourcepack.json");

            if (newMetaFile.exists()) {
                try (FileReader reader = new FileReader(newMetaFile)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("resource_pack")) {
                        JsonObject rp = json.getAsJsonObject("resource_pack");
                        if (rp.has("url")) this.resourcePackUrl = rp.get("url").getAsString();
                        if (rp.has("name")) this.resourcePackName = rp.get("name").getAsString();
                        this.loaded = true;
                    } else if (json.has("resource_pack_url")) {
                        this.resourcePackUrl = json.get("resource_pack_url").getAsString();
                        if (json.has("resource_pack_name")) {
                            this.resourcePackName = json.get("resource_pack_name").getAsString();
                        }
                        this.loaded = true;
                    }
                }
            } else if (oldMetaFile.exists()) {
                try (FileReader reader = new FileReader(oldMetaFile)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json.has("url")) {
                        this.resourcePackUrl = json.get("url").getAsString();
                    }
                    if (json.has("name")) {
                        this.resourcePackName = json.get("name").getAsString();
                    }
                    this.loaded = true;
                }
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error loading RP metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean hasResourcePack() {
        return loaded && 
               resourcePackUrl != null && !resourcePackUrl.trim().isEmpty() && 
               resourcePackName != null && !resourcePackName.trim().isEmpty();
    }

    public String getResourcePackUrl() {
        return resourcePackUrl;
    }

    public String getResourcePackName() {
        return resourcePackName;
    }
}