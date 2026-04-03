package me.cryo.zombierool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.ResourceLocation;
import me.cryo.zombierool.init.ZombieroolModSounds;
import net.minecraft.Util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.concurrent.*;

public class MapDownloaderScreen extends Screen {

    // ─────────────────────────────────────────────
    //  CONSTANTS
    // ─────────────────────────────────────────────
    private static final String OFFICIAL_JSON_URL  = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/maps.json";
    private static final String COMMUNITY_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-community-hub/main/maps.json";
    private static final String FEATURED_JSON_URL  = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/featured.json";
    private static final String GITHUB_OFFICIAL_URL = "https://github.com/Cryo60/zombierool-maps";
    private static final String PUBLISH_APP_URL    = "https://zombierool-community.onrender.com";
    private static final String DISCORD_MESSAGE    = "Join our Discord for community-made maps!";
    private static final String DISCORD_INVITE     = "https://discord.gg/QxsqH2FhFq";

    private static final long MAX_FILE_SIZE    = 500L * 1024L * 1024L;
    private static final int  CONNECT_TIMEOUT  = 15000;
    private static final int  READ_TIMEOUT     = 60000;

    // ─────────────────────────────────────────────
    //  DESIGN PALETTE  (ARGB)
    // ─────────────────────────────────────────────
    private static final int C_BG_DARK      = 0xCC000000;
    private static final int C_BG_PANEL     = 0x8C000000;
    private static final int C_BG_CARD      = 0x88000000;
    private static final int C_BG_CARD_SEL  = 0x0AFFCC00;
    private static final int C_BG_GRID      = 0x07006480;

    private static final int C_BORDER_CYAN_DIM  = 0x26006480;
    private static final int C_BORDER_CYAN_MED  = 0x4D00C8FF;
    private static final int C_BORDER_CYAN_FULL = 0xFF00CCFF;
    private static final int C_BORDER_GOLD      = 0x59FFCC00;
    private static final int C_BORDER_CARD      = 0x12FFFFFF;
    private static final int C_BORDER_CARD_SEL  = 0x66FFCC00;

    private static final int C_ACCENT_INSTALLED = 0xFF44FF88;
    private static final int C_ACCENT_UPDATE    = 0xFFFFAA00;

    private static final int C_TEXT_WHITE    = 0xFFFFFFFF;
    private static final int C_TEXT_LIGHT    = 0xFFCCCCCC;
    private static final int C_TEXT_MED      = 0xFF888888;
    private static final int C_TEXT_DARK     = 0xFF444444;
    private static final int C_TEXT_HINT     = 0xFF333333;
    private static final int C_TEXT_CYAN     = 0xFF00CCFF;
    private static final int C_TEXT_GOLD     = 0xFFFFCC00;
    private static final int C_TEXT_GREEN    = 0xFF44FF88;
    private static final int C_TEXT_ORANGE   = 0xFFFFAA00;
    private static final int C_TEXT_STARS    = 0xFFFFCC00;
    private static final int C_TEXT_DL       = 0xFF555555;
    private static final int C_TEXT_VERSION  = 0xFF3A3A3A;
    private static final int C_TEXT_DISCORD  = 0xFF5566FF;

    private static final int C_BTN_INSTALL   = 0xCC22C55E;
    private static final int C_BTN_UPDATE    = 0xCCF59E0B;
    private static final int C_BTN_DONE      = 0xCC50505A;
    private static final int C_BTN_DISABLED  = 0xFF555555;
    private static final int C_BTN_TXT       = 0xFFFFFFFF;
    private static final int C_BTN_TXT_DONE  = 0xFFBBBBBB;

    // ─────────────────────────────────────────────
    //  LAYOUT
    // ─────────────────────────────────────────────
    private static final int H_TOPBAR      = 36;
    private static final int H_TABS        = 30;
    private static final int H_BODY_PAD    = 10; 
    private static final int H_FEATURED    = 88;
    private static final int H_PROGRESS    = 28;
    private static final int H_BOTTOMBAR   = 30;

    private static final int CARD_HEIGHT   = 76;
    private static final int CARD_GAP      = 6;
    private static final int CARD_IMG_W    = 110;
    private static final int CARD_BTN_W    = 88;
    private static final int CARD_BTN_H    = 20;
    
    private static final int FEAT_IMG_W    = 130;
    private static final int FEAT_BTN_W    = 80;
    private static final int FEAT_BTN_H    = 22;

    private int bodyY;      
    private int listY;      
    private int listBottom; 
    private int progressY;  
    private int bottomY;    
    private int contentX;   
    private int contentW;   

    // ─────────────────────────────────────────────
    //  STATE
    // ─────────────────────────────────────────────
    private final Screen lastScreen;
    private MapList mapList;
    private Button officialTabButton;
    private Button communityTabButton;
    private Button sortButton;
    private Button downloadAllButton;
    private Button openGithubButton;
    private Button publishButton;
    private Button backButton;
    private EditBox searchBox;

    private List<MapEntry> allLoadedMaps  = new ArrayList<>();
    private List<MapEntry> filteredMaps   = new ArrayList<>();
    private MapEntry featuredMap          = null;
    private String officialFeaturedId     = "";
    private String communityFeaturedId    = "";

    private boolean loading              = true;
    private boolean loadingFeatured      = true;
    private String  errorMessage         = null;
    private String  statusMessage        = null;
    private float   downloadProgress     = 0.0f;
    private boolean isDownloading        = false;
    private boolean networkIssuesDetected = false;

    private int discordLinkX, discordLinkY, discordLinkWidth;
    private boolean isOfficialTab = true;

    private int currentSortIndex = 0;
    private final String[] sortKeys = {
        "gui.zombierool.downloader.sort.stars",
        "gui.zombierool.downloader.sort.downloads",
        "gui.zombierool.downloader.sort.recent"
    };
    private final String[] sortLabels = { "★ STARS", "⬇ DOWNLOADS", "🕐 RECENT" };

    private final Map<String, DynamicTexture>  imageCache            = new HashMap<>();
    private final Map<String, ResourceLocation> resourceLocationCache = new HashMap<>();

    public enum MapInstallState { NOT_INSTALLED, UPDATE_AVAILABLE, INSTALLED }

    public MapDownloaderScreen(Screen lastScreen) {
        super(Component.translatable("gui.zombierool.downloader.maps.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        bodyY      = H_TOPBAR + H_TABS;
        bottomY    = this.height - H_BOTTOMBAR;
        progressY  = bottomY - H_PROGRESS;
        listBottom = progressY - H_BODY_PAD;
        listY      = bodyY + H_BODY_PAD + H_FEATURED + CARD_GAP;

        int pad    = clamp((int)(this.width * 0.02f), 8, 20);
        contentX   = pad;
        contentW   = this.width - pad * 2;

        int tabW = Math.min(110, (this.width - 40) / 4);
        this.officialTabButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.official"),
                btn -> { playSound(); switchTab(true); })
                .bounds(contentX, H_TOPBAR + 4, tabW, 22).build());

        this.communityTabButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.community"),
                btn -> { playSound(); switchTab(false); })
                .bounds(contentX + tabW + 4, H_TOPBAR + 4, tabW, 22).build());

        int sortW   = Math.min(90, contentW / 5);
        int searchW = Math.min(140, contentW / 4);
        int sortX   = this.width - contentX - sortW - searchW - 6;
        int searchX = this.width - contentX - searchW;

        this.sortButton = this.addRenderableWidget(
            Button.builder(Component.literal(sortLabels[currentSortIndex]), btn -> {
                playSound();
                currentSortIndex = (currentSortIndex + 1) % sortLabels.length;
                btn.setMessage(Component.literal(sortLabels[currentSortIndex]));
                filterMaps();
            }).bounds(sortX, H_TOPBAR + 4, sortW, 22).build());

        this.searchBox = new EditBox(this.font, searchX, H_TOPBAR + 7, searchW, 16,
                                     Component.translatable("gui.zombierool.downloader.search"));
        this.searchBox.setResponder(q -> filterMaps());
        this.addRenderableWidget(this.searchBox);

        int listH = listBottom - listY;
        this.mapList = new MapList(this.minecraft, this.width, Math.max(10, listH), listY, listBottom, CARD_HEIGHT + CARD_GAP);
        this.addWidget(this.mapList);

        int btnCount = 4;
        int btnW     = Math.min(100, (contentW - (btnCount - 1) * 6) / btnCount);
        int bY       = bottomY + 5;

        this.downloadAllButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.update_all"),
                btn -> { playSound(); downloadAllMaps(); })
                .bounds(contentX, bY, btnW, 20).build());

        this.openGithubButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.github"),
                btn -> { playSound(); Util.getPlatform().openUri(GITHUB_OFFICIAL_URL); })
                .bounds(contentX + btnW + 6, bY, btnW, 20).build());

        this.publishButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.publish"),
                btn -> { playSound(); Util.getPlatform().openUri(PUBLISH_APP_URL); })
                .bounds(contentX + (btnW + 6) * 2, bY, btnW, 20).build());

        this.backButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.back"),
                btn -> { playSound(); this.minecraft.setScreen(lastScreen); })
                .bounds(contentX + (btnW + 6) * 3, bY, btnW, 20).build());

        loadFeaturedData();
        switchTab(isOfficialTab);
    }

    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }

    private void playSound() {
        this.minecraft.getSoundManager().play(
            SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }

    private void switchTab(boolean official) {
        this.isOfficialTab = official;
        this.loading       = true;
        this.errorMessage  = null;
        this.allLoadedMaps.clear();
        this.filteredMaps.clear();
        this.featuredMap = null;
        if (this.searchBox != null) this.searchBox.setValue("");
        if (this.mapList   != null) this.mapList.refreshList();
        updateTabStates();
        loadMapsFromUrl(isOfficialTab ? OFFICIAL_JSON_URL : COMMUNITY_JSON_URL);
    }

    private void updateTabStates() {
        if (officialTabButton   != null) officialTabButton.active   = !isOfficialTab && !isDownloading;
        if (communityTabButton  != null) communityTabButton.active  = isOfficialTab  && !isDownloading;
        if (openGithubButton    != null) openGithubButton.visible   = isOfficialTab;
        if (publishButton       != null) publishButton.visible      = !isOfficialTab;
        updateButtonStates();
    }

    private void updateButtonStates() {
        if (downloadAllButton != null)
            downloadAllButton.active = !isDownloading &&
                filteredMaps.stream().anyMatch(m -> m.state == MapInstallState.UPDATE_AVAILABLE);
        if (backButton    != null) backButton.active    = !isDownloading;
        if (sortButton    != null) sortButton.active    = !isDownloading;
        if (searchBox     != null) searchBox.setEditable(!isDownloading);
    }

    private void loadFeaturedData() {
        new Thread(() -> {
            try {
                String urlWithAntiCache = FEATURED_JSON_URL + "?t=" + System.currentTimeMillis();
                HttpURLConnection conn = openConnectionWithRedirects(urlWithAntiCache);
                BufferedReader reader  = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder  sb     = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close(); conn.disconnect();

                JsonObject json = new Gson().fromJson(sb.toString(), JsonObject.class);
                if (json.has("official"))  officialFeaturedId  = json.get("official").getAsString();
                if (json.has("community")) communityFeaturedId = json.get("community").getAsString();

                loadingFeatured = false;
                this.minecraft.execute(this::resolveFeaturedMap);
            } catch (Exception e) { loadingFeatured = false; }
        }).start();
    }

    private void resolveFeaturedMap() {
        if (loading || loadingFeatured) return;
        String targetId = isOfficialTab ? officialFeaturedId : communityFeaturedId;
        featuredMap = null;
        if (targetId != null && !targetId.isEmpty()) {
            for (MapEntry m : allLoadedMaps) {
                if (m.id.equals(targetId)) { featuredMap = m; break; }
            }
        }
        updateTabStates();
    }

    private void loadMapsFromUrl(String jsonUrl) {
        new Thread(() -> {
            try {
                System.setProperty("java.net.preferIPv4Stack", "true");
                String urlWithAntiCache = jsonUrl + (jsonUrl.contains("?") ? "&t=" : "?t=") + System.currentTimeMillis();
                HttpURLConnection conn = openConnectionWithRedirects(urlWithAntiCache);
                BufferedReader reader  = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder  sb     = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close(); conn.disconnect();

                JsonObject json      = new Gson().fromJson(sb.toString(), JsonObject.class);
                JsonArray  mapsArray = json.getAsJsonArray("maps");

                allLoadedMaps.clear();
                for (int i = 0; i < mapsArray.size(); i++) {
                    JsonObject mapObj = mapsArray.get(i).getAsJsonObject();

                    float avgStars    = 0f;
                    int   reviewsCount = 0;
                    if (mapObj.has("reviews")) {
                        JsonArray revArr = mapObj.getAsJsonArray("reviews");
                        reviewsCount = revArr.size();
                        if (reviewsCount > 0) {
                            float sum = 0;
                            for (int j = 0; j < reviewsCount; j++)
                                sum += revArr.get(j).getAsJsonObject().get("stars").getAsFloat();
                            avgStars = sum / reviewsCount;
                        }
                    }

                    List<String> tags = new ArrayList<>();
                    if (mapObj.has("tags")) {
                        JsonArray tArr = mapObj.getAsJsonArray("tags");
                        for (int t = 0; t < tArr.size(); t++) tags.add(tArr.get(t).getAsString().toLowerCase());
                    }

                    MapEntry entry = new MapEntry(
                        mapObj.get("id").getAsString(),
                        mapObj.get("name").getAsString(),
                        mapObj.has("description") ? mapObj.get("description").getAsString() : "",
                        mapObj.get("download_url").getAsString(),
                        mapObj.has("sha256")       ? mapObj.get("sha256").getAsString()       : null,
                        mapObj.has("author")       ? mapObj.get("author").getAsString()       : "Official",
                        mapObj.has("image_url")    ? mapObj.get("image_url").getAsString()    : null,
                        tags, avgStars, reviewsCount,
                        mapObj.has("game_version") ? mapObj.get("game_version").getAsString() : "Unknown",
                        mapObj.has("zr_version")   ? mapObj.get("zr_version").getAsString()   : "Unknown",
                        mapObj.has("version")      ? mapObj.get("version").getAsString()      : "1.0.0",
                        mapObj.has("downloads")    ? mapObj.get("downloads").getAsInt()       : 0,
                        mapObj.has("timestamp")    ? mapObj.get("timestamp").getAsLong()      : 0L
                    );

                    downloadImageAsync(entry);
                    allLoadedMaps.add(entry);
                }

                loading = false;
                this.minecraft.execute(() -> {
                    resolveFeaturedMap();
                    filterMaps();
                    checkDownloadedMaps();
                });

            } catch (FileNotFoundException e) {
                loading = false;
                this.minecraft.execute(() -> { allLoadedMaps.clear(); filterMaps(); checkDownloadedMaps(); });
            } catch (Exception e) {
                errorMessage = "Failed to load maps: " + e.getMessage();
                loading = false;
                networkIssuesDetected = true;
            }
        }).start();
    }

    private void downloadImageAsync(MapEntry entry) {
        if (entry.imageUrl == null || entry.imageUrl.isEmpty()) return;
        if (imageCache.containsKey(entry.id)) return;

        new Thread(() -> {
            try {
                HttpURLConnection conn = openConnectionWithRedirects(entry.imageUrl);
                try (InputStream in = conn.getInputStream()) {
                    NativeImage image = NativeImage.read(in);
                    this.minecraft.execute(() -> {
                        DynamicTexture   tex = new DynamicTexture(image);
                        ResourceLocation loc = this.minecraft.getTextureManager()
                                                    .register("zr_map_" + entry.id, tex);
                        imageCache.put(entry.id, tex);
                        resourceLocationCache.put(entry.id, loc);
                    });
                }
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private void checkDownloadedMaps() {
        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        Gson gson     = new Gson();
        for (MapEntry map : allLoadedMaps) checkStateForMap(map, savesDir, gson);
        if (featuredMap != null) checkStateForMap(featuredMap, savesDir, gson);
        updateButtonStates();
    }

    private void checkStateForMap(MapEntry mapObj, File savesDir, Gson gson) {
        File mapDir = new File(savesDir, mapObj.name);
        MapInstallState state = MapInstallState.NOT_INSTALLED;

        if (mapDir.exists() && mapDir.isDirectory()) {
            File vFile = new File(mapDir, "zombierool_map.json");
            if (vFile.exists()) {
                try (FileReader reader = new FileReader(vFile)) {
                    JsonObject vJson      = gson.fromJson(reader, JsonObject.class);
                    String     localSha   = vJson.has("sha256")   ? vJson.get("sha256").getAsString()   : "";
                    String     localVer   = vJson.has("version")  ? vJson.get("version").getAsString()  : "";

                    boolean    hashDiff   = mapObj.sha256  != null && !mapObj.sha256.isEmpty()
                                           && !localSha.equalsIgnoreCase(mapObj.sha256);
                    boolean    verDiff    = mapObj.version != null && !mapObj.version.isEmpty()
                                           && !localVer.equalsIgnoreCase(mapObj.version);

                    state = (hashDiff || verDiff) ? MapInstallState.UPDATE_AVAILABLE : MapInstallState.INSTALLED;
                } catch (Exception e) { state = MapInstallState.UPDATE_AVAILABLE; }
            } else {
                state = MapInstallState.UPDATE_AVAILABLE;
            }
        }
        mapObj.state = state;
    }

    public void downloadMap(MapEntry map) {
        if (isDownloading) return;
        new Thread(() -> {
            try {
                downloadMapInternal(map);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                this.minecraft.execute(() -> {
                    statusMessage = "Error: " + msg;
                    isDownloading = false;
                    updateButtonStates();
                });
            }
        }).start();
    }

    private void downloadAllMaps() {
        if (isDownloading) return;
        List<MapEntry> todo = new ArrayList<>();
        for (MapEntry m : filteredMaps)
            if (m.state == MapInstallState.UPDATE_AVAILABLE) todo.add(m);

        if (todo.isEmpty()) { statusMessage = "All maps are up to date!"; return; }

        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (MapEntry map : todo) {
            chain = chain.thenCompose(v -> {
                CompletableFuture<Void> f = new CompletableFuture<>();
                new Thread(() -> {
                    try { downloadMapInternal(map); }
                    catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        this.minecraft.execute(() -> statusMessage = "Error on " + map.name + ": " + msg);
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    f.complete(null);
                }).start();
                return f;
            });
        }
        chain.thenRun(() -> this.minecraft.execute(() -> {
            isDownloading = false;
            statusMessage = "§aAll updates completed!";
            updateButtonStates();
        }));
    }

    private void downloadMapInternal(MapEntry map) throws Exception {
        this.minecraft.execute(() -> {
            isDownloading    = true;
            downloadProgress = 0f;
            statusMessage    = "Downloading " + map.name + "...";
            updateButtonStates();
        });

        downloadFile(map.downloadUrl, map.name, map.name + ".zip", map.sha256);
        recordDownloadOnServer(map.id);

        this.minecraft.execute(() -> {
            checkDownloadedMaps();
            saveUnifiedMetadata(map, new File(
                new File(Minecraft.getInstance().gameDirectory, "saves"), map.name));
            statusMessage    = "§aSuccessfully installed: " + map.name;
            downloadProgress = 0f;
            isDownloading    = false;
            updateButtonStates();
        });
    }

    private void recordDownloadOnServer(String mapId) {
        new Thread(() -> {
            try {
                URL url = new URL(PUBLISH_APP_URL + "/api/map/" + mapId + "/download");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "ZombieRool-Client/1.0");
                conn.setConnectTimeout(60000);
                conn.setReadTimeout(60000);
                int code = conn.getResponseCode();
                System.out.println("[ZombieRool] Record download for " + mapId + " -> HTTP " + code);
                conn.disconnect();
            } catch (Exception e) {
                System.err.println("[ZombieRool] Error recording download for " + mapId + ": " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void downloadFile(String fileUrl, String name, String fileName, String sha256Hash) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        HttpURLConnection conn = openConnectionWithRedirects(fileUrl);

        File tempDir = new File(Minecraft.getInstance().gameDirectory, "temp_downloads");
        tempDir.mkdirs();
        File zipFile = new File(tempDir, fileName);

        long contentLength = conn.getContentLengthLong();
        if (contentLength > MAX_FILE_SIZE) { conn.disconnect(); throw new Exception("File exceeds maximum size"); }

        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(zipFile)) {
            byte[] buffer = new byte[8192]; int bytesRead; long totalRead = 0;
            long lastUpdate = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (totalRead > MAX_FILE_SIZE) throw new IOException("Limit exceeded during download");

                long now = System.currentTimeMillis();
                if (contentLength > 0 && now - lastUpdate > 100) {
                    final float p = (float) totalRead / contentLength;
                    this.minecraft.execute(() -> downloadProgress = p);
                    lastUpdate = now;
                }
            }
        }
        conn.disconnect();

        this.minecraft.execute(() -> statusMessage = "Installing " + name + "...");
        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        File targetDir = new File(savesDir, name);

        if (sha256Hash != null && !sha256Hash.isEmpty()) {
            this.minecraft.execute(() -> statusMessage = "Verifying integrity...");
            String actual = calculateSHA256(zipFile);
            if (actual != null && !actual.equalsIgnoreCase(sha256Hash)) {
                zipFile.delete(); throw new Exception("Hash mismatch (integrity check failed)");
            }
        }

        if (targetDir.exists()) deleteDirectory(targetDir);
        targetDir.mkdirs();

        extractZip(zipFile, targetDir);
        zipFile.delete();
    }

    private void saveUnifiedMetadata(MapEntry map, File worldDir) {
        try {
            File       metaFile = new File(worldDir, "zombierool_map.json");
            JsonObject json     = new JsonObject();

            if (metaFile.exists()) {
                try (FileReader r = new FileReader(metaFile)) {
                    json = new Gson().fromJson(r, JsonObject.class);
                    if (json == null) json = new JsonObject();
                } catch (Exception ignored) {}
            }

            json.addProperty("id",      map.id);
            json.addProperty("version", map.version != null ? map.version : "1.0.0");
            json.addProperty("sha256",  map.sha256  != null ? map.sha256  : "");

            try (FileWriter w = new FileWriter(metaFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, w);
            }
        } catch (Exception ignored) {}
    }

    private void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] contents = dir.listFiles();
        if (contents != null) for (File f : contents) deleteDirectory(f);
        dir.delete();
    }

    private String calculateSHA256(File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = fis.read(buf)) != -1) digest.update(buf, 0, n);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return null; }
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if (!file.getCanonicalPath().startsWith(destDir.getCanonicalPath() + File.separator))
                    throw new IOException("Zip Slip detected");

                if (entry.isDirectory()) { file.mkdirs(); }
                else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buf = new byte[8192]; int len;
                        while ((len = zis.read(buf)) > 0) fos.write(buf, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void filterMaps() {
        String query = (searchBox != null) ? searchBox.getValue().toLowerCase().trim() : "";
        filteredMaps.clear();

        for (MapEntry m : allLoadedMaps) {
            if (query.isEmpty()
                || m.name.toLowerCase().contains(query)
                || m.author.toLowerCase().contains(query)
                || m.tags.stream().anyMatch(t -> t.contains(query))) {
                filteredMaps.add(m);
            }
        }

        filteredMaps.sort((a, b) -> switch (currentSortIndex) {
            case 1  -> Integer.compare(b.downloads, a.downloads);
            case 2  -> Long.compare(b.timestamp, a.timestamp);
            default -> Float.compare(b.avgStars, a.avgStars);
        });

        if (mapList != null) mapList.refreshList();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= discordLinkX && mouseX <= discordLinkX + discordLinkWidth
            && mouseY >= discordLinkY && mouseY <= discordLinkY + 9) {
            playSound(); Util.getPlatform().openUri(DISCORD_INVITE); return true;
        }

        if (featuredMap != null && !loadingFeatured) {
            int featX   = contentX;
            int featBtnX = featX + contentW - FEAT_BTN_W - 10;
            int featBtnY = bodyY + H_BODY_PAD + H_FEATURED - FEAT_BTN_H - 10;
            if (mouseX >= featBtnX && mouseX <= featBtnX + FEAT_BTN_W
                && mouseY >= featBtnY && mouseY <= featBtnY + FEAT_BTN_H && !isDownloading) {
                playSound();
                if (featuredMap.state != MapInstallState.NOT_INSTALLED) {
                    this.minecraft.setScreen(new OverwriteConfirmScreen(this, featuredMap));
                } else {
                    downloadMap(featuredMap);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int kc, int sc, int mod) {
        if (searchBox != null && searchBox.isFocused()) {
            if (kc == 256) { onClose(); return true; }
            return searchBox.keyPressed(kc, sc, mod);
        }
        return super.keyPressed(kc, sc, mod);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float pt) {
        lastMouseX = mouseX; lastMouseY = mouseY;
        this.renderBackground(g);
        renderBGGrid(g);
        super.render(g, mouseX, mouseY, pt);

        renderTopBar(g);
        renderTabsBackground(g);

        if (loading) {
            g.drawCenteredString(font,
                Component.translatable("gui.zombierool.downloader.loading"),
                this.width / 2, this.height / 2, C_TEXT_MED);
        } else if (errorMessage != null) {
            g.drawCenteredString(font, errorMessage, this.width / 2, this.height / 2, 0xFFFF5555);
        } else {
            renderFeatured(g, mouseX, mouseY);
            if (mapList != null) mapList.render(g, mouseX, mouseY, pt);
        }

        renderProgressRow(g);
        renderBottomBar(g, mouseX, mouseY);
    }

    private void renderBGGrid(GuiGraphics g) {
        for (int gx = 0; gx < this.width;  gx += 40) g.fill(gx, 0, gx + 1, this.height, C_BG_GRID);
        for (int gy = 0; gy < this.height; gy += 40) g.fill(0, gy, this.width, gy + 1,   C_BG_GRID);
    }

    private void renderTopBar(GuiGraphics g) {
        g.fill(0, 0, this.width, H_TOPBAR, C_BG_DARK);
        g.fill(0, H_TOPBAR - 1, this.width, H_TOPBAR, C_BORDER_CYAN_DIM);
        g.drawCenteredString(font, "MAP DOWNLOADER", this.width / 2, H_TOPBAR / 2 - 4, C_TEXT_GOLD);
    }

    private void renderTabsBackground(GuiGraphics g) {
        g.fill(0, H_TOPBAR, this.width, bodyY, 0x99000000);
        g.fill(0, bodyY - 1, this.width, bodyY, C_BORDER_CYAN_DIM);
    }

    private void renderFeatured(GuiGraphics g, int mouseX, int mouseY) {
        if (loadingFeatured) {
            g.drawCenteredString(font, "Loading featured...",
                contentX + contentW / 2, bodyY + H_BODY_PAD + H_FEATURED / 2 - 4, C_TEXT_DARK);
            return;
        }
        if (featuredMap == null) return;

        int fX = contentX;
        int fY = bodyY + H_BODY_PAD;
        int fW = contentW;
        int fH = H_FEATURED;

        g.fill(fX, fY, fX + fW, fY + fH, C_BG_CARD);
        g.renderOutline(fX, fY, fW, fH, C_BORDER_GOLD);

        ResourceLocation imgLoc = resourceLocationCache.get(featuredMap.id);
        if (imgLoc != null) {
            RenderSystem.enableBlend();
            g.blit(imgLoc, fX + 1, fY + 1, 0, 0, FEAT_IMG_W, fH - 2, FEAT_IMG_W, fH - 2);
            RenderSystem.disableBlend();
        } else {
            g.fill(fX + 1, fY + 1, fX + FEAT_IMG_W, fY + fH - 1, 0xFF111111);
            g.drawCenteredString(font, "No Image", fX + FEAT_IMG_W / 2, fY + fH / 2 - 4, C_TEXT_DARK);
        }
        g.fill(fX + FEAT_IMG_W, fY + 6, fX + FEAT_IMG_W + 1, fY + fH - 6, C_BORDER_CYAN_DIM);

        int tX = fX + FEAT_IMG_W + 10;
        int tY = fY + 8;
        int tW = fW - FEAT_IMG_W - FEAT_BTN_W - 24;

        g.drawString(font, "★ FEATURED", tX, tY, C_TEXT_GOLD);
        tY += 12;

        String statusSuffix = featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? " [UPDATE]"
                            : featuredMap.state == MapInstallState.INSTALLED         ? " [INSTALLED]" : "";
        int nameColor = featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? C_TEXT_ORANGE
                      : featuredMap.state == MapInstallState.INSTALLED         ? C_TEXT_GREEN : C_TEXT_WHITE;

        String displayName = featuredMap.name + statusSuffix;
        while (font.width(displayName) > tW && displayName.length() > 4)
            displayName = displayName.substring(0, displayName.length() - 1);
        g.drawString(font, displayName, tX, tY, nameColor);
        tY += 12;

        String desc = font.plainSubstrByWidth(featuredMap.description, tW);
        g.drawString(font, desc, tX, tY, C_TEXT_MED);
        tY += 11;

        String meta = "MC: " + featuredMap.gameVersion + "  ·  ZR: " + featuredMap.zrVersion
                    + "  ·  v" + featuredMap.version + "  ·  ⬇ " + featuredMap.downloads;
        g.drawString(font, meta, tX, tY, C_TEXT_DARK);
        tY += 11;

        int tagX = tX;
        for (String tag : featuredMap.tags) {
            int tagW2 = font.width(tag) + 8;
            if (tagX + tagW2 > fX + fW - FEAT_BTN_W - 14) break;
            g.fill(tagX, tY, tagX + tagW2, tY + 10, 0x1A6699FF);
            g.renderOutline(tagX, tY, tagW2, 10, 0x2666AAFF);
            g.drawString(font, tag, tagX + 4, tY + 1, C_TEXT_CYAN);
            tagX += tagW2 + 4;
        }

        int btnX = fX + fW - FEAT_BTN_W - 10;
        int btnY = fY + fH - FEAT_BTN_H - 10;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + FEAT_BTN_W
                      && mouseY >= btnY && mouseY <= btnY + FEAT_BTN_H;

        int btnColor = isDownloading ? C_BTN_DISABLED
                     : featuredMap.state == MapInstallState.NOT_INSTALLED ? (btnHov ? 0xCC16A34A : C_BTN_INSTALL)
                     : featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? (btnHov ? 0xCCD97706 : C_BTN_UPDATE)
                     : (btnHov ? 0xCC4B5563 : C_BTN_DONE);

        g.fill(btnX, btnY, btnX + FEAT_BTN_W, btnY + FEAT_BTN_H, btnColor);
        String btnTxt = featuredMap.state == MapInstallState.NOT_INSTALLED ? "Download"
                      : featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? "Update" : "Reinstall";
        g.drawCenteredString(font, btnTxt, btnX + FEAT_BTN_W / 2, btnY + FEAT_BTN_H / 2 - 4, C_BTN_TXT);
    }

    private void renderProgressRow(GuiGraphics g) {
        g.fill(0, progressY, this.width, progressY + H_PROGRESS, 0x88000000);
        g.fill(0, progressY, this.width, progressY + 1, C_BORDER_CYAN_DIM);

        int cy = progressY + H_PROGRESS / 2 - 4;

        if (statusMessage != null) {
            String raw = statusMessage.startsWith("§a") ? statusMessage.substring(2) : statusMessage;
            int col = statusMessage.startsWith("§a") ? C_TEXT_GREEN
                    : (isDownloading ? C_TEXT_ORANGE : 0xFFFF5555);

            if (isDownloading && downloadProgress > 0) {
                g.drawString(font, raw, contentX, cy, col);
                int barW  = Math.min(200, contentW / 3);
                int barX  = this.width - contentX - barW;
                int barY  = progressY + H_PROGRESS / 2 - 3;

                g.fill(barX, barY, barX + barW, barY + 6, 0xFF1A1A1A);
                g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 7, 0xFF252525);
                g.fill(barX, barY, barX + (int)(barW * downloadProgress), barY + 6, C_TEXT_CYAN);
                String pct = (int)(downloadProgress * 100) + "%";
                g.drawString(font, pct, barX - font.width(pct) - 6, cy, C_TEXT_MED);
            } else {
                g.drawCenteredString(font, raw, this.width / 2, cy, col);
            }
        } else {
            discordLinkWidth = font.width(DISCORD_MESSAGE);
            discordLinkX     = (this.width - discordLinkWidth) / 2;
            discordLinkY     = cy;
            boolean hov = mouseCheck(discordLinkX, discordLinkY, discordLinkWidth, 9);
            g.drawCenteredString(font, DISCORD_MESSAGE, this.width / 2, cy, hov ? 0xFF7777FF : C_TEXT_DISCORD);
            if (hov) g.fill(discordLinkX, cy + 9, discordLinkX + discordLinkWidth, cy + 10, 0xFF5566FF);
        }
    }

    private int lastMouseX = 0, lastMouseY = 0;
    private boolean mouseCheck(int x, int y, int w, int h) {
        return lastMouseX >= x && lastMouseX <= x + w && lastMouseY >= y && lastMouseY <= y + h;
    }

    private void renderBottomBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, bottomY, this.width, this.height, C_BG_DARK);
        g.fill(0, bottomY, this.width, bottomY + 1, 0x14FFFFFF);
    }

    @Override
    public void removed() {
        super.removed();
        for (DynamicTexture t : imageCache.values()) t.close();
        imageCache.clear();
        resourceLocationCache.clear();
    }

    private HttpURLConnection openConnectionWithRedirects(String urlString) throws Exception {
        try {
            String decoded = URLDecoder.decode(urlString, StandardCharsets.UTF_8.name());
            URL    tempUrl = new URL(decoded);
            URI    uri     = new URI(tempUrl.getProtocol(), tempUrl.getUserInfo(), tempUrl.getHost(),
                                    tempUrl.getPort(), tempUrl.getPath(), tempUrl.getQuery(), tempUrl.getRef());
            urlString = uri.toASCIIString();
        } catch (Exception e) { urlString = urlString.replace(" ", "%20"); }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0");
        conn.setInstanceFollowRedirects(false);

        int status = conn.getResponseCode();
        int redirects = 0;
        while (status == 301 || status == 302 || status == 303 || status == 307 || status == 308) {
            if (redirects++ > 10) throw new IOException("Too many redirects");
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            url  = new URL(url, newUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT); conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(false);
            status = conn.getResponseCode();
        }
        if (status >= 400) {
            if (status == 404 && urlString.endsWith(".json")) throw new FileNotFoundException("JSON not found");
            throw new IOException("HTTP " + status);
        }
        return conn;
    }

    private class MapList extends ObjectSelectionList<MapEntry> {
        public MapList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            setRenderBackground(false);
            setRenderTopAndBottom(false);
            refreshList();
        }

        public void refreshList() {
            clearEntries();
            for (MapEntry m : filteredMaps) addEntry(m);
        }

        public void updatePosition(int top, int bottom) { this.y0 = top; this.y1 = bottom; }

        @Override public void setSelected(MapEntry e) { super.setSelected(e); updateButtonStates(); }
        @Override public int getRowWidth() { return Math.min(contentW, this.width - 12); }
        @Override protected int getScrollbarPosition() { return this.width / 2 + getRowWidth() / 2 + 4; }
        @Override protected void renderBackground(GuiGraphics g) { /* transparent */ }
    }

    private class MapEntry extends ObjectSelectionList.Entry<MapEntry> {
        public final String id, name, description, downloadUrl;
        public final String sha256, author, imageUrl;
        public final List<String> tags;
        public final float avgStars;
        public final int   reviewsCount;
        public final String gameVersion, zrVersion, version;
        public final int  downloads;
        public final long timestamp;
        public MapInstallState state = MapInstallState.NOT_INSTALLED;

        public MapEntry(String id, String name, String description, String downloadUrl,
                        String sha256,
                        String author, String imageUrl, List<String> tags,
                        float avgStars, int reviewsCount,
                        String gameVersion, String zrVersion, String version,
                        int downloads, long timestamp) {
            this.id = id; this.name = name; this.description = description;
            this.downloadUrl = downloadUrl; 
            this.sha256 = sha256;
            this.author = author; this.imageUrl = imageUrl; this.tags = tags;
            this.avgStars = avgStars; this.reviewsCount = reviewsCount;
            this.gameVersion = gameVersion; this.zrVersion = zrVersion; this.version = version;
            this.downloads = downloads; this.timestamp = timestamp;
        }

        @Override
        public void render(GuiGraphics g, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovering, float pt) {
            boolean isSelected = mapList.getSelected() == this;
            int cardH = height - CARD_GAP;

            g.fill(left, top, left + width, top + cardH, isSelected ? C_BG_CARD_SEL : C_BG_CARD);
            g.renderOutline(left, top, width, cardH, isSelected ? C_BORDER_CARD_SEL : C_BORDER_CARD);

            if (state == MapInstallState.INSTALLED) {
                g.fill(left, top, left + 3, top + cardH, C_ACCENT_INSTALLED);
            } else if (state == MapInstallState.UPDATE_AVAILABLE) {
                g.fill(left, top, left + 3, top + cardH, C_ACCENT_UPDATE);
            }

            int imgX = left + 4;
            int imgY = top + (cardH - 68) / 2;
            ResourceLocation imgLoc = resourceLocationCache.get(id);
            if (imgLoc != null) {
                RenderSystem.enableBlend();
                g.blit(imgLoc, imgX, imgY, 0, 0, CARD_IMG_W, 68, CARD_IMG_W, 68);
                RenderSystem.disableBlend();
            } else {
                g.fill(imgX, imgY, imgX + CARD_IMG_W, imgY + 68, 0xFF0E0E0E);
                g.drawCenteredString(font, "No Image", imgX + CARD_IMG_W / 2, imgY + 28, C_TEXT_DARK);
            }
            g.fill(imgX + CARD_IMG_W + 2, top + 6, imgX + CARD_IMG_W + 3, top + cardH - 6, C_BORDER_CYAN_DIM);

            int tX  = imgX + CARD_IMG_W + 8;
            int tW  = width - CARD_IMG_W - CARD_BTN_W - 22;
            int tY1 = top + 8;
            int tY2 = top + cardH / 2 - 4;
            int tY3 = top + cardH - 18;

            String nameStr = font.plainSubstrByWidth(name, tW - 80);
            g.drawString(font, nameStr, tX, tY1, C_TEXT_WHITE);

            int badgeX = tX + font.width(nameStr) + 6;
            g.drawString(font, "by " + author, badgeX, tY1, C_TEXT_DARK);
            badgeX += font.width("by " + author) + 6;

            if (state == MapInstallState.INSTALLED) {
                g.drawString(font, "✓ INSTALLED", badgeX, tY1, C_TEXT_GREEN);
            } else if (state == MapInstallState.UPDATE_AVAILABLE) {
                g.drawString(font, "↑ UPDATE", badgeX, tY1, C_TEXT_ORANGE);
            }

            String desc = font.plainSubstrByWidth(description, tW);
            g.drawString(font, desc, tX, tY2, C_TEXT_MED);

            String starsStr = reviewsCount > 0
                ? String.format("%.1f ★ (%d)", avgStars, reviewsCount) : "No ratings";
            g.drawString(font, starsStr, tX, tY3, C_TEXT_STARS);

            int dlX = tX + font.width(starsStr) + 10;
            g.drawString(font, "⬇ " + downloads, dlX, tY3, C_TEXT_DL);

            int verX = dlX + font.width("⬇ " + downloads) + 10;
            g.drawString(font, "MC " + gameVersion + " · v" + version, verX, tY3, C_TEXT_VERSION);

            int btnX = left + width - CARD_BTN_W - 8;
            int btnY = top + cardH / 2 - CARD_BTN_H / 2;
            boolean btnHov = mouseX >= btnX && mouseX <= btnX + CARD_BTN_W
                          && mouseY >= btnY && mouseY <= btnY + CARD_BTN_H;

            int btnColor = isDownloading ? C_BTN_DISABLED
                : state == MapInstallState.NOT_INSTALLED     ? (btnHov ? 0xCC16A34A : C_BTN_INSTALL)
                : state == MapInstallState.UPDATE_AVAILABLE  ? (btnHov ? 0xCCD97706 : C_BTN_UPDATE)
                : (btnHov ? 0xCC4B5563 : C_BTN_DONE);

            g.fill(btnX, btnY, btnX + CARD_BTN_W, btnY + CARD_BTN_H, btnColor);

            String btnTxt = state == MapInstallState.NOT_INSTALLED    ? "Download"
                          : state == MapInstallState.UPDATE_AVAILABLE ? "Update" : "Reinstall";

            int txtColor = (state == MapInstallState.INSTALLED && !isDownloading) ? C_BTN_TXT_DONE : C_BTN_TXT;
            g.drawCenteredString(font, btnTxt, btnX + CARD_BTN_W / 2, btnY + CARD_BTN_H / 2 - 4, txtColor);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            mapList.setSelected(this);
            int rowW  = mapList.getRowWidth();
            int left  = MapDownloaderScreen.this.width / 2 - rowW / 2;
            int btnX  = left + rowW - CARD_BTN_W - 8;

            if (mouseX >= btnX && mouseX <= btnX + CARD_BTN_W && !isDownloading) {
                playSound();
                if (state != MapInstallState.NOT_INSTALLED) {
                    minecraft.setScreen(new OverwriteConfirmScreen(MapDownloaderScreen.this, this));
                } else {
                    downloadMap(this);
                }
            }
            return true;
        }

        @Override public Component getNarration() { return Component.literal(name); }
    }

    private class OverwriteConfirmScreen extends Screen {
        private final MapDownloaderScreen parent;
        private final MapEntry map;

        OverwriteConfirmScreen(MapDownloaderScreen parent, MapEntry map) {
            super(Component.translatable("gui.zombierool.overwrite.title"));
            this.parent = parent; this.map = map;
        }

        @Override
        protected void init() {
            int bX = (this.width - 214) / 2;
            int bY = this.height / 2 + 28;

            this.addRenderableWidget(Button.builder(
                Component.translatable("gui.zombierool.overwrite.yes"), btn -> {
                    minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1f));
                    minecraft.setScreen(parent); 
                    parent.downloadMap(map);
                }).bounds(bX, bY, 100, 20).build());

            this.addRenderableWidget(Button.builder(
                Component.translatable("gui.zombierool.overwrite.cancel"), btn -> {
                    minecraft.getSoundManager().play(
                        SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1f));
                    minecraft.setScreen(parent);
                }).bounds(bX + 114, bY, 100, 20).build());
        }

        @Override
        public void render(GuiGraphics g, int mx, int my, float pt) {
            this.renderBackground(g);
            g.fill(0, 0, this.width, this.height, 0x99000000);

            int cX = this.width / 2;
            int cY = this.height / 2 - 38;

            g.fill(cX - 120, cY - 8, cX + 120, cY + 60, 0xEE0D1117);
            g.renderOutline(cX - 120, cY - 8, 240, 68, 0x59FF5555);

            g.drawCenteredString(font, this.title, cX, cY, 0xFFFF5555);
            g.drawCenteredString(font, Component.translatable("gui.zombierool.overwrite.warning1"), cX, cY + 16, C_TEXT_MED);

            super.render(g, mx, my, pt);
        }
    }
}