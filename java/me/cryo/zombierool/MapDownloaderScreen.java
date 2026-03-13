package me.cryo.zombierool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.concurrent.*;

public class MapDownloaderScreen extends Screen {
    private static final String MAPS_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/maps.json";
    private static final String FEATURED_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/featured.json";
    private static final String GITHUB_MAPS_URL = "https://github.com/Cryo60/zombierool-maps/tree/main";
    private static final String DISCORD_MESSAGE = "Join our Discord for community-made maps!";
    private static final String DISCORD_INVITE = "https://discord.gg/wK5z899M75";
    
    private static final long MAX_FILE_SIZE = 500L * 1024L * 1024L;
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;
    private static final int DOWNLOAD_TIMEOUT = 300000;

    private final Screen lastScreen;
    private MapList mapList;
    
    private Button officialTabButton;
    private Button communityTabButton;
    private Button downloadButton;
    private Button downloadAllButton;
    private Button openGithubButton;
    private Button backButton;
    private Button downloadFeaturedButton;
    
    private List<MapEntry> maps = new ArrayList<>();
    private FeaturedMap featuredMap = null;
    
    private DynamicTexture featuredTexture = null;
    private ResourceLocation featuredTextureLocation = null;

    private boolean loading = true;
    private boolean loadingFeatured = true;
    private String errorMessage = null;
    private String statusMessage = null;
    private float downloadProgress = 0.0f;
    private boolean isDownloading = false;
    private int consecutiveFailures = 0;
    private boolean networkIssuesDetected = false;

    private int discordLinkX;
    private int discordLinkY;
    private int discordLinkWidth;
    
    private boolean isOfficialTab = true;

    public enum MapInstallState {
        NOT_INSTALLED,
        UPDATE_AVAILABLE,
        INSTALLED
    }

    public MapDownloaderScreen(Screen lastScreen) {
        super(Component.literal("Maps"));
        this.lastScreen = lastScreen;
    }

    // NOUVEAU SYSTEME UNIFIE : zombierool_map.json
    private void saveUnifiedMetadata(MapEntry map, File worldDir) {
        try {
            File metaFile = new File(worldDir, "zombierool_map.json");
            JsonObject json = new JsonObject();
            
            // On préserve les données existantes (ex: config du créateur pour le Spooky Mode)
            if (metaFile.exists()) {
                try (FileReader reader = new FileReader(metaFile)) {
                    json = new Gson().fromJson(reader, JsonObject.class);
                    if (json == null) json = new JsonObject();
                } catch (Exception ignored) {}
            }
            
            json.addProperty("id", map.id);
            json.addProperty("sha256", map.sha256 != null ? map.sha256 : "");
            
            if (map.hasResourcePack()) {
                json.addProperty("resource_pack_url", map.resourcePackUrl);
                json.addProperty("resource_pack_name", map.resourcePackName != null ? map.resourcePackName : map.name);
            }
            
            try (FileWriter writer = new FileWriter(metaFile)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error saving unified metadata: " + e.getMessage());
        }
    }

    @Override
    protected void init() {
        loadFeaturedMap();
        loadMapsFromGithub();

        int topMargin = 32;

        this.officialTabButton = Button.builder(Component.literal("Official Maps"), btn -> {
            playSound();
            isOfficialTab = true;
            updateTabStates();
        }).bounds(this.width / 2 - 105, topMargin, 100, 20).build();

        this.communityTabButton = Button.builder(Component.literal("Community Maps"), btn -> {
            playSound();
            isOfficialTab = false;
            updateTabStates();
        }).bounds(this.width / 2 + 5, topMargin, 100, 20).build();

        this.addRenderableWidget(officialTabButton);
        this.addRenderableWidget(communityTabButton);

        int featuredHeight = 120;
        int listTop = topMargin + 25 + featuredHeight + 10;
        
        int buttonsPerRow = (this.width > 450) ? 4 : 2;
        int spacing = 5;
        int buttonWidth = Math.min(120, (this.width - 20) / buttonsPerRow - spacing);
        int buttonHeight = 20;
        
        int rows = (int) Math.ceil(4.0 / buttonsPerRow);
        int totalButtonAreaHeight = rows * (buttonHeight + spacing);
        int listHeight = this.height - listTop - totalButtonAreaHeight - 40; 
        
        this.mapList = new MapList(this.minecraft, this.width, listHeight, listTop, listTop + listHeight, 36);
        this.addWidget(this.mapList);

        int startX = (this.width - (buttonsPerRow * buttonWidth + (buttonsPerRow - 1) * spacing)) / 2;
        int startY = this.height - totalButtonAreaHeight - 30;

        this.downloadButton = Button.builder(Component.literal("Download"), btn -> {
            playSound();
            downloadAndInstallMap();
        }).bounds(startX, startY, buttonWidth, buttonHeight).build();

        this.downloadAllButton = Button.builder(Component.literal("Update All"), btn -> {
            playSound();
            downloadAllMaps();
        }).bounds(startX + buttonWidth + spacing, startY, buttonWidth, buttonHeight).build();

        int row2Y = (buttonsPerRow == 2) ? startY + buttonHeight + spacing : startY;
        int row2X = (buttonsPerRow == 2) ? startX : startX + (buttonWidth + spacing) * 2;

        this.openGithubButton = Button.builder(Component.literal("Open GitHub"), btn -> {
            playSound();
            Util.getPlatform().openUri(GITHUB_MAPS_URL);
        }).bounds(row2X, row2Y, buttonWidth, buttonHeight).build();

        this.backButton = Button.builder(Component.literal("Back"), btn -> {
            playSound();
            this.minecraft.setScreen(lastScreen);
        }).bounds(row2X + buttonWidth + spacing, row2Y, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(downloadButton);
        this.addRenderableWidget(downloadAllButton);
        this.addRenderableWidget(openGithubButton);
        this.addRenderableWidget(backButton);

        if (featuredMap != null) {
            int featuredButtonWidth = 100;
            int featuredButtonX = this.width - featuredButtonWidth - 30;
            int featuredButtonY = topMargin + 25 + 40;
            this.downloadFeaturedButton = Button.builder(Component.literal("Download"), btn -> {
                playSound();
                downloadFeaturedMap();
            }).bounds(featuredButtonX, featuredButtonY, featuredButtonWidth, 20).build();
            this.addRenderableWidget(downloadFeaturedButton);
        }

        updateTabStates();
    }

    private void updateTabStates() {
        officialTabButton.active = !isOfficialTab;
        communityTabButton.active = isOfficialTab;
        downloadButton.visible = isOfficialTab;
        downloadAllButton.visible = isOfficialTab;
        openGithubButton.visible = isOfficialTab;
        if (downloadFeaturedButton != null) {
            downloadFeaturedButton.visible = isOfficialTab && featuredMap != null;
        }
        updateButtonStates();
    }

    private void loadFeaturedMap() {
        new Thread(() -> {
            try {
                System.setProperty("java.net.preferIPv4Stack", "true");
                URL url = new URL(FEATURED_JSON_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Gson gson = new Gson();
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);

                String id = json.get("id").getAsString();
                String name = json.get("name").getAsString();
                String description = json.get("description").getAsString();
                String downloadUrl = json.get("download_url").getAsString();
                String previewUrl = json.get("preview_url").getAsString();
                
                String resourcePackUrl = null;
                String resourcePackName = null;
                if (json.has("resource_pack")) {
                    JsonObject rpObj = json.getAsJsonObject("resource_pack");
                    if (rpObj.has("url")) resourcePackUrl = rpObj.get("url").getAsString();
                    if (rpObj.has("name")) resourcePackName = rpObj.get("name").getAsString();
                }

                String sha256 = json.has("sha256") ? json.get("sha256").getAsString() : null;

                featuredMap = new FeaturedMap(id, name, description, downloadUrl, 
                                             resourcePackUrl, resourcePackName, sha256, previewUrl);
                
                downloadFeaturedPreview(previewUrl);
                loadingFeatured = false;
                
                this.minecraft.execute(this::checkDownloadedMaps);

            } catch (Exception e) {
                loadingFeatured = false;
            }
        }).start();
    }

    private void downloadFeaturedPreview(String previewUrl) {
        try {
            URL url = new URL(previewUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                String newUrl = conn.getHeaderField("Location");
                conn.disconnect();
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.connect();
            }

            try (InputStream in = conn.getInputStream()) {
                NativeImage image = NativeImage.read(in);
                this.minecraft.execute(() -> {
                    featuredTexture = new DynamicTexture(image);
                    featuredTextureLocation = this.minecraft.getTextureManager()
                        .register("zombierool_featured", featuredTexture);
                });
            }
            conn.disconnect();
        } catch (Exception e) {}
    }

    private void downloadFeaturedMap() {
        if (featuredMap == null || isDownloading) return;
        MapEntry mapEntry = featuredMap.toMapEntry();
        
        if (featuredMap.state == MapInstallState.INSTALLED || featuredMap.state == MapInstallState.UPDATE_AVAILABLE) {
            this.minecraft.setScreen(new OverwriteConfirmScreen(this, mapEntry));
        } else if (mapEntry.hasResourcePack()) {
            this.minecraft.setScreen(new ResourcePackConfirmScreen(this, mapEntry));
        } else {
            downloadMap(mapEntry, false);
        }
    }

    private void playSound() {
        this.minecraft.getSoundManager().play(
            SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)
        );
    }

    private void loadMapsFromGithub() {
        new Thread(() -> {
            try {
                System.setProperty("java.net.preferIPv4Stack", "true");
                URL url = new URL(MAPS_JSON_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Gson gson = new Gson();
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                JsonArray mapsArray = json.getAsJsonArray("maps");

                maps.clear();
                for (int i = 0; i < mapsArray.size(); i++) {
                    JsonObject mapObj = mapsArray.get(i).getAsJsonObject();
                    String resourcePackUrl = null;
                    String resourcePackName = null;
                    if (mapObj.has("resource_pack")) {
                        JsonObject rpObj = mapObj.getAsJsonObject("resource_pack");
                        if (rpObj.has("url")) resourcePackUrl = rpObj.get("url").getAsString();
                        if (rpObj.has("name")) resourcePackName = rpObj.get("name").getAsString();
                    }

                    String sha256 = mapObj.has("sha256") ? mapObj.get("sha256").getAsString() : null;

                    MapEntry entry = new MapEntry(
                        mapObj.get("id").getAsString(),
                        mapObj.get("name").getAsString(),
                        mapObj.get("description").getAsString(),
                        mapObj.get("download_url").getAsString(),
                        resourcePackUrl,
                        resourcePackName,
                        sha256
                    );
                    maps.add(entry);
                }

                loading = false;
                this.minecraft.execute(() -> {
                    checkDownloadedMaps();
                    if (mapList != null) {
                        mapList.refreshList();
                        updateButtonStates();
                    }
                });

            } catch (Exception e) {
                errorMessage = "Failed to load maps. Click 'Open GitHub' to download manually.";
                loading = false;
                networkIssuesDetected = true;
            }
        }).start();
    }

    private void checkDownloadedMaps() {
        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        Gson gson = new Gson();

        for (MapEntry map : maps) {
            checkStateForMap(map, savesDir, gson);
        }
        if (featuredMap != null) {
            checkStateForMap(featuredMap, savesDir, gson);
            if (downloadFeaturedButton != null) {
                if (featuredMap.state == MapInstallState.NOT_INSTALLED) downloadFeaturedButton.setMessage(Component.literal("Download"));
                else if (featuredMap.state == MapInstallState.UPDATE_AVAILABLE) downloadFeaturedButton.setMessage(Component.literal("Update"));
                else downloadFeaturedButton.setMessage(Component.literal("Reinstall"));
            }
        }
        if (mapList != null) mapList.refreshList();
        updateButtonStates();
    }

    private void checkStateForMap(Object mapObj, File savesDir, Gson gson) {
        String name = mapObj instanceof MapEntry ? ((MapEntry)mapObj).name : ((FeaturedMap)mapObj).name;
        String sha256 = mapObj instanceof MapEntry ? ((MapEntry)mapObj).sha256 : ((FeaturedMap)mapObj).sha256;
        
        File mapDir = new File(savesDir, name);
        MapInstallState state = MapInstallState.NOT_INSTALLED;

        if (mapDir.exists() && mapDir.isDirectory()) {
            File newVFile = new File(mapDir, "zombierool_map.json");
            File oldVFile = new File(mapDir, "zombierool_version.json");
            File targetFile = newVFile.exists() ? newVFile : (oldVFile.exists() ? oldVFile : null);

            if (targetFile != null) {
                try (FileReader reader = new FileReader(targetFile)) {
                    JsonObject vJson = gson.fromJson(reader, JsonObject.class);
                    String localSha = vJson.has("sha256") ? vJson.get("sha256").getAsString() : "";
                    if (sha256 != null && !sha256.isEmpty() && !localSha.equalsIgnoreCase(sha256)) {
                        state = MapInstallState.UPDATE_AVAILABLE;
                    } else {
                        state = MapInstallState.INSTALLED;
                    }
                } catch (Exception e) {
                    state = MapInstallState.UPDATE_AVAILABLE;
                }
            } else {
                state = MapInstallState.UPDATE_AVAILABLE;
            }
        }
        
        if (mapObj instanceof MapEntry) ((MapEntry)mapObj).state = state;
        else ((FeaturedMap)mapObj).state = state;
    }

    private void downloadAndInstallMap() {
        MapEntry selected = mapList.getSelected();
        if (selected == null || isDownloading) return;

        if (selected.state == MapInstallState.INSTALLED || selected.state == MapInstallState.UPDATE_AVAILABLE) {
            this.minecraft.setScreen(new OverwriteConfirmScreen(this, selected));
        } else if (selected.hasResourcePack()) {
            this.minecraft.setScreen(new ResourcePackConfirmScreen(this, selected));
        } else {
            downloadMap(selected, false);
        }
    }

    private void downloadAllMaps() {
        if (isDownloading) return;
        
        List<MapEntry> mapsToDownload = new ArrayList<>();
        for (MapEntry map : maps) {
            if (map.state == MapInstallState.UPDATE_AVAILABLE) {
                mapsToDownload.add(map);
            }
        }
        
        if (mapsToDownload.isEmpty()) {
            this.minecraft.execute(() -> statusMessage = "All maps are up to date!");
            return;
        }

        CompletableFuture<Void> downloadChain = CompletableFuture.completedFuture(null);

        for (MapEntry map : mapsToDownload) {
            downloadChain = downloadChain.thenCompose(v -> {
                if (consecutiveFailures >= 2) {
                    this.minecraft.execute(() -> {
                        statusMessage = "Multiple failures detected. Use 'Open GitHub'.";
                        networkIssuesDetected = true;
                    });
                    return CompletableFuture.completedFuture(null);
                }
                
                CompletableFuture<Void> future = new CompletableFuture<>();
                new Thread(() -> {
                    downloadMapInternal(map, map.hasResourcePack());
                    try { Thread.sleep(1000); } catch (InterruptedException e) {}
                    future.complete(null);
                }).start();
                return future;
            });
        }
    }

    public void downloadMap(MapEntry map, boolean includeResourcePack) {
        new Thread(() -> downloadMapInternal(map, includeResourcePack)).start();
    }

    private void downloadMapInternal(MapEntry map, boolean includeResourcePack) {
        this.minecraft.execute(() -> {
            isDownloading = true;
            downloadProgress = 0.0f;
            statusMessage = "Downloading " + map.name + "...";
            updateButtonStates();
        });

        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Boolean> future = executor.submit(() -> 
                downloadFile(map.downloadUrl, map.name, true, map.name + ".zip", map.sha256, map)
            );

            boolean mapSuccess;
            try {
                mapSuccess = future.get(DOWNLOAD_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                mapSuccess = false;
                this.minecraft.execute(() -> statusMessage = "Download timeout - slow connection");
            } finally {
                executor.shutdownNow();
            }

            if (!mapSuccess) {
                consecutiveFailures++;
                if (consecutiveFailures >= 3) networkIssuesDetected = true;
                this.minecraft.execute(() -> {
                    statusMessage = "Download failed. Try 'Open GitHub'.";
                    isDownloading = false;
                    updateButtonStates();
                });
                return;
            }

            consecutiveFailures = 0;

            String resourcePackFileName = null;
            if (includeResourcePack && map.hasResourcePack()) {
                this.minecraft.execute(() -> {
                    statusMessage = "Downloading resource pack...";
                    downloadProgress = 0.0f;
                });

                String rpName = map.resourcePackName != null ? map.resourcePackName : map.name;
                resourcePackFileName = rpName + ".zip";
                final String finalRpName = rpName;
                final String finalRpFileName = resourcePackFileName;

                executor = Executors.newSingleThreadExecutor();
                Future<Boolean> rpFuture = executor.submit(() -> 
                    downloadFile(map.resourcePackUrl, finalRpName, false, finalRpFileName, null, null)
                );

                try {
                    boolean rpSuccess = rpFuture.get(DOWNLOAD_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (!rpSuccess) {
                        resourcePackFileName = null;
                    } else {
                        this.minecraft.execute(() -> {
                            try {
                                Minecraft.getInstance().getResourcePackRepository().reload();
                            } catch (Exception e) {}
                        });
                    }
                } catch (TimeoutException e) {
                    rpFuture.cancel(true);
                    resourcePackFileName = null;
                } finally {
                    executor.shutdownNow();
                }
            }

            this.minecraft.execute(() -> {
                checkDownloadedMaps();
                statusMessage = "§aSuccessfully installed: " + map.name;
                downloadProgress = 0.0f;
                isDownloading = false;
                updateButtonStates();
            });

        } catch (Exception e) {
            consecutiveFailures++;
            this.minecraft.execute(() -> {
                statusMessage = "Error: " + e.getClass().getSimpleName();
                isDownloading = false;
                updateButtonStates();
            });
        }
    }

    private boolean downloadFile(String fileUrl, String name, boolean isMap, String fileName, String sha256Hash, MapEntry mapRef) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        int maxRetries = 2;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                URL url = new URL(fileUrl);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Connection", "close");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setRequestProperty("Connection", "close");
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);
                    conn.connect();
                    responseCode = conn.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    conn.disconnect();
                    throw new IOException("HTTP " + responseCode);
                }

                File tempDir = new File(Minecraft.getInstance().gameDirectory, "temp_downloads");
                tempDir.mkdirs();
                File zipFile = new File(tempDir, fileName);

                long contentLength = conn.getContentLengthLong();
                if (contentLength > MAX_FILE_SIZE) {
                    conn.disconnect();
                    this.minecraft.execute(() -> statusMessage = "File too large!");
                    return false;
                }

                try (InputStream in = conn.getInputStream();
                     FileOutputStream out = new FileOutputStream(zipFile)) {
                    
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    long lastUpdate = System.currentTimeMillis();

                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        
                        if (totalRead > MAX_FILE_SIZE) throw new IOException("Limit exceeded");

                        long now = System.currentTimeMillis();
                        if (contentLength > 0 && now - lastUpdate > 500) {
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

                    if (targetDir.exists()) {
                        deleteDirectory(targetDir);
                    }
                    targetDir.mkdirs();

                    extractZip(zipFile, targetDir);

                    if (sha256Hash != null && !sha256Hash.isEmpty()) {
                        this.minecraft.execute(() -> statusMessage = "Verifying integrity...");
                        String actualHash = calculateSHA256(zipFile);
                        if (actualHash != null && !actualHash.equalsIgnoreCase(sha256Hash)) {
                            zipFile.delete();
                            this.minecraft.execute(() -> statusMessage = "Integrity check failed");
                            return false;
                        }
                    }
                    
                    if (mapRef != null) {
                        saveUnifiedMetadata(mapRef, targetDir);
                    }

                    zipFile.delete();
                } else {
                    this.minecraft.execute(() -> statusMessage = "Installing RP...");
                    File resourcePacksDir = new File(Minecraft.getInstance().gameDirectory, "resourcepacks");
                    resourcePacksDir.mkdirs();
                    File targetFile = new File(resourcePacksDir, fileName);
                    
                    if (targetFile.exists()) targetFile.delete();
                    Files.move(zipFile.toPath(), targetFile.toPath());
                }

                return true;

            } catch (Exception e) {
                retryCount++;
                if (retryCount >= maxRetries) return false;
                final int currentRetry = retryCount;
                this.minecraft.execute(() -> statusMessage = "Retry " + currentRetry + "/" + maxRetries + "...");
                try { Thread.sleep(2000); } catch (InterruptedException ie) { return false; }
            }
        }
        return false;
    }

    private void deleteDirectory(File dir) {
        if (!dir.exists()) return;
        File[] allContents = dir.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        dir.delete();
    }

    private String calculateSHA256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, n);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder result = new StringBuilder();
            for (byte b : hash) result.append(String.format("%02x", b));
            return result.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                String canonicalDestPath = destDir.getCanonicalPath();
                String canonicalFilePath = file.getCanonicalPath();
                
                if (!canonicalFilePath.startsWith(canonicalDestPath + File.separator)) {
                    throw new IOException("Zip Slip detected: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        byte[] buffer = new byte[8192];
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }

    private void updateButtonStates() {
        MapEntry selected = mapList != null ? mapList.getSelected() : null;
        
        if (downloadButton != null) {
            if (selected != null && !isDownloading && isOfficialTab) {
                downloadButton.active = true;
                if (selected.state == MapInstallState.NOT_INSTALLED) downloadButton.setMessage(Component.literal("Download"));
                else if (selected.state == MapInstallState.UPDATE_AVAILABLE) downloadButton.setMessage(Component.literal("Update"));
                else downloadButton.setMessage(Component.literal("Reinstall"));
            } else {
                downloadButton.active = false;
                downloadButton.setMessage(Component.literal("Download"));
            }
        }

        if (downloadAllButton != null) {
            boolean hasUpdates = maps.stream().anyMatch(m -> m.state == MapInstallState.UPDATE_AVAILABLE);
            downloadAllButton.active = !isDownloading && hasUpdates && isOfficialTab;
        }

        if (downloadFeaturedButton != null && featuredMap != null) {
            downloadFeaturedButton.active = !isDownloading && isOfficialTab;
        }
        
        if (backButton != null) {
            backButton.active = !isDownloading;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseX >= discordLinkX && mouseX <= discordLinkX + discordLinkWidth &&
            mouseY >= discordLinkY && mouseY <= discordLinkY + 9) {
            playSound();
            Util.getPlatform().openUri(DISCORD_INVITE);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (isOfficialTab) {
            if (!loadingFeatured && featuredMap != null) {
                int featuredX = 20;
                int featuredY = 32 + 25; 
                int featuredWidth = this.width - 40;
                int featuredHeight = 100;
                
                graphics.fill(featuredX, featuredY, featuredX + featuredWidth, featuredY + featuredHeight, 0xAA000000);
                graphics.renderOutline(featuredX, featuredY, featuredWidth, featuredHeight, 0xFFFFAA00);

                if (featuredTextureLocation != null) {
                    int imgSize = 90;
                    int imgX = featuredX + 5;
                    int imgY = featuredY + 5;
                    graphics.blit(featuredTextureLocation, imgX, imgY, 0, 0, imgSize, imgSize, imgSize, imgSize);
                }

                int textX = featuredX + 105;
                int textY = featuredY + 10;
                
                graphics.drawString(this.font, "⭐ FEATURED MAP", textX, textY, 0xFFFFAA00);
                
                String displayName = featuredMap.name;
                if (featuredMap.state == MapInstallState.UPDATE_AVAILABLE) displayName += " [Update Available]";
                else if (featuredMap.state == MapInstallState.INSTALLED) displayName += " [Installed]";

                int color = featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? 0xFFFFAA00 : 
                           (featuredMap.state == MapInstallState.INSTALLED ? 0xFF00FF00 : 0xFFFFFFFF);

                String trimmedName = this.font.plainSubstrByWidth(displayName, featuredWidth - 120 - 120);
                if (!trimmedName.equals(displayName)) trimmedName += "...";
                graphics.drawString(this.font, trimmedName, textX, textY + 15, color);

                String desc = featuredMap.description;
                int maxWidth = featuredWidth - 120 - 120; 
                List<String> wrappedDesc = wrapText(desc, maxWidth);
                
                int descY = textY + 30;
                for (int i = 0; i < Math.min(3, wrappedDesc.size()); i++) {
                    graphics.drawString(this.font, wrappedDesc.get(i), textX, descY + (i * 10), 0xFFAAAAAA);
                }

                if (featuredMap.hasResourcePack()) {
                    graphics.drawString(this.font, "[Includes Resource Pack]", textX, textY + 70, 0xFFFFAA00);
                }
            }

            if (loading) {
                graphics.drawCenteredString(this.font, "Loading maps...", this.width / 2, this.height / 2, 0xFFFFFF);
            } else if (errorMessage != null) {
                graphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2 - 10, 0xFF5555);
            } else if (mapList != null) {
                this.mapList.render(graphics, mouseX, mouseY, partialTick);
            }
        } else {
            graphics.drawCenteredString(this.font, "Coming soon...", this.width / 2, this.height / 2, 0xAAAAAA);
        }

        String warning = networkIssuesDetected ? 
            "⚠ Network issues - Use 'Open GitHub' for manual download" :
            "Requires stable connection (WiFi recommended)";
        int warningColor = networkIssuesDetected ? 0xFF5555 : 0xFFAA00;
        graphics.drawCenteredString(this.font, warning, this.width / 2, 22, warningColor);

        discordLinkWidth = this.font.width(DISCORD_MESSAGE);
        discordLinkX = (this.width - discordLinkWidth) / 2;
        discordLinkY = this.height - 65;
        boolean hovering = mouseX >= discordLinkX && mouseX <= discordLinkX + discordLinkWidth &&
                          mouseY >= discordLinkY && mouseY <= discordLinkY + 9;
        
        int dcColor = hovering ? 0x5555FF : 0xAAAAAA;
        graphics.drawCenteredString(this.font, DISCORD_MESSAGE, this.width / 2, discordLinkY, dcColor);
        if (hovering) {
            graphics.fill(discordLinkX, discordLinkY + 9, discordLinkX + discordLinkWidth, discordLinkY + 10, 0xFF5555FF);
        }

        if (statusMessage != null) {
            int statusColor = statusMessage.startsWith("§a") ? 0xFFFFFF : (isDownloading ? 0xFFAA00 : 0xFF5555);
            graphics.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 85, statusColor);
        }

        if (isDownloading && downloadProgress > 0) {
            int barWidth = 200;
            int barHeight = 10;
            int barX = (this.width - barWidth) / 2;
            int barY = this.height - 100;
            
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
            int progressWidth = (int)(barWidth * downloadProgress);
            graphics.fill(barX, barY, barX + progressWidth, barY + barHeight, 0xFF00FF00);
            graphics.renderOutline(barX, barY, barWidth, barHeight, 0xFFFFFFFF);
        }

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
                if (currentLine.length() > 0) {
                    lines.add(currentLine.toString());
                    currentLine = new StringBuilder(word);
                } else {
                    lines.add(word);
                }
            }
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    @Override
    public void removed() {
        super.removed();
        if (featuredTexture != null) {
            featuredTexture.close();
        }
    }

    private class MapList extends ObjectSelectionList<MapEntry> {
        public MapList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
            refreshList();
        }

        public void refreshList() {
            this.clearEntries();
            for (MapEntry map : maps) {
                this.addEntry(map);
            }
        }

        @Override
        public void setSelected(MapEntry entry) {
            super.setSelected(entry);
            updateButtonStates();
        }
        
        @Override
        public int getRowWidth() {
            return this.width - 40;
        }
        
        @Override
        protected int getScrollbarPosition() {
            return this.width - 15;
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (!isOfficialTab) return false;
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (!isOfficialTab) return false;
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
    }

    private class MapEntry extends ObjectSelectionList.Entry<MapEntry> {
        public final String id;
        public final String name;
        public final String description;
        public final String downloadUrl;
        public final String resourcePackUrl;
        public final String resourcePackName;
        public final String sha256;
        public MapInstallState state = MapInstallState.NOT_INSTALLED;

        public MapEntry(String id, String name, String description, String downloadUrl, 
                       String resourcePackUrl, String resourcePackName, String sha256) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.downloadUrl = downloadUrl;
            this.resourcePackUrl = resourcePackUrl;
            this.resourcePackName = resourcePackName;
            this.sha256 = sha256;
        }

        public boolean hasResourcePack() {
            return resourcePackUrl != null && !resourcePackUrl.isEmpty();
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, 
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            
            int color = 0xFFFFFF;
            String status = "";
            if (state == MapInstallState.INSTALLED) {
                color = 0x00FF00;
                status = " [Installed]";
            } else if (state == MapInstallState.UPDATE_AVAILABLE) {
                color = 0xFFFFAA00;
                status = " [Update Available]";
            }

            String rpIndicator = hasResourcePack() ? " [RP]" : "";
            
            String fullTitle = name + status + rpIndicator;
            String trimmedTitle = font.plainSubstrByWidth(fullTitle, width - 10);
            if (!trimmedTitle.equals(fullTitle)) trimmedTitle += "...";
            
            graphics.drawString(font, trimmedTitle, left + 5, top + 2, color);
            
            String trimmedDesc = font.plainSubstrByWidth(description, width - 10);
            if (!trimmedDesc.equals(description)) trimmedDesc += "...";
            graphics.drawString(font, trimmedDesc, left + 5, top + 14, 0xAAAAAA);
            
            graphics.renderOutline(left, top, width, height - 2, 0xFF444444);
            graphics.fill(left + 1, top + 1, left + width - 1, top + height - 3, 0x44000000);
        }

        @Override
        public Component getNarration() {
            return Component.literal(name);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            mapList.setSelected(this);
            return true;
        }
    }

    private class FeaturedMap {
        public final String id, name, description, downloadUrl, resourcePackUrl, resourcePackName, sha256, previewUrl;
        public MapInstallState state = MapInstallState.NOT_INSTALLED;

        public FeaturedMap(String id, String name, String description, String downloadUrl,
                          String resourcePackUrl, String resourcePackName, String sha256, String previewUrl) {
            this.id = id; this.name = name; this.description = description; this.downloadUrl = downloadUrl;
            this.resourcePackUrl = resourcePackUrl; this.resourcePackName = resourcePackName;
            this.sha256 = sha256; this.previewUrl = previewUrl;
        }

        public boolean hasResourcePack() {
            return resourcePackUrl != null && !resourcePackUrl.isEmpty();
        }

        public MapEntry toMapEntry() {
            return new MapEntry(id, name, description, downloadUrl, resourcePackUrl, resourcePackName, sha256);
        }
    }

    // Secondary UI Screens for Confirmation
    private class ResourcePackConfirmScreen extends Screen {
        private final MapDownloaderScreen parent;
        private final MapEntry map;

        protected ResourcePackConfirmScreen(MapDownloaderScreen parent, MapEntry map) {
            super(Component.literal("Resource Pack Available"));
            this.parent = parent;
            this.map = map;
        }

        @Override
        protected void init() {
            int buttonWidth = 100;
            int buttonHeight = 20;
            int spacing = 10;
            int startX = (this.width - (buttonWidth * 2 + spacing)) / 2;
            int buttonY = this.height / 2 + 20;

            this.addRenderableWidget(Button.builder(Component.literal("Yes"), btn -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
                minecraft.setScreen(parent);
                parent.downloadMap(map, true);
            }).bounds(startX, buttonY, buttonWidth, buttonHeight).build());

            this.addRenderableWidget(Button.builder(Component.literal("No"), btn -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
                minecraft.setScreen(parent);
                parent.downloadMap(map, false);
            }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "This map includes a custom resource pack.", this.width / 2, this.height / 2 - 10, 0xAAAAAA);
            graphics.drawCenteredString(this.font, "Would you like to download it?", this.width / 2, this.height / 2 + 5, 0xAAAAAA);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    private class OverwriteConfirmScreen extends Screen {
        private final MapDownloaderScreen parent;
        private final MapEntry map;

        protected OverwriteConfirmScreen(MapDownloaderScreen parent, MapEntry map) {
            super(Component.literal("Overwrite Save"));
            this.parent = parent;
            this.map = map;
        }

        @Override
        protected void init() {
            int buttonWidth = 100;
            int spacing = 10;
            int startX = (this.width - (buttonWidth * 2 + spacing)) / 2;
            int buttonY = this.height / 2 + 30;

            this.addRenderableWidget(Button.builder(Component.literal("Yes, Overwrite"), btn -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
                if (map.hasResourcePack()) minecraft.setScreen(new ResourcePackConfirmScreen(parent, map));
                else { minecraft.setScreen(parent); parent.downloadMap(map, false); }
            }).bounds(startX, buttonY, buttonWidth, 20).build());

            this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
                minecraft.setScreen(parent);
            }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, 20).build());
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFF5555);
            graphics.drawCenteredString(this.font, "Updating this map will completely DELETE your local save progress.", this.width / 2, this.height / 2 - 10, 0xAAAAAA);
            graphics.drawCenteredString(this.font, "Are you sure you want to proceed?", this.width / 2, this.height / 2 + 5, 0xFFFFFF);
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }
}