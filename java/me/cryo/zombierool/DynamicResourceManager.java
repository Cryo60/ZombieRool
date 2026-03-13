package me.cryo.zombierool.core.manager;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DynamicResourceManager {

    // --- SERVER SIDE: SKINS ---
    private static final Map<String, Map<String, byte[]>> SERVER_SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();

    // --- SERVER SIDE: AUDIO PACK & WEB SERVER ---
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static HttpServer webServer;
    private static int currentPort = 8080;
    private static String packHash = "";

    // --- CLIENT SIDE: SKINS ---
    private static final Map<String, Map<String, ResourceLocation>> CLIENT_SKIN_CACHE = new ConcurrentHashMap<>();

    // ==========================================
    // INITIALIZATION (SERVER)
    // ==========================================
    public static void loadWorldResources(ServerLevel level) {
        File worldDir = level.getServer().getWorldPath(LevelResource.ROOT).toFile();
        
        // 1. Load Skins
        File skinsDir = new File(worldDir, "zombierool/skins");
        loadSkins(skinsDir);

        // 2. Generate and Host Audio Pack
        File audioDir = new File(worldDir, "zombierool/audio");
        File packZip = new File(worldDir, "zombierool/zombierool_generated_pack.zip");
        new Thread(() -> generateAndHostAudioPack(audioDir, packZip)).start();
    }

    // ==========================================
    // SERVER: SKINS LOGIC
    // ==========================================
    private static void loadSkins(File skinsDir) {
        SERVER_SKIN_CACHE.clear();
        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
            new File(skinsDir, "zombie").mkdirs();
            new File(skinsDir, "crawler").mkdirs();
            new File(skinsDir, "hellhound").mkdirs();
            return;
        }

        loadMobSkins(new File(skinsDir, "zombie"), "zombie");
        loadMobSkins(new File(skinsDir, "crawler"), "crawler");
        loadMobSkins(new File(skinsDir, "hellhound"), "hellhound");
    }

    private static void loadMobSkins(File dir, String mobType) {
        if (!dir.exists() || !dir.isDirectory()) return;
        Map<String, byte[]> mobSkins = new HashMap<>();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                try {
                    byte[] data = Files.readAllBytes(file.toPath());
                    String skinId = file.getName().replace(".png", "");
                    mobSkins.put(skinId, data);
                    System.out.println("[ZombieRool] Loaded dynamic skin: " + mobType + "/" + skinId);
                } catch (Exception e) {
                    System.err.println("[ZombieRool] Failed to load skin " + file.getName() + ": " + e.getMessage());
                }
            }
        }
        if (!mobSkins.isEmpty()) {
            SERVER_SKIN_CACHE.put(mobType, mobSkins);
        }
    }

    public static String getRandomSkin(String mobType) {
        Map<String, byte[]> mobSkins = SERVER_SKIN_CACHE.get(mobType);
        if (mobSkins == null || mobSkins.isEmpty()) return "";
        List<String> keys = new ArrayList<>(mobSkins.keySet());
        return keys.get(RANDOM.nextInt(keys.size()));
    }

    public static Map<String, Map<String, byte[]>> getAllServerSkins() {
        return SERVER_SKIN_CACHE;
    }

    // ==========================================
    // SERVER: AUDIO PACK & WEB SERVER LOGIC
    // ==========================================
    private static void generateAndHostAudioPack(File audioDir, File packZip) {
        File musicDir = new File(audioDir, "music");
        File voicesDir = new File(audioDir, "voices");

        musicDir.mkdirs();
        voicesDir.mkdirs();

        File[] musicFiles = musicDir.listFiles((d, n) -> n.endsWith(".ogg"));
        File[] voiceFiles = voicesDir.listFiles((d, n) -> n.endsWith(".ogg"));

        if ((musicFiles == null || musicFiles.length == 0) && (voiceFiles == null || voiceFiles.length == 0)) {
            System.out.println("[ZombieRool] No custom audio found. Skipping auto-pack generation.");
            return;
        }

        System.out.println("[ZombieRool] Custom audio found. Generating resource pack...");

        try {
            if (packZip.exists()) {
                packZip.delete();
            }

            FileOutputStream fos = new FileOutputStream(packZip);
            ZipOutputStream zos = new ZipOutputStream(fos);

            JsonObject packMeta = new JsonObject();
            JsonObject packObj = new JsonObject();
            packObj.addProperty("pack_format", 15);
            packObj.addProperty("description", "ZombieRool Auto-Generated Audio Pack");
            packMeta.add("pack", packObj);
            
            zos.putNextEntry(new ZipEntry("pack.mcmeta"));
            zos.write(GSON.toJson(packMeta).getBytes());
            zos.closeEntry();

            JsonObject soundsJson = new JsonObject();

            if (musicFiles != null) {
                for (File f : musicFiles) {
                    String name = f.getName().replace(".ogg", "");
                    JsonObject soundEvent = new JsonObject();
                    soundEvent.addProperty("category", "music");
                    JsonArray soundsArray = new JsonArray();
                    soundsArray.add("zombierool:music/" + name);
                    soundEvent.add("sounds", soundsArray);
                    soundsJson.add(name, soundEvent);

                    zos.putNextEntry(new ZipEntry("assets/zombierool/sounds/music/" + f.getName()));
                    Files.copy(f.toPath(), zos);
                    zos.closeEntry();
                }
            }

            if (voiceFiles != null) {
                for (File f : voiceFiles) {
                    String name = f.getName().replace(".ogg", "");
                    JsonObject soundEvent = new JsonObject();
                    soundEvent.addProperty("category", "player");
                    JsonArray soundsArray = new JsonArray();
                    soundsArray.add("zombierool:voices/" + name);
                    soundEvent.add("sounds", soundsArray);
                    
                    // On enregistre avec le nom custom_voice_PRESET_TYPE
                    soundsJson.add(name, soundEvent);

                    zos.putNextEntry(new ZipEntry("assets/zombierool/sounds/voices/" + f.getName()));
                    Files.copy(f.toPath(), zos);
                    zos.closeEntry();
                }
            }

            zos.putNextEntry(new ZipEntry("assets/zombierool/sounds.json"));
            zos.write(GSON.toJson(soundsJson).getBytes());
            zos.closeEntry();

            zos.close();
            fos.close();

            System.out.println("[ZombieRool] Auto-pack generated at: " + packZip.getAbsolutePath());
            startWebServer(packZip);

        } catch (Exception e) {
            System.err.println("[ZombieRool] Failed to generate audio pack.");
            e.printStackTrace();
        }
    }

    private static void startWebServer(File packZip) {
        stopWebServer();
        try {
            packHash = calculateSHA1(packZip);
            
            boolean started = false;
            while (!started && currentPort < 8100) {
                try {
                    webServer = HttpServer.create(new InetSocketAddress(currentPort), 0);
                    started = true;
                } catch (Exception e) {
                    currentPort++;
                }
            }

            if (!started) {
                System.err.println("[ZombieRool] Failed to start web server: No open ports found.");
                return;
            }

            webServer.createContext("/pack.zip", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    exchange.getResponseHeaders().set("Content-Type", "application/zip");
                    exchange.sendResponseHeaders(200, packZip.length());
                    try (OutputStream os = exchange.getResponseBody();
                         FileInputStream fs = new FileInputStream(packZip)) {
                        final byte[] buffer = new byte[0x10000];
                        int count;
                        while ((count = fs.read(buffer)) >= 0) {
                            os.write(buffer, 0, count);
                        }
                    }
                }
            });

            webServer.setExecutor(null);
            webServer.start();
            System.out.println("[ZombieRool] Web server started on port " + currentPort);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void stopWebServer() {
        if (webServer != null) {
            webServer.stop(0);
            webServer = null;
            System.out.println("[ZombieRool] Web server stopped.");
        }
    }

    public static int getWebServerPort() {
        return currentPort;
    }

    public static String getPackHash() {
        return packHash;
    }

    public static boolean isWebServerRunning() {
        return webServer != null;
    }

    private static String calculateSHA1(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int n;
            while ((n = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, n);
            }
        }
        byte[] hash = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==========================================
    // CLIENT: SKINS LOGIC
    // ==========================================
    @OnlyIn(Dist.CLIENT)
    public static void registerClientSkin(String mobType, String skinId, byte[] data) {
        try {
            NativeImage image = NativeImage.read(new ByteArrayInputStream(data));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = new ResourceLocation("zombierool", "dynamic_skin_" + mobType + "_" + skinId);
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().getTextureManager().register(location, texture);
                CLIENT_SKIN_CACHE.computeIfAbsent(mobType, k -> new HashMap<>()).put(skinId, location);
            });
        } catch (Exception e) {
            System.err.println("[ZombieRool] Failed to register dynamic skin: " + mobType + "/" + skinId);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static ResourceLocation getClientSkin(String mobType, String skinId) {
        Map<String, ResourceLocation> mobSkins = CLIENT_SKIN_CACHE.get(mobType);
        if (mobSkins != null) {
            return mobSkins.get(skinId);
        }
        return null;
    }

    @OnlyIn(Dist.CLIENT)
    public static void clearClientSkins() {
        CLIENT_SKIN_CACHE.clear();
    }
}