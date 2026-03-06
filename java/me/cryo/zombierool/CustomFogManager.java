package me.cryo.zombierool.configuration;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

public class CustomFogManager {
    private static final File CONFIG_FILE = new File(Minecraft.getInstance().gameDirectory, "config/zombierool_custom_fogs.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, FogData> customPresets = new HashMap<>();
    private static boolean loaded = false;

    public static class FogData {
        public float r, g, b, near, far;
        public FogData(float r, float g, float b, float near, float far) {
            this.r = r; this.g = g; this.b = b; this.near = near; this.far = far;
        }
    }

    public static void load() {
        if (loaded) return;
        try {
            if (CONFIG_FILE.exists()) {
                try (FileReader reader = new FileReader(CONFIG_FILE)) {
                    Map<String, FogData> loadedMap = GSON.fromJson(reader, new TypeToken<Map<String, FogData>>(){}.getType());
                    if (loadedMap != null) customPresets = loadedMap;
                }
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error loading custom fogs: " + e.getMessage());
        }
        loaded = true;
    }

    public static void savePreset(String name, float r, float g, float b, float near, float far) {
        customPresets.put(name, new FogData(r, g, b, near, far));
        saveToFile();
    }

    public static void deletePreset(String name) {
        if (customPresets.remove(name) != null) {
            saveToFile();
        }
    }

    private static void saveToFile() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(customPresets, writer);
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error saving custom fogs: " + e.getMessage());
        }
    }

    public static Map<String, FogData> getCustomPresets() {
        load();
        return customPresets;
    }
}