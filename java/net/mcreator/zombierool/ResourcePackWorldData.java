package net.mcreator.zombierool.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class ResourcePackWorldData {
    private static final String FILE_NAME = "zombierool_resourcepack.json";
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
            File rpFile = new File(worldDir, FILE_NAME);
            
            System.out.println("[ZombieRool] Looking for RP metadata at: " + rpFile.getAbsolutePath());
            
            if (rpFile.exists()) {
                try (FileReader reader = new FileReader(rpFile)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    
                    if (json.has("url")) {
                        this.resourcePackUrl = json.get("url").getAsString();
                    }
                    if (json.has("name")) {
                        this.resourcePackName = json.get("name").getAsString();
                    }
                    
                    this.loaded = true;
                    System.out.println("[ZombieRool] Loaded RP metadata: " + resourcePackName + " -> " + resourcePackUrl);
                }
            } else {
                System.out.println("[ZombieRool] No RP metadata found");
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error loading RP metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void save(MinecraftServer server, String url, String name) {
        try {
            File worldDir = server.getWorldPath(LevelResource.ROOT).toFile();
            File rpFile = new File(worldDir, FILE_NAME);
            
            JsonObject json = new JsonObject();
            json.addProperty("url", url);
            json.addProperty("name", name);
            
            try (FileWriter writer = new FileWriter(rpFile)) {
                GSON.toJson(json, writer);
            }
            
            System.out.println("[ZombieRool] Saved RP metadata to world: " + name);
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error saving RP metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public boolean hasResourcePack() {
        return loaded && resourcePackUrl != null && resourcePackName != null;
    }

    public String getResourcePackUrl() {
        return resourcePackUrl;
    }

    public String getResourcePackName() {
        return resourcePackName;
    }
}