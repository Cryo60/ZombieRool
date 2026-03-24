package me.cryo.zombierool.core.manager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.loading.FMLLoader;
import me.cryo.zombierool.network.NetworkHandler;
import me.cryo.zombierool.network.packet.S2CSyncDynamicSoundPacket;
import me.cryo.zombierool.network.packet.S2CSyncDynamicChalkPacket;
import net.minecraftforge.network.PacketDistributor;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicResourceManager {
    private static final Map<String, Map<String, byte[]>> SERVER_SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, byte[]> SERVER_CHALK_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, AudioEntry> SERVER_AUDIO_CACHE = new ConcurrentHashMap<>();
    private static final Random RANDOM = new Random();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<String, Map<String, ResourceLocation>> CLIENT_SKIN_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, ResourceLocation> CLIENT_CHALK_CACHE = new ConcurrentHashMap<>();
    public static final Map<String, List<String>> CUSTOM_VOICE_MAP = new ConcurrentHashMap<>();

    public static boolean overrideSfx = false;
    public static boolean overrideMusic = false;
    public static boolean overrideVoices = false;
    public static boolean overrideSkins = false;

    private static final long MAX_AUDIO_SIZE = 15 * 1024 * 1024; 
    private static final long MAX_SKIN_SIZE = 2 * 1024 * 1024; 

    private static final String[] VALID_VOICE_EVENTS = {
        "level_start", "special_start", "inform_killfirm", "kill_headshot",
        "kill_hellhound", "kill_crawler", "respawn", "was_revived", "is_reviving",
        "has_revived", "general_hit", "zombie_hit", "crawler_hit", "hellhound_hit",
        "box_move", "weapon_upgraded", "took_perk", "inform_reloading", "empty_clip",
        "no_money", "power_on", "random_chatter", "voice_melee_attack"
    };

    public static void loadWorldResources(ServerLevel level) {
        overrideSfx = false;
        overrideMusic = false;
        overrideVoices = false;
        overrideSkins = false;
        CUSTOM_VOICE_MAP.clear();
        SERVER_SKIN_CACHE.clear();
        SERVER_CHALK_CACHE.clear();
        SERVER_AUDIO_CACHE.clear();

        if (FMLLoader.getDist().isClient()) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> DynamicResourceManager::clearClientResources);
        }

        File worldDir = level.getServer().getWorldPath(LevelResource.ROOT).toFile();
        File zrDir = new File(worldDir, "zombierool");
        zrDir.mkdirs();

        createDefaultFilesIfMissing(zrDir);
        loadConfiguration(zrDir);

        File skinsDir = new File(zrDir, "skins");
        loadSkins(skinsDir);

        File chalksDir = new File(zrDir, "assets/chalks");
        loadChalks(chalksDir);

        File audioDir = new File(zrDir, "audio");
        loadAudioFiles(audioDir); 
    }

    private static void loadAudioFiles(File audioDir) {
        File musicDir = new File(audioDir, "music");
        File voicesDir = new File(audioDir, "voices");
        File sfxDir = new File(audioDir, "sfx");

        boolean hasMusic = hasAnyOggFiles(musicDir);
        boolean hasVoices = hasAnyOggFiles(voicesDir);
        boolean hasSfx = hasAnyOggFiles(sfxDir);

        if (!hasMusic && !hasVoices && !hasSfx) {
            System.out.println("[ZombieRool] Aucun fichier audio personnalisé. Chargement audio ignoré.");
            return;
        }

        System.out.println("[ZombieRool] Chargement des fichiers audio en mémoire...");

        if (hasMusic && overrideMusic) loadMusicFiles(musicDir);
        
        CUSTOM_VOICE_MAP.clear();
        if (hasVoices && overrideVoices) loadVoiceFiles(voicesDir);
        if (hasSfx && overrideSfx) loadSfxFiles(sfxDir);

        System.out.println("[ZombieRool] " + SERVER_AUDIO_CACHE.size() + " son(s) chargé(s) en mémoire.");
    }

    private static void loadMusicFiles(File musicDir) {
        Map<String, String> musicFolders = Map.of(
            "default", "zombie_soundtrack",
            "damned", "zombie_soundtrack_damned",
            "secret", "secret_song",
            "menu", "menu_music"
        );

        for (Map.Entry<String, String> entry : musicFolders.entrySet()) {
            File folder = new File(musicDir, entry.getKey());
            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".ogg"));
                if (files != null) {
                    for (File f : files) {
                        String safeName = getSafeName(f.getName()).replace(".ogg", "");
                        storeAudioFile(f, "zombierool:music/" + entry.getKey() + "/" + safeName, "music");
                    }
                }
            }
        }

        File[] looseMusic = musicDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".ogg"));
        if (looseMusic != null) {
            for (File f : looseMusic) {
                String safeName = getSafeName(f.getName()).replace(".ogg", "");
                storeAudioFile(f, "zombierool:music/" + safeName, "music");
            }
        }
    }

    private static void loadVoiceFiles(File voicesDir) {
        File[] rootFiles = voicesDir.listFiles((d, n) -> new File(d, n).isFile()
            && n.toLowerCase(Locale.ROOT).endsWith(".ogg"));
        if (rootFiles != null) {
            for (File f : rootFiles) {
                String safeName = getSafeName(f.getName()).replace(".ogg", "");
                String baseEvent = extractBaseVoiceEvent(safeName);
                String eventName = baseEvent != null ? "player_" + baseEvent : safeName;
                String soundKey = "zombierool:voices/" + safeName;

                CUSTOM_VOICE_MAP.computeIfAbsent(eventName, k -> new ArrayList<>()).add(soundKey);
                storeAudioFile(f, soundKey, "voice");
            }
        }

        File[] subDirs = voicesDir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String presetName = getSafeName(subDir.getName());
                File[] subFiles = subDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".ogg"));
                
                if (subFiles != null) {
                    for (File f : subFiles) {
                        String safeName = getSafeName(f.getName()).replace(".ogg", "");
                        String baseEvent = extractBaseVoiceEvent(safeName);
                        
                        String eventName;
                        if (baseEvent != null) {
                            eventName = presetName.startsWith("player_")
                                ? presetName + "_" + baseEvent
                                : "player_" + presetName + "_" + baseEvent;
                        } else {
                            eventName = presetName.startsWith("player_")
                                ? presetName + "_" + safeName
                                : "player_" + presetName + "_" + safeName;
                        }

                        String soundKey = "zombierool:voices/" + presetName + "/" + safeName;
                        CUSTOM_VOICE_MAP.computeIfAbsent(eventName, k -> new ArrayList<>()).add(soundKey);
                        storeAudioFile(f, soundKey, "voice");
                    }
                }
            }
        }
    }

    private static void loadSfxFiles(File sfxDir) {
        Map<String, String> sfxMapping = Map.of(
            "ambient", "ambient_loop",
            "start", "start_zombie",
            "round_change", "next_wave_zombie",
            "special_change", "special_change",
            "special_start", "fetch_me_their_souls",
            "misc", ""
        );

        for (Map.Entry<String, String> entry : sfxMapping.entrySet()) {
            File subDir = new File(sfxDir, entry.getKey());
            if (!subDir.exists() || !subDir.isDirectory()) continue;

            File[] files = subDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".ogg"));
            if (files == null) continue;

            String category = "ambient".equals(entry.getKey()) ? "ambient" : "sfx";
            for (File f : files) {
                String safeName = getSafeName(f.getName()).replace(".ogg", "");
                storeAudioFile(f, "zombierool:sfx/" + entry.getKey() + "/" + safeName, category);
            }
        }
    }

    private static void storeAudioFile(File file, String soundEventName, String category) {
        try {
            if (file.length() > MAX_AUDIO_SIZE) {
                System.err.println("[ZombieRool] Fichier audio trop volumineux (max 15MB) rejeté : " + file.getName());
                return;
            }
            byte[] data = Files.readAllBytes(file.toPath());
            SERVER_AUDIO_CACHE.put(soundEventName, new AudioEntry(category, data));
        } catch (Exception e) {
            System.err.println("[ZombieRool] Impossible de lire : " + file.getName());
        }
    }

    public static void sendAudioToPlayer(ServerPlayer player) {
        if (SERVER_AUDIO_CACHE.isEmpty() && SERVER_CHALK_CACHE.isEmpty()) return;

        System.out.println("[ZombieRool] Envoi des ressources dynamiques au joueur " + player.getGameProfile().getName());

        for (Map.Entry<String, AudioEntry> entry : SERVER_AUDIO_CACHE.entrySet()) {
            String soundEventName = entry.getKey();
            AudioEntry audio = entry.getValue();
            byte[] data = audio.data;

            int totalSize = data.length;
            int chunkSize = S2CSyncDynamicSoundPacket.CHUNK_SIZE;

            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                S2CSyncDynamicSoundPacket.begin(soundEventName, audio.category, totalSize)
            );

            int offset = 0;
            int chunkIndex = 0;
            while (offset < totalSize) {
                int length = Math.min(chunkSize, totalSize - offset);
                byte[] chunk = Arrays.copyOfRange(data, offset, offset + length);

                NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> player),
                    S2CSyncDynamicSoundPacket.data(soundEventName, chunkIndex, chunk)
                );

                offset += length;
                chunkIndex++;
            }

            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                S2CSyncDynamicSoundPacket.end(soundEventName)
            );
        }

        for (Map.Entry<String, byte[]> entry : SERVER_CHALK_CACHE.entrySet()) {
            NetworkHandler.INSTANCE.send(
                PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncDynamicChalkPacket(entry.getKey(), entry.getValue())
            );
        }
    }

    public static boolean hasAudioLoaded() {
        return !SERVER_AUDIO_CACHE.isEmpty();
    }

    private static void loadSkins(File skinsDir) {
        if (!skinsDir.exists()) {
            skinsDir.mkdirs();
            new File(skinsDir, "zombie").mkdirs();
            new File(skinsDir, "crawler").mkdirs();
            new File(skinsDir, "hellhound").mkdirs();
            new File(skinsDir, "zombie_eyes").mkdirs();
            new File(skinsDir, "crawler_eyes").mkdirs();
            new File(skinsDir, "hellhound_eyes").mkdirs();
            return;
        }

        loadMobSkins(new File(skinsDir, "zombie"), "zombie");
        loadMobSkins(new File(skinsDir, "crawler"), "crawler");
        loadMobSkins(new File(skinsDir, "hellhound"), "hellhound");
        loadMobSkins(new File(skinsDir, "zombie_eyes"), "zombie_eyes");
        loadMobSkins(new File(skinsDir, "crawler_eyes"), "crawler_eyes");
        loadMobSkins(new File(skinsDir, "hellhound_eyes"), "hellhound_eyes");
    }

    private static void loadMobSkins(File dir, String mobType) {
        if (!dir.exists() || !dir.isDirectory()) return;

        Map<String, byte[]> mobSkins = new HashMap<>();
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));

        if (files != null) {
            for (File file : files) {
                try {
                    if (file.length() > MAX_SKIN_SIZE) {
                        System.err.println("[ZombieRool] Fichier skin trop volumineux (max 2MB) rejeté : " + file.getName());
                        continue;
                    }
                    byte[] data = Files.readAllBytes(file.toPath());
                    String skinId = file.getName().replace(".png", "")
                        .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
                    
                    mobSkins.put(skinId, data);
                } catch (Exception e) {
                    System.err.println("[ZombieRool] Impossible de charger la skin "
                        + file.getName() + " : " + e.getMessage());
                }
            }
        }

        if (!mobSkins.isEmpty()) {
            SERVER_SKIN_CACHE.put(mobType, mobSkins);
        }
    }

    private static void loadChalks(File dir) {
        if (!dir.exists()) {
            dir.mkdirs();
            return;
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".png"));
        if (files != null) {
            for (File file : files) {
                try {
                    if (file.length() > MAX_SKIN_SIZE) {
                        System.err.println("[ZombieRool] Fichier chalk trop volumineux (max 2MB) rejeté : " + file.getName());
                        continue;
                    }
                    byte[] data = Files.readAllBytes(file.toPath());
                    String chalkId = file.getName().replace(".png", "")
                        .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
                    SERVER_CHALK_CACHE.put(chalkId, data);
                } catch (Exception e) {
                    System.err.println("[ZombieRool] Impossible de charger le chalk "
                        + file.getName() + " : " + e.getMessage());
                }
            }
        }
    }

    public static String getRandomSkin(String mobType) {
        Map<String, byte[]> mobSkins = SERVER_SKIN_CACHE.get(mobType);
        if (mobSkins == null || mobSkins.isEmpty()) return "";
        if (!overrideSkins && RANDOM.nextBoolean()) return "";
        List<String> keys = new ArrayList<>(mobSkins.keySet());
        return keys.get(RANDOM.nextInt(keys.size()));
    }

    public static Map<String, Map<String, byte[]>> getAllServerSkins() {
        return SERVER_SKIN_CACHE;
    }

    public static boolean hasCustomVoice(String eventName) {
        return CUSTOM_VOICE_MAP.containsKey(eventName) && !CUSTOM_VOICE_MAP.get(eventName).isEmpty();
    }

    public static String getRandomCustomVoiceKey(String eventName) {
        List<String> list = CUSTOM_VOICE_MAP.get(eventName);
        if (list == null || list.isEmpty()) return "zombierool:" + eventName; 
        return list.get(RANDOM.nextInt(list.size()));
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerClientSkin(String mobType, String skinId, byte[] data) {
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(data));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = new ResourceLocation("zombierool",
                "dynamic_skin_" + mobType + "_" + skinId);

            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().getTextureManager().register(location, texture);
                CLIENT_SKIN_CACHE.computeIfAbsent(mobType, k -> new HashMap<>())
                    .put(skinId, location);
            });
        } catch (Exception e) {
            System.err.println("[ZombieRool] Impossible d'enregistrer la skin dynamique : "
                + mobType + "/" + skinId);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static void registerClientChalk(String chalkId, byte[] data) {
        try {
            NativeImage image = NativeImage.read(new java.io.ByteArrayInputStream(data));
            DynamicTexture texture = new DynamicTexture(image);
            ResourceLocation location = new ResourceLocation("zombierool", "dynamic_chalk_" + chalkId);
            Minecraft.getInstance().execute(() -> {
                Minecraft.getInstance().getTextureManager().register(location, texture);
                CLIENT_CHALK_CACHE.put(chalkId, location);
            });
        } catch (Exception e) {
            System.err.println("[ZombieRool] Impossible d'enregistrer le chalk dynamique : " + chalkId);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public static ResourceLocation getClientSkin(String mobType, String skinId) {
        Map<String, ResourceLocation> mobSkins = CLIENT_SKIN_CACHE.get(mobType);
        return mobSkins != null ? mobSkins.get(skinId) : null;
    }

    @OnlyIn(Dist.CLIENT)
    public static List<ResourceLocation> getAllClientChalks() {
        return new ArrayList<>(CLIENT_CHALK_CACHE.values());
    }

    @OnlyIn(Dist.CLIENT)
    public static void clearClientResources() {
        CLIENT_SKIN_CACHE.clear();
        CLIENT_CHALK_CACHE.clear();
    }

    private static void loadConfiguration(File zrDir) {
        File configJson = new File(zrDir, "config.json");
        if (configJson.exists()) {
            try (FileReader fr = new FileReader(configJson)) {
                JsonObject cfg = GSON.fromJson(fr, JsonObject.class);
                if (cfg != null) {
                    if (cfg.has("override_sfx")) overrideSfx = cfg.get("override_sfx").getAsBoolean();
                    if (cfg.has("override_music")) overrideMusic = cfg.get("override_music").getAsBoolean();
                    if (cfg.has("override_voices")) overrideVoices = cfg.get("override_voices").getAsBoolean();
                    if (cfg.has("override_skins")) overrideSkins = cfg.get("override_skins").getAsBoolean();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static void createDefaultFilesIfMissing(File zrDir) {
        File config = new File(zrDir, "config.json");
        if (!config.exists()) {
            try (FileWriter fw = new FileWriter(config)) {
                JsonObject json = new JsonObject();
                json.addProperty("override_sfx", false);
                json.addProperty("override_music", false);
                json.addProperty("override_voices", false);
                json.addProperty("override_skins", false);
                GSON.toJson(json, fw);
            } catch (Exception e) {}
        }

        File readme = new File(zrDir, "README.md");
        if (!readme.exists()) {
            try (FileWriter fw = new FileWriter(readme)) {
                String readmeContent = """
                        # ZombieRool Customization
                        This folder allows you to override or add custom assets to your ZombieRool map without needing a traditional resource pack. These assets are sent to the client automatically when they join the world.
                        ## 1. Audio (`audio/`)
                        - `music/`: Custom background music (`default`, `damned`, `secret`, `menu`).
                        - `sfx/`: Custom sound effects for game events (`start`, `round_change`, etc.).
                        - `voices/`: Custom character voice lines (`player_1_xxx.ogg`).
                        All audio files MUST be in `.ogg` format and smaller than 15MB.
                        
                        ## 2. Skins (`skins/`)
                        - Add custom `.png` textures for mobs (`zombie/`, `hellhound/`, `crawler/`).
                        - Add custom `.png` textures for emissive eyes (`zombie_eyes/`, etc.).
                        All skin files MUST be in `.png` format and smaller than 2MB.

                        ## 3. Chalks / Overlays (`assets/chalks/`)
                        - Place your custom `.png` chalk textures here.
                        - These textures will be automatically loaded and available in-game using the **Chalk** item (Craie).
                        - You can use the Chalk item to select, rotate, and place these textures as overlays on blocks (great for writing "HELP", drawing arrows, or map secrets).
                        - **Recommended Size:** `128x128` pixels to match the vanilla ZombieRool style.
                        All chalk files MUST be in `.png` format and smaller than 2MB.

                        *Note: Remember to enable the overrides in `config.json` for audio and skins! Chalks are loaded automatically.*
                        """;
                fw.write(readmeContent);
            } catch (Exception e) {}
        }

        File sfxDir = new File(zrDir, "audio/sfx");
        sfxDir.mkdirs();
        new File(sfxDir, "start").mkdirs();
        new File(sfxDir, "round_change").mkdirs();
        new File(sfxDir, "special_change").mkdirs();
        new File(sfxDir, "special_start").mkdirs();
        new File(sfxDir, "ambient").mkdirs();
        new File(sfxDir, "misc").mkdirs();

        new File(zrDir, "audio/voices").mkdirs();

        File musicDir = new File(zrDir, "audio/music");
        musicDir.mkdirs();
        new File(musicDir, "default").mkdirs();
        new File(musicDir, "damned").mkdirs();
        new File(musicDir, "secret").mkdirs();
        new File(musicDir, "menu").mkdirs();

        new File(zrDir, "skins/zombie").mkdirs();
        new File(zrDir, "skins/crawler").mkdirs();
        new File(zrDir, "skins/hellhound").mkdirs();
        new File(zrDir, "skins/zombie_eyes").mkdirs();
        new File(zrDir, "skins/crawler_eyes").mkdirs();
        new File(zrDir, "skins/hellhound_eyes").mkdirs();

        new File(zrDir, "assets/chalks").mkdirs();
    }

    private static String getSafeName(String original) {
        return original.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
    }

    private static String extractBaseVoiceEvent(String rawName) {
        List<String> sortedEvents = new ArrayList<>(Arrays.asList(VALID_VOICE_EVENTS));
        sortedEvents.sort((a, b) -> Integer.compare(b.length(), a.length()));
        for (String event : sortedEvents) {
            if (rawName.startsWith(event)) return event;
        }
        return null;
    }

    private static boolean hasAnyOggFiles(File dir) {
        if (!dir.exists() || !dir.isDirectory()) return false;
        File[] files = dir.listFiles();
        if (files == null) return false;
        for (File f : files) {
            if (f.isDirectory()) {
                if (hasAnyOggFiles(f)) return true;
            } else if (f.getName().toLowerCase(Locale.ROOT).endsWith(".ogg")) {
                return true;
            }
        }
        return false;
    }

    private static class AudioEntry {
        final String category;
        final byte[] data;
        AudioEntry(String category, byte[] data) {
            this.category = category;
            this.data = data;
        }
    }
}