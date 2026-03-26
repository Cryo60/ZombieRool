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

    private static final String OFFICIAL_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/maps.json";
    private static final String COMMUNITY_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-community-hub/main/maps.json";
    private static final String FEATURED_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/featured.json";
    private static final String GITHUB_OFFICIAL_URL = "https://github.com/Cryo60/zombierool-maps";
    private static final String PUBLISH_APP_URL = "https://zombierool-community.onrender.com";
    private static final String DISCORD_MESSAGE = "Join our Discord for community-made maps!";
    private static final String DISCORD_INVITE = "https://discord.gg/QxsqH2FhFq";
    
    private static final long MAX_FILE_SIZE = 500L * 1024L * 1024L; 
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 60000;
    private static final int DOWNLOAD_TIMEOUT = 300000; 

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

    private List<MapEntry> allLoadedMaps = new ArrayList<>();
    private List<MapEntry> filteredMaps = new ArrayList<>();
    private MapEntry featuredMap = null;
    private String officialFeaturedId = "";
    private String communityFeaturedId = "";

    private boolean loading = true;
    private boolean loadingFeatured = true;
    private String errorMessage = null;
    private String statusMessage = null;
    private float downloadProgress = 0.0f;
    private boolean isDownloading = false;
    private int consecutiveFailures = 0;
    private boolean networkIssuesDetected = false;

    private int discordLinkX, discordLinkY, discordLinkWidth;
    private boolean isOfficialTab = true;

    private int currentSortIndex = 0;
    private final String[] sortKeys = {
        "gui.zombierool.downloader.sort.stars",
        "gui.zombierool.downloader.sort.downloads",
        "gui.zombierool.downloader.sort.recent"
    };

    private final Map<String, DynamicTexture> imageCache = new HashMap<>();
    private final Map<String, ResourceLocation> resourceLocationCache = new HashMap<>();

    public enum MapInstallState {
        NOT_INSTALLED,
        UPDATE_AVAILABLE,
        INSTALLED
    }

    public MapDownloaderScreen(Screen lastScreen) {
        super(Component.translatable("gui.zombierool.downloader.maps.title"));
        this.lastScreen = lastScreen;
    }

    private HttpURLConnection openConnectionWithRedirects(String urlString) throws Exception {
        try {
            String decoded = URLDecoder.decode(urlString, StandardCharsets.UTF_8.name());
            URL tempUrl = new URL(decoded);
            URI uri = new URI(tempUrl.getProtocol(), tempUrl.getUserInfo(), tempUrl.getHost(), tempUrl.getPort(), tempUrl.getPath(), tempUrl.getQuery(), tempUrl.getRef());
            urlString = uri.toASCIIString();
        } catch (Exception e) {
            urlString = urlString.replace(" ", "%20");
        }

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(CONNECT_TIMEOUT);
        conn.setReadTimeout(READ_TIMEOUT);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setInstanceFollowRedirects(false);

        int status = conn.getResponseCode();
        int redirects = 0;
        
        while (status == HttpURLConnection.HTTP_MOVED_TEMP || 
               status == HttpURLConnection.HTTP_MOVED_PERM || 
               status == HttpURLConnection.HTTP_SEE_OTHER ||
               status == 307 || status == 308) {
            
            if (redirects > 10) throw new IOException("Too many redirects");
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            
            url = new URL(url, newUrl);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(false);
            status = conn.getResponseCode();
            redirects++;
        }
        
        if (status >= 400) {
            if (status == 404 && urlString.endsWith(".json")) {
                throw new FileNotFoundException("JSON File not found");
            }
            throw new IOException("HTTP " + status + " on " + url.getFile());
        }
        
        return conn;
    }

    private void saveUnifiedMetadata(MapEntry map, File worldDir) {
        try {
            File metaFile = new File(worldDir, "zombierool_map.json");
            JsonObject json = new JsonObject();

            if (metaFile.exists()) {
                try (FileReader reader = new FileReader(metaFile)) {
                    json = new Gson().fromJson(reader, JsonObject.class);
                    if (json == null) json = new JsonObject();
                } catch (Exception ignored) {}
            }

            json.addProperty("id", map.id);
            json.addProperty("version", map.version != null ? map.version : "1.0.0");
            json.addProperty("sha256", map.sha256 != null ? map.sha256 : "");
            
            if (map.hasResourcePack()) {
                json.addProperty("resource_pack_url", map.resourcePackUrl);
                json.addProperty("resource_pack_name", map.resourcePackName != null ? map.resourcePackName : map.name);
            }

            try (FileWriter writer = new FileWriter(metaFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception e) {}
    }

    @Override
    protected void init() {
        int tabY = 25;
        this.officialTabButton = Button.builder(Component.translatable("gui.zombierool.downloader.official"), btn -> {
            playSound();
            switchTab(true);
        }).bounds(20, tabY, 120, 20).build();

        this.communityTabButton = Button.builder(Component.translatable("gui.zombierool.downloader.community"), btn -> {
            playSound();
            switchTab(false);
        }).bounds(145, tabY, 120, 20).build();

        this.addRenderableWidget(officialTabButton);
        this.addRenderableWidget(communityTabButton);

        int filterY = 50;
        this.sortButton = Button.builder(Component.translatable(sortKeys[currentSortIndex]), btn -> {
            playSound();
            currentSortIndex = (currentSortIndex + 1) % sortKeys.length;
            btn.setMessage(Component.translatable(sortKeys[currentSortIndex]));
            filterMaps();
        }).bounds(20, filterY, 120, 20).build();
        this.addRenderableWidget(this.sortButton);

        this.searchBox = new EditBox(this.font, 145, filterY, 150, 20, Component.translatable("gui.zombierool.downloader.search"));
        this.searchBox.setResponder(query -> filterMaps());
        this.addRenderableWidget(this.searchBox);

        int featuredHeight = 80;
        int listTop = 80 + featuredHeight + 5;
        
        int buttonsPerRow = (this.width > 450) ? 4 : 2;
        int spacing = 5;
        int buttonWidth = Math.min(120, (this.width - 20) / buttonsPerRow - spacing);
        int buttonHeight = 20;
        int rows = (int) Math.ceil(4.0 / buttonsPerRow);
        int totalButtonAreaHeight = rows * (buttonHeight + spacing);
        
        int reservedBottomSpace = totalButtonAreaHeight + 50; 
        int listHeight = this.height - listTop - reservedBottomSpace; 

        this.mapList = new MapList(this.minecraft, this.width, listHeight, listTop, listTop + listHeight, 80);
        this.addWidget(this.mapList);

        int startX = (this.width - (buttonsPerRow * buttonWidth + (buttonsPerRow - 1) * spacing)) / 2;
        int startY = this.height - totalButtonAreaHeight - 5; 

        this.downloadAllButton = Button.builder(Component.translatable("gui.zombierool.downloader.update_all"), btn -> {
            playSound();
            downloadAllMaps();
        }).bounds(startX, startY, buttonWidth, buttonHeight).build();

        this.openGithubButton = Button.builder(Component.translatable("gui.zombierool.downloader.github"), btn -> {
            playSound();
            Util.getPlatform().openUri(GITHUB_OFFICIAL_URL);
        }).bounds(startX + buttonWidth + spacing, startY, buttonWidth, buttonHeight).build();

        this.publishButton = Button.builder(Component.translatable("gui.zombierool.downloader.publish"), btn -> {
            playSound();
            Util.getPlatform().openUri(PUBLISH_APP_URL);
        }).bounds(startX + (buttonWidth + spacing) * 2, startY, buttonWidth, buttonHeight).build();

        this.backButton = Button.builder(Component.translatable("gui.zombierool.downloader.back"), btn -> {
            playSound();
            this.minecraft.setScreen(lastScreen);
        }).bounds(startX + (buttonWidth + spacing) * 3, startY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(downloadAllButton);
        this.addRenderableWidget(openGithubButton);
        this.addRenderableWidget(publishButton);
        this.addRenderableWidget(backButton);

        loadFeaturedData();
        switchTab(isOfficialTab);
    }

    private void switchTab(boolean official) {
        this.isOfficialTab = official;
        this.loading = true;
        this.errorMessage = null;
        this.allLoadedMaps.clear();
        this.filteredMaps.clear();
        this.featuredMap = null;
        this.searchBox.setValue("");
        if (this.mapList != null) this.mapList.refreshList();
        updateTabStates();
        loadMapsFromUrl(isOfficialTab ? OFFICIAL_JSON_URL : COMMUNITY_JSON_URL);
    }

    private void updateTabStates() {
        officialTabButton.active = !isOfficialTab;
        communityTabButton.active = isOfficialTab;
        openGithubButton.visible = isOfficialTab;
        publishButton.visible = !isOfficialTab;

        int listTop = 80 + (featuredMap != null ? 80 : 0) + 5;
        int buttonsPerRow = (this.width > 450) ? 4 : 2;
        int totalButtonAreaHeight = (int) Math.ceil(3.0 / buttonsPerRow) * 25;
        int listHeight = this.height - listTop - totalButtonAreaHeight - 50;

        if (mapList != null) mapList.updatePosition(listTop, listTop + listHeight);
        updateButtonStates();
    }

    private void loadFeaturedData() {
        new Thread(() -> {
            try {
                HttpURLConnection conn = openConnectionWithRedirects(FEATURED_JSON_URL);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                conn.disconnect();

                JsonObject json = new Gson().fromJson(response.toString(), JsonObject.class);
                if (json.has("official")) officialFeaturedId = json.get("official").getAsString();
                if (json.has("community")) communityFeaturedId = json.get("community").getAsString();
                
                loadingFeatured = false;
                this.minecraft.execute(this::resolveFeaturedMap);
            } catch (Exception e) {
                loadingFeatured = false;
            }
        }).start();
    }

    private void loadMapsFromUrl(String jsonUrl) {
        new Thread(() -> {
            try {
                System.setProperty("java.net.preferIPv4Stack", "true");
                HttpURLConnection conn = openConnectionWithRedirects(jsonUrl);
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                conn.disconnect();

                JsonObject json = new Gson().fromJson(response.toString(), JsonObject.class);
                JsonArray mapsArray = json.getAsJsonArray("maps");

                allLoadedMaps.clear();
                for (int i = 0; i < mapsArray.size(); i++) {
                    JsonObject mapObj = mapsArray.get(i).getAsJsonObject();

                    String resourcePackUrl = null;
                    String resourcePackName = null;
                    if (mapObj.has("resource_pack")) {
                        JsonObject rpObj = mapObj.getAsJsonObject("resource_pack");
                        if (rpObj.has("url")) resourcePackUrl = rpObj.get("url").getAsString();
                        if (rpObj.has("name")) resourcePackName = rpObj.get("name").getAsString();
                    }

                    float avgStars = 0.0f;
                    int reviewsCount = 0;
                    if (mapObj.has("reviews")) {
                        JsonArray revArr = mapObj.getAsJsonArray("reviews");
                        reviewsCount = revArr.size();
                        if (reviewsCount > 0) {
                            float sum = 0;
                            for (int j = 0; j < reviewsCount; j++) sum += revArr.get(j).getAsJsonObject().get("stars").getAsFloat();
                            avgStars = sum / reviewsCount;
                        }
                    }

                    List<String> tags = new ArrayList<>();
                    if (mapObj.has("tags")) {
                        JsonArray tArr = mapObj.getAsJsonArray("tags");
                        for(int t=0; t<tArr.size(); t++) tags.add(tArr.get(t).getAsString().toLowerCase());
                    }

                    String gameVer = mapObj.has("game_version") ? mapObj.get("game_version").getAsString() : "Unknown";
                    String zrVer = mapObj.has("zr_version") ? mapObj.get("zr_version").getAsString() : "Unknown";
                    String version = mapObj.has("version") ? mapObj.get("version").getAsString() : "1.0.0";
                    
                    int downloads = mapObj.has("downloads") ? mapObj.get("downloads").getAsInt() : 0;
                    long timestamp = mapObj.has("timestamp") ? mapObj.get("timestamp").getAsLong() : 0L;

                    MapEntry entry = new MapEntry(
                        mapObj.get("id").getAsString(),
                        mapObj.get("name").getAsString(),
                        mapObj.has("description") ? mapObj.get("description").getAsString() : "",
                        mapObj.get("download_url").getAsString(),
                        resourcePackUrl, resourcePackName,
                        mapObj.has("sha256") ? mapObj.get("sha256").getAsString() : null,
                        mapObj.has("author") ? mapObj.get("author").getAsString() : "Official",
                        mapObj.has("image_url") ? mapObj.get("image_url").getAsString() : null,
                        tags, avgStars, reviewsCount,
                        gameVer, zrVer, version, downloads, timestamp
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
                this.minecraft.execute(() -> {
                    allLoadedMaps.clear();
                    filterMaps();
                    checkDownloadedMaps();
                });
            } catch (Exception e) {
                errorMessage = "Failed to load maps: " + e.getMessage();
                loading = false;
                networkIssuesDetected = true;
            }
        }).start();
    }

    private void resolveFeaturedMap() {
        if (loading || loadingFeatured) return;
        String targetId = isOfficialTab ? officialFeaturedId : communityFeaturedId;
        featuredMap = null;
        if (targetId != null && !targetId.isEmpty()) {
            for (MapEntry m : allLoadedMaps) {
                if (m.id.equals(targetId)) {
                    featuredMap = m;
                    break;
                }
            }
        }
        updateTabStates();
    }

    private void filterMaps() {
        String query = searchBox.getValue().toLowerCase().trim();
        filteredMaps.clear();

        for (MapEntry m : allLoadedMaps) {
            boolean matchName = m.name.toLowerCase().contains(query);
            boolean matchAuthor = m.author.toLowerCase().contains(query);
            boolean matchTag = m.tags.stream().anyMatch(t -> t.contains(query));

            if (query.isEmpty() || matchName || matchAuthor || matchTag) {
                filteredMaps.add(m);
            }
        }

        filteredMaps.sort((a, b) -> {
            if (currentSortIndex == 1) { 
                return Integer.compare(b.downloads, a.downloads);
            } else if (currentSortIndex == 2) { 
                return Long.compare(b.timestamp, a.timestamp);
            } else { 
                return Float.compare(b.avgStars, a.avgStars);
            }
        });

        if (mapList != null) mapList.refreshList();
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
                        DynamicTexture texture = new DynamicTexture(image);
                        ResourceLocation loc = this.minecraft.getTextureManager().register("zr_map_" + entry.id, texture);
                        imageCache.put(entry.id, texture);
                        resourceLocationCache.put(entry.id, loc);
                    });
                }
                conn.disconnect();
            } catch (Exception e) {}
        }).start();
    }

    private void checkDownloadedMaps() {
        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        Gson gson = new Gson();

        for (MapEntry map : allLoadedMaps) checkStateForMap(map, savesDir, gson);
        if (featuredMap != null) {
            checkStateForMap(featuredMap, savesDir, gson);
        }

        updateButtonStates();
    }

    private void checkStateForMap(MapEntry mapObj, File savesDir, Gson gson) {
        File mapDir = new File(savesDir, mapObj.name);
        MapInstallState state = MapInstallState.NOT_INSTALLED;

        if (mapDir.exists() && mapDir.isDirectory()) {
            File newVFile = new File(mapDir, "zombierool_map.json");
            if (newVFile.exists()) {
                try (FileReader reader = new FileReader(newVFile)) {
                    JsonObject vJson = gson.fromJson(reader, JsonObject.class);
                    String localSha = vJson.has("sha256") ? vJson.get("sha256").getAsString() : "";
                    String localVersion = vJson.has("version") ? vJson.get("version").getAsString() : "";

                    boolean hashDiffers = mapObj.sha256 != null && !mapObj.sha256.isEmpty() && !localSha.equalsIgnoreCase(mapObj.sha256);
                    boolean versionDiffers = mapObj.version != null && !mapObj.version.isEmpty() && !localVersion.equalsIgnoreCase(mapObj.version);

                    if (hashDiffers || versionDiffers) {
                        state = MapInstallState.UPDATE_AVAILABLE;
                    } else {
                        state = MapInstallState.INSTALLED;
                    }
                } catch (Exception e) { state = MapInstallState.UPDATE_AVAILABLE; }
            } else {
                state = MapInstallState.UPDATE_AVAILABLE;
            }
        }
        mapObj.state = state;
    }

    public void downloadMap(MapEntry map, boolean includeResourcePack) {
        if (isDownloading) return;
        new Thread(() -> {
            try {
                downloadMapInternal(map, includeResourcePack);
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
        List<MapEntry> mapsToDownload = new ArrayList<>();
        for (MapEntry map : filteredMaps) {
            if (map.state == MapInstallState.UPDATE_AVAILABLE) mapsToDownload.add(map);
        }

        if (mapsToDownload.isEmpty()) {
            this.minecraft.execute(() -> statusMessage = "All maps are up to date!");
            return;
        }

        CompletableFuture<Void> downloadChain = CompletableFuture.completedFuture(null);
        for (MapEntry map : mapsToDownload) {
            downloadChain = downloadChain.thenCompose(v -> {
                CompletableFuture<Void> future = new CompletableFuture<>();
                new Thread(() -> {
                    try {
                        downloadMapInternal(map, map.hasResourcePack());
                    } catch (Exception e) {
                        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                        this.minecraft.execute(() -> statusMessage = "Error on " + map.name + ": " + msg);
                    }
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
                    future.complete(null);
                }).start();
                return future;
            });
        }
        
        downloadChain.thenRun(() -> {
            this.minecraft.execute(() -> {
                isDownloading = false;
                statusMessage = "§aAll updates completed!";
                updateButtonStates();
            });
        });
    }

    private void downloadMapInternal(MapEntry map, boolean includeResourcePack) throws Exception {
        this.minecraft.execute(() -> {
            isDownloading = true;
            downloadProgress = 0.0f;
            statusMessage = "Downloading " + map.name + "...";
            updateButtonStates();
        });

        downloadFile(map.downloadUrl, map.name, true, map.name + ".zip", map.sha256, map);

        if (includeResourcePack && map.hasResourcePack()) {
            this.minecraft.execute(() -> { statusMessage = "Downloading resource pack..."; downloadProgress = 0.0f; });
            String rpName = map.resourcePackName != null ? map.resourcePackName : map.name;
            String resourcePackFileName = rpName + ".zip";
            downloadFile(map.resourcePackUrl, rpName, false, resourcePackFileName, null, null);
            this.minecraft.execute(() -> { try { Minecraft.getInstance().getResourcePackRepository().reload(); } catch (Exception ignored) {} });
        }

        // Contact The API with a safe GET request to increment downloads
        recordDownloadOnServer(map.id);

        this.minecraft.execute(() -> {
            checkDownloadedMaps();
            saveUnifiedMetadata(map, new File(new File(Minecraft.getInstance().gameDirectory, "saves"), map.name));
            statusMessage = "§aSuccessfully installed: " + map.name;
            downloadProgress = 0.0f;
            isDownloading = false;
            updateButtonStates();
        });
    }

    private void recordDownloadOnServer(String mapId) {
        new Thread(() -> {
            try {
                URL url = new URL(PUBLISH_APP_URL + "/api/map/" + mapId + "/download");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("User-Agent", "ZombieRool-Client/1.0");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.getResponseCode(); // This triggers the request
                conn.disconnect();
            } catch (Exception ignored) {}
        }).start();
    }

    private void downloadFile(String fileUrl, String name, boolean isMap, String fileName, String sha256Hash, MapEntry mapRef) throws Exception {
        System.setProperty("java.net.preferIPv4Stack", "true");
        
        HttpURLConnection conn = openConnectionWithRedirects(fileUrl);
        
        File tempDir = new File(Minecraft.getInstance().gameDirectory, "temp_downloads");
        tempDir.mkdirs();
        File zipFile = new File(tempDir, fileName);

        long contentLength = conn.getContentLengthLong();
        if (contentLength > MAX_FILE_SIZE) {
            conn.disconnect();
            throw new Exception("File exceeds maximum allowed size");
        }

        try (InputStream in = conn.getInputStream(); FileOutputStream out = new FileOutputStream(zipFile)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalRead = 0;
            long lastUpdate = System.currentTimeMillis();

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                if (totalRead > MAX_FILE_SIZE) throw new IOException("Limit exceeded during download");

                long now = System.currentTimeMillis();
                if (contentLength > 0 && now - lastUpdate > 100) {
                    final float progress = (float) totalRead / contentLength;
                    this.minecraft.execute(() -> downloadProgress = progress);
                    lastUpdate = now;
                }
            }
        }
        conn.disconnect();

        if (isMap) {
            this.minecraft.execute(() -> statusMessage = "Installing " + name + "...");
            File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
            File targetDir = new File(savesDir, name);

            if (sha256Hash != null && !sha256Hash.isEmpty()) {
                this.minecraft.execute(() -> statusMessage = "Verifying integrity...");
                String actualHash = calculateSHA256(zipFile);
                if (actualHash != null && !actualHash.equalsIgnoreCase(sha256Hash)) {
                    zipFile.delete();
                    throw new Exception("Hash mismatch (Integrity Check Failed)");
                }
            }

            if (targetDir.exists()) deleteDirectory(targetDir);
            targetDir.mkdirs();

            extractZip(zipFile, targetDir);
            zipFile.delete();
        } else {
            this.minecraft.execute(() -> statusMessage = "Installing RP...");
            File resourcePacksDir = new File(Minecraft.getInstance().gameDirectory, "resourcepacks");
            resourcePacksDir.mkdirs();
            File targetFile = new File(resourcePacksDir, fileName);
            if (targetFile.exists()) targetFile.delete();
            java.nio.file.Files.move(zipFile.toPath(), targetFile.toPath());
        }
    }

    private void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] allContents = dir.listFiles();
        if (allContents != null) for (File file : allContents) deleteDirectory(file);
        dir.delete();
    }

    private String calculateSHA256(File file) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = fis.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) { return null; }
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                String canonicalDestPath = destDir.getCanonicalPath();
                if (!file.getCanonicalPath().startsWith(canonicalDestPath + File.separator)) throw new IOException("Zip Slip detected");

                if (entry.isDirectory()) file.mkdirs();
                else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) fos.write(buffer, 0, len);
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void updateButtonStates() {
        if (downloadAllButton != null) {
            downloadAllButton.active = !isDownloading && filteredMaps.stream().anyMatch(m -> m.state == MapInstallState.UPDATE_AVAILABLE);
        }
        if (backButton != null) backButton.active = !isDownloading;
        if (officialTabButton != null) officialTabButton.active = !isOfficialTab && !isDownloading;
        if (communityTabButton != null) communityTabButton.active = isOfficialTab && !isDownloading;
        if (sortButton != null) sortButton.active = !isDownloading;
    }

    private void playSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= discordLinkX && mouseX <= discordLinkX + discordLinkWidth && mouseY >= discordLinkY && mouseY <= discordLinkY + 9) {
            playSound();
            Util.getPlatform().openUri(DISCORD_INVITE);
            return true;
        }

        if (featuredMap != null && !loadingFeatured) {
            int featuredWidth = Math.min(600, this.width - 40);
            int featuredX = this.width / 2 - featuredWidth / 2;
            int featuredY = 80; 

            int btnWidth = 100;
            int btnHeight = 20;
            int btnX = featuredX + featuredWidth - btnWidth - 10;
            int btnY = featuredY + 80 - btnHeight - 10;

            if (mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight && !isDownloading) {
                playSound();
                if (featuredMap.state == MapInstallState.INSTALLED || featuredMap.state == MapInstallState.UPDATE_AVAILABLE) {
                    this.minecraft.setScreen(new OverwriteConfirmScreen(this, featuredMap));
                } else if (featuredMap.hasResourcePack()) {
                    this.minecraft.setScreen(new ResourcePackConfirmScreen(this, featuredMap));
                } else {
                    downloadMap(featuredMap, false);
                }
                return true;
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.searchBox.isFocused()) {
            if (keyCode == 256) { this.onClose(); return true; }
            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        int featuredHeight = 80;
        if (!loadingFeatured && featuredMap != null) {
            int featuredWidth = Math.min(600, this.width - 40);
            int featuredX = this.width / 2 - featuredWidth / 2;
            int featuredY = 80; 

            graphics.fill(featuredX, featuredY, featuredX + featuredWidth, featuredY + featuredHeight, 0xBB000000);
            graphics.renderOutline(featuredX, featuredY, featuredWidth, featuredHeight, 0xFFFFAA00);

            ResourceLocation imgLoc = resourceLocationCache.get(featuredMap.id);
            if (imgLoc != null) {
                int imgW = 120;
                int imgH = 68;
                RenderSystem.enableBlend();
                graphics.blit(imgLoc, featuredX + 6, featuredY + (featuredHeight - imgH) / 2, 0, 0, imgW, imgH, imgW, imgH);
                RenderSystem.disableBlend();
            } else {
                int imgW = 120;
                int imgH = 68;
                graphics.fill(featuredX + 6, featuredY + (featuredHeight - imgH) / 2, featuredX + 6 + imgW, featuredY + (featuredHeight - imgH) / 2 + imgH, 0xFF222222);
                graphics.drawCenteredString(font, "No Image", featuredX + 6 + imgW / 2, featuredY + featuredHeight / 2 - 4, 0xFF555555);
            }

            int textX = featuredX + 135;
            int textY = featuredY + 8;
            graphics.drawString(this.font, Component.translatable("gui.zombierool.downloader.featured"), textX, textY, 0xFFFFAA00);

            String displayName = featuredMap.name;
            if (featuredMap.state == MapInstallState.UPDATE_AVAILABLE) displayName += Component.translatable("gui.zombierool.downloader.status.update").getString();
            else if (featuredMap.state == MapInstallState.INSTALLED) displayName += Component.translatable("gui.zombierool.downloader.status.installed").getString();
            
            int color = featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? 0xFFFFAA00 : 
                       (featuredMap.state == MapInstallState.INSTALLED ? 0xFF00FF00 : 0xFFFFFFFF);

            String trimmedName = this.font.plainSubstrByWidth(displayName, featuredWidth - 135 - 110);
            if (!trimmedName.equals(displayName)) trimmedName += "...";
            graphics.drawString(this.font, trimmedName, textX, textY + 14, color);

            String desc = featuredMap.description;
            List<String> wrappedDesc = wrapText(desc, featuredWidth - 135 - 110);
            int descY = textY + 30;
            for (int i = 0; i < Math.min(2, wrappedDesc.size()); i++) {
                graphics.drawString(this.font, wrappedDesc.get(i), textX, descY + (i * 10), 0xFFAAAAAA);
            }

            int btnWidth = 100;
            int btnHeight = 20;
            int btnX = featuredX + featuredWidth - btnWidth - 10;
            int btnY = featuredY + featuredHeight - btnHeight - 10;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight;
            
            int btnColor;
            if (isDownloading) {
                btnColor = 0xFF555555;
            } else if (featuredMap.state == MapInstallState.NOT_INSTALLED) {
                btnColor = btnHover ? 0xFF16A34A : 0xFF22C55E; 
            } else if (featuredMap.state == MapInstallState.UPDATE_AVAILABLE) {
                btnColor = btnHover ? 0xFFD97706 : 0xFFF59E0B; 
            } else {
                btnColor = btnHover ? 0xFF4B5563 : 0xFF6B7280; 
            }
            
            graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, btnColor);
            String btnText;
            if (featuredMap.state == MapInstallState.NOT_INSTALLED) btnText = "Download";
            else if (featuredMap.state == MapInstallState.UPDATE_AVAILABLE) btnText = "Update";
            else btnText = "Reinstall";
            graphics.drawCenteredString(font, btnText, btnX + btnWidth / 2, btnY + 6, 0xFFFFFFFF);
        }

        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.downloader.loading"), this.width / 2, this.height / 2, 0xAAAAAA);
        } else if (errorMessage != null) {
            graphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2 - 10, 0xFF5555);
        } else if (mapList != null) {
            this.mapList.render(graphics, mouseX, mouseY, partialTick);
        }

        int buttonsPerRow = (this.width > 450) ? 4 : 2;
        int rows = (int) Math.ceil(4.0 / buttonsPerRow);
        int totalButtonAreaHeight = rows * 25; 
        int startY = this.height - totalButtonAreaHeight - 5; 

        int currentTextY = startY - 15; 
        
        if (statusMessage != null) {
            int statusColor = statusMessage.startsWith("§a") ? 0xFFFFFF : (isDownloading ? 0xFFAA00 : 0xFF5555);
            graphics.drawCenteredString(this.font, statusMessage, this.width / 2, currentTextY, statusColor);
            currentTextY -= 15;
        }

        if (isDownloading && downloadProgress > 0) {
            int barWidth = 200;
            int barHeight = 8;
            int barX = (this.width - barWidth) / 2;
            int barY = currentTextY - 2;
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
            graphics.fill(barX, barY, barX + (int)(barWidth * downloadProgress), barY + barHeight, 0xFF00FF00);
            graphics.renderOutline(barX, barY, barWidth, barHeight, 0xFFFFFFFF);
            currentTextY -= 15;
        }

        discordLinkWidth = this.font.width(DISCORD_MESSAGE);
        discordLinkX = (this.width - discordLinkWidth) / 2;
        discordLinkY = currentTextY;
        boolean hovering = mouseX >= discordLinkX && mouseX <= discordLinkX + discordLinkWidth && mouseY >= discordLinkY && mouseY <= discordLinkY + 9;
        int dcColor = hovering ? 0x5555FF : 0xAAAAAA;
        graphics.drawCenteredString(this.font, DISCORD_MESSAGE, this.width / 2, discordLinkY, dcColor);
        if (hovering) graphics.fill(discordLinkX, discordLinkY + 9, discordLinkX + discordLinkWidth, discordLinkY + 10, 0xFF5555FF);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder currentLine = new StringBuilder();
        for (String word : words) {
            String testLine = currentLine.length() == 0 ? word : currentLine + " " + word;
            if (this.font.width(testLine) <= maxWidth) {
                if (currentLine.length() > 0) currentLine.append(" ");
                currentLine.append(word);
            } else {
                if (currentLine.length() > 0) { lines.add(currentLine.toString()); currentLine = new StringBuilder(word); } 
                else lines.add(word);
            }
        }
        if (currentLine.length() > 0) lines.add(currentLine.toString());
        return lines;
    }

    @Override
    public void removed() {
        super.removed();
        for (DynamicTexture t : imageCache.values()) t.close();
        imageCache.clear();
        resourceLocationCache.clear();
    }

    private class MapList extends ObjectSelectionList<MapEntry> {
        public MapList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            refreshList();
        }

        public void refreshList() {
            this.clearEntries();
            for (MapEntry map : filteredMaps) {
                this.addEntry(map);
            }
        }

        public void updatePosition(int top, int bottom) {
            this.y0 = top; this.y1 = bottom;
        }

        @Override public void setSelected(MapEntry entry) { super.setSelected(entry); updateButtonStates(); }
        @Override public int getRowWidth() { return Math.min(600, this.width - 40); }
        @Override protected int getScrollbarPosition() { return this.width / 2 + getRowWidth() / 2 + 5; }
    }

    private class MapEntry extends ObjectSelectionList.Entry<MapEntry> {
        public final String id, name, description, downloadUrl, resourcePackUrl, resourcePackName, sha256, author, imageUrl;
        public final List<String> tags;
        public final float avgStars;
        public final int reviewsCount;
        public final String gameVersion, zrVersion, version;
        public final int downloads;
        public final long timestamp;
        public MapInstallState state = MapInstallState.NOT_INSTALLED;

        public MapEntry(String id, String name, String description, String downloadUrl, 
                        String resourcePackUrl, String resourcePackName, String sha256,
                        String author, String imageUrl, List<String> tags, float avgStars, int reviewsCount,
                        String gameVersion, String zrVersion, String version, int downloads, long timestamp) {
            this.id = id; this.name = name; this.description = description; this.downloadUrl = downloadUrl;
            this.resourcePackUrl = resourcePackUrl; this.resourcePackName = resourcePackName; this.sha256 = sha256;
            this.author = author; this.imageUrl = imageUrl; this.tags = tags;
            this.avgStars = avgStars; this.reviewsCount = reviewsCount;
            this.gameVersion = gameVersion; this.zrVersion = zrVersion; this.version = version;
            this.downloads = downloads; this.timestamp = timestamp;
        }

        public boolean hasResourcePack() { return resourcePackUrl != null && !resourcePackUrl.isEmpty(); }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, 
                           int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean isSelected = mapList.getSelected() == this;
            graphics.fill(left, top, left + width, top + height - 4, isSelected ? 0x66AAAAAA : 0x55000000);
            graphics.renderOutline(left, top, width, height - 4, isSelected ? 0xFFFFAA00 : 0xFF444444);

            int textOffset = left + 10;

            ResourceLocation imgLoc = resourceLocationCache.get(id);
            int imgW = 120;
            int imgH = 68;
            if (imgLoc != null) {
                RenderSystem.enableBlend();
                graphics.blit(imgLoc, left + 4, top + (height - 4 - imgH) / 2, 0, 0, imgW, imgH, imgW, imgH);
                RenderSystem.disableBlend();
            } else {
                graphics.fill(left + 4, top + (height - 4 - imgH) / 2, left + 4 + imgW, top + (height - 4 - imgH) / 2 + imgH, 0xFF222222);
                graphics.drawCenteredString(font, "No Image", left + 4 + imgW / 2, top + height / 2 - 8, 0xFF555555);
            }
            textOffset = left + 4 + imgW + 10;

            int color = 0xFFFFFF;
            String status = "";
            if (state == MapInstallState.INSTALLED) { color = 0x00FF00; status = " " + Component.translatable("gui.zombierool.downloader.status.installed").getString(); } 
            else if (state == MapInstallState.UPDATE_AVAILABLE) { color = 0xFFFFAA00; status = " " + Component.translatable("gui.zombierool.downloader.status.update").getString(); }

            String rpIndicator = hasResourcePack() ? " [RP]" : "";
            String fullTitle = name + " v" + version + " (By " + author + ")" + status + rpIndicator;
            graphics.drawString(font, font.plainSubstrByWidth(fullTitle, width - (textOffset - left) - 80), textOffset, top + 6, color);
            
            String reviewText = reviewsCount > 0 ? String.format("%.1f ★ (%d)", avgStars, reviewsCount) : "No ratings";
            reviewText += String.format(" | ⬇ %d", downloads);
            graphics.drawString(font, reviewText, left + width - font.width(reviewText) - 10, top + 6, 0xFFFF00);

            String trimmedDesc = font.plainSubstrByWidth(description, width - (textOffset - left) - 10);
            graphics.drawString(font, trimmedDesc, textOffset, top + 22, 0xAAAAAA);

            int metaY = top + height - 20;
            graphics.drawString(font, "MC: " + gameVersion + " | ZR: " + zrVersion, textOffset, metaY, 0x777777);
            
            if (!tags.isEmpty()) {
                String tagStr = String.join(", ", tags);
                graphics.drawString(font, "Tags: " + font.plainSubstrByWidth(tagStr, width - (textOffset - left) - 120), textOffset + 110, metaY, 0x5555FF);
            }

            int btnWidth = 80;
            int btnHeight = 20;
            int btnX = left + width - btnWidth - 10;
            int btnY = top + height - btnHeight - 12;
            boolean btnHover = mouseX >= btnX && mouseX <= btnX + btnWidth && mouseY >= btnY && mouseY <= btnY + btnHeight;
            
            int btnColor;
            if (isDownloading) {
                btnColor = 0xFF555555;
            } else if (state == MapInstallState.NOT_INSTALLED) {
                btnColor = btnHover ? 0xFF16A34A : 0xFF22C55E; 
            } else if (state == MapInstallState.UPDATE_AVAILABLE) {
                btnColor = btnHover ? 0xFFD97706 : 0xFFF59E0B; 
            } else {
                btnColor = btnHover ? 0xFF4B5563 : 0xFF6B7280; 
            }
            
            graphics.fill(btnX, btnY, btnX + btnWidth, btnY + btnHeight, btnColor);
            String btnText;
            if (state == MapInstallState.NOT_INSTALLED) btnText = "Download";
            else if (state == MapInstallState.UPDATE_AVAILABLE) btnText = "Update";
            else btnText = "Reinstall";
            graphics.drawCenteredString(font, btnText, btnX + btnWidth / 2, btnY + 6, 0xFFFFFFFF);
        }

        @Override public Component getNarration() { return Component.literal(name); }

        @Override 
        public boolean mouseClicked(double mouseX, double mouseY, int button) { 
            mapList.setSelected(this); 
            int btnWidth = 80;
            int btnHeight = 20;
            int listWidth = mapList.getRowWidth();
            int left = MapDownloaderScreen.this.width / 2 - listWidth / 2; 
            int btnX = left + listWidth - btnWidth - 10;
            if (mouseX >= btnX && mouseX <= btnX + btnWidth && !isDownloading) {
                playSound();
                if (state == MapInstallState.INSTALLED || state == MapInstallState.UPDATE_AVAILABLE) {
                    minecraft.setScreen(new OverwriteConfirmScreen(MapDownloaderScreen.this, this));
                } else if (hasResourcePack()) {
                    minecraft.setScreen(new ResourcePackConfirmScreen(MapDownloaderScreen.this, this));
                } else {
                    downloadMap(this, false);
                }
            }
            return true; 
        }
    }

    private class OverwriteConfirmScreen extends Screen {
        private final MapDownloaderScreen parent; private final MapEntry map;
        protected OverwriteConfirmScreen(MapDownloaderScreen parent, MapEntry map) { super(Component.translatable("gui.zombierool.overwrite.title")); this.parent = parent; this.map = map; }
        @Override protected void init() {
            int startX = (this.width - 210) / 2; int buttonY = this.height / 2 + 30;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.overwrite.yes"), btn -> { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)); if (map.hasResourcePack()) minecraft.setScreen(new ResourcePackConfirmScreen(parent, map)); else { minecraft.setScreen(parent); parent.downloadMap(map, false); } }).bounds(startX, buttonY, 100, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.zombierool.overwrite.cancel"), btn -> { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)); minecraft.setScreen(parent); }).bounds(startX + 110, buttonY, 100, 20).build());
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float pt) {
            this.renderBackground(graphics);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFF5555);
            graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.overwrite.warning1"), this.width / 2, this.height / 2 - 10, 0xAAAAAA);
            super.render(graphics, mx, my, pt);
        }
    }

    private class ResourcePackConfirmScreen extends Screen {
        private final MapDownloaderScreen parent; private final MapEntry map;
        protected ResourcePackConfirmScreen(MapDownloaderScreen parent, MapEntry map) { super(Component.translatable("gui.zombierool.rp_confirm.title")); this.parent = parent; this.map = map; }
        @Override protected void init() {
            int startX = (this.width - 210) / 2; int buttonY = this.height / 2 + 30;
            this.addRenderableWidget(Button.builder(Component.translatable("gui.yes"), btn -> { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)); minecraft.setScreen(parent); parent.downloadMap(map, true); }).bounds(startX, buttonY, 100, 20).build());
            this.addRenderableWidget(Button.builder(Component.translatable("gui.no"), btn -> { minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)); minecraft.setScreen(parent); parent.downloadMap(map, false); }).bounds(startX + 110, buttonY, 100, 20).build());
        }
        @Override public void render(GuiGraphics graphics, int mx, int my, float pt) {
            this.renderBackground(graphics);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFAA00);
            graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.rp_confirm.desc1"), this.width / 2, this.height / 2 - 10, 0xFFFFFF);
            super.render(graphics, mx, my, pt);
        }
    }
}