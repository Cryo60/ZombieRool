package net.mcreator.zombierool;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.mcreator.zombierool.init.ZombieroolModSounds;
import net.minecraft.Util;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.concurrent.*;

public class MapDownloaderScreen extends Screen {
    private static final String MAPS_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/maps.json";
    private static final String GITHUB_MAPS_URL = "https://github.com/Cryo60/zombierool-maps/tree/main/maps";
    private static final String DISCORD_MESSAGE = "Join our Discord for community-made maps!";
    private static final String DISCORD_INVITE = "https://discord.gg/HGv2r44hXM";
    
    // Sécurité: Domaines autorisés
    private static final List<String> ALLOWED_DOMAINS = Arrays.asList(
        "raw.githubusercontent.com",
        "github.com",
        "objects.githubusercontent.com",
        "codeload.github.com"
    );
    
    // Sécurité: Limite de taille (500 MB max par fichier)
    private static final long MAX_FILE_SIZE = 500L * 1024L * 1024L;
    
    // Timeouts adaptés pour 4G
    private static final int CONNECT_TIMEOUT = 30000;
    private static final int READ_TIMEOUT = 60000;
    private static final int DOWNLOAD_TIMEOUT = 300000;
    
    private final Screen lastScreen;
    private MapList mapList;
    private Button downloadButton;
    private Button downloadAllButton;
    private Button openGithubButton;
    private Button backButton;
    private List<MapEntry> maps = new ArrayList<>();
    private List<MapEntry> downloadedMaps = new ArrayList<>();
    private boolean loading = true;
    private String errorMessage = null;
    private String statusMessage = null;
    private float downloadProgress = 0.0f;
    private boolean isDownloading = false;
    private int consecutiveFailures = 0;
    private boolean networkIssuesDetected = false;
    private int discordLinkX;
    private int discordLinkY;
    private int discordLinkWidth;

    public MapDownloaderScreen(Screen lastScreen) {
        super(Component.literal("Official Maps"));
        this.lastScreen = lastScreen;
    }

    /**
     * Sauvegarde les métadonnées du RP dans le monde après téléchargement
     */
    private void saveResourcePackMetadata(MapEntry map, File worldDir) {
        if (!map.hasResourcePack()) return;
        
        try {
            File rpMetadataFile = new File(worldDir, "zombierool_resourcepack.json");
            
            com.google.gson.JsonObject json = new com.google.gson.JsonObject();
            json.addProperty("url", map.resourcePackUrl);
            json.addProperty("name", map.resourcePackName != null ? map.resourcePackName : map.name);
            
            try (java.io.FileWriter writer = new java.io.FileWriter(rpMetadataFile)) {
                new com.google.gson.Gson().toJson(json, writer);
            }
            
            System.out.println("[ZombieRool] Saved RP metadata to world: " + worldDir.getName());
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error saving RP metadata: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void init() {
        loadMapsFromGithub();

        int listHeight = (this.height * 2) / 3 - 60;
        this.mapList = new MapList(this.minecraft, this.width, listHeight, 32, listHeight + 32, 36);
        this.addWidget(this.mapList);

        int buttonWidth = 120;
        int buttonHeight = 20;
        int spacing = 5;
        int totalWidth = (buttonWidth * 4) + (spacing * 3);
        int startX = (this.width - totalWidth) / 2;
        int buttonY = this.height - 50;

        this.downloadButton = Button.builder(Component.literal("Download"), btn -> {
            playSound();
            downloadAndInstallMap();
        }).bounds(startX, buttonY, buttonWidth, buttonHeight).build();

        this.downloadAllButton = Button.builder(Component.literal("Download All"), btn -> {
            playSound();
            downloadAllMaps();
        }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build();
        
        this.openGithubButton = Button.builder(Component.literal("Open GitHub"), btn -> {
            playSound();
            Util.getPlatform().openUri(GITHUB_MAPS_URL);
        }).bounds(startX + (buttonWidth + spacing) * 2, buttonY, buttonWidth, buttonHeight).build();

        this.backButton = Button.builder(Component.literal("Back"), btn -> {
            playSound();
            this.minecraft.setScreen(lastScreen);
        }).bounds(startX + (buttonWidth + spacing) * 3, buttonY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(downloadButton);
        this.addRenderableWidget(downloadAllButton);
        this.addRenderableWidget(openGithubButton);
        this.addRenderableWidget(backButton);

        updateButtonStates();
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
                        if (rpObj.has("url")) {
                            resourcePackUrl = rpObj.get("url").getAsString();
                        }
                        if (rpObj.has("name")) {
                            resourcePackName = rpObj.get("name").getAsString();
                        }
                    }
                    
                    String sha256 = null;
                    if (mapObj.has("sha256")) {
                        sha256 = mapObj.get("sha256").getAsString();
                    }
                    
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
                checkDownloadedMaps();
                
                this.minecraft.execute(() -> {
                    if (mapList != null) {
                        mapList.refreshList();
                        updateButtonStates();
                    }
                });

            } catch (Exception e) {
                errorMessage = "Failed to load maps. Click 'Open GitHub' to download manually.";
                loading = false;
                networkIssuesDetected = true;
                e.printStackTrace();
            }
        }).start();
    }

    private void checkDownloadedMaps() {
        downloadedMaps.clear();
        File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
        for (MapEntry map : maps) {
            File mapDir = new File(savesDir, map.name);
            if (mapDir.exists() && mapDir.isDirectory()) {
                downloadedMaps.add(map);
            }
        }
    }

    private void downloadAndInstallMap() {
        MapEntry selected = mapList.getSelected();
        if (selected == null || isDownloading) {
            return;
        }
        
        if (selected.hasResourcePack()) {
            this.minecraft.setScreen(new ResourcePackConfirmScreen(this, selected));
        } else {
            downloadMap(selected, true, false);
        }
    }

    private void downloadAllMaps() {
        if (isDownloading) return;
        
        List<MapEntry> mapsToDownload = new ArrayList<>();
        for (MapEntry map : maps) {
            if (!downloadedMaps.contains(map)) {
                mapsToDownload.add(map);
            }
        }
        
        if (mapsToDownload.isEmpty()) return;
        
        // Queue downloads using CompletableFuture chain
        CompletableFuture<Void> downloadChain = CompletableFuture.completedFuture(null);
        
        for (MapEntry map : mapsToDownload) {
            downloadChain = downloadChain.thenCompose(v -> {
                if (consecutiveFailures >= 2) {
                    this.minecraft.execute(() -> {
                        statusMessage = "Multiple failures detected. Use 'Open GitHub' for manual download.";
                        networkIssuesDetected = true;
                    });
                    return CompletableFuture.completedFuture(null);
                }
                
                CompletableFuture<Void> future = new CompletableFuture<>();
                
                new Thread(() -> {
                    downloadMapInternal(map, false, map.hasResourcePack());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    future.complete(null);
                }).start();
                
                return future;
            });
        }
    }

    public void downloadMap(MapEntry map, boolean showNotification, boolean includeResourcePack) {
        new Thread(() -> downloadMapInternal(map, showNotification, includeResourcePack)).start();
    }
    
    private void downloadMapInternal(MapEntry map, boolean showNotification, boolean includeResourcePack) {
        this.minecraft.execute(() -> {
            isDownloading = true;
            downloadProgress = 0.0f;
            statusMessage = "Downloading " + map.name + "...";
            updateButtonStates();
        });
        
        try {
            // Téléchargement de la map avec timeout global
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<Boolean> future = executor.submit(() -> 
                downloadFile(map.downloadUrl, map.name, true, map.name + ".zip", map.sha256)
            );
            
            boolean mapSuccess;
            try {
                mapSuccess = future.get(DOWNLOAD_TIMEOUT, TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                mapSuccess = false;
                this.minecraft.execute(() -> {
                    statusMessage = "Download timeout - slow connection detected";
                });
            } finally {
                executor.shutdownNow();
            }
            
            if (!mapSuccess) {
                consecutiveFailures++;
                if (consecutiveFailures >= 3) {
                    networkIssuesDetected = true;
                }
                this.minecraft.execute(() -> {
                    statusMessage = "Download failed. Try 'Open GitHub' for manual download.";
                    isDownloading = false;
                    updateButtonStates();
                });
                return;
            }
            
            consecutiveFailures = 0; // Reset sur succès
            
            // NOUVEAU: Sauvegarder les métadonnées du RP dans le monde après extraction
            if (includeResourcePack && map.hasResourcePack()) {
                File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
                File worldDir = new File(savesDir, map.name);
                
                // Trouver le bon dossier si un suffixe a été ajouté
                int i = 1;
                while (!worldDir.exists() && i < 100) {
                    worldDir = new File(savesDir, map.name + " (" + i + ")");
                    i++;
                }
                
                if (worldDir.exists()) {
                    saveResourcePackMetadata(map, worldDir);
                }
            }
            
            // Téléchargement du resource pack si disponible
            String resourcePackFileName = null;
            if (includeResourcePack && map.hasResourcePack()) {
                this.minecraft.execute(() -> {
                    statusMessage = "Downloading resource pack...";
                    downloadProgress = 0.0f;
                });
                
                String rpName = map.resourcePackName != null ? map.resourcePackName : map.name;
                resourcePackFileName = rpName + ".zip";
                
                System.out.println("[ZombieRool] ========== RESOURCE PACK DOWNLOAD ==========");
                System.out.println("[ZombieRool] Map name: " + map.name);
                System.out.println("[ZombieRool] RP name from JSON: " + map.resourcePackName);
                System.out.println("[ZombieRool] Final RP name: " + rpName);
                System.out.println("[ZombieRool] Final RP filename: " + resourcePackFileName);
                System.out.println("[ZombieRool] RP URL: " + map.resourcePackUrl);
                
                final String finalRpName = rpName;
                final String finalRpFileName = resourcePackFileName;
                
                executor = Executors.newSingleThreadExecutor();
                Future<Boolean> rpFuture = executor.submit(() -> 
                    downloadFile(map.resourcePackUrl, finalRpName, false, finalRpFileName, null)
                );
                
                try {
                    boolean rpSuccess = rpFuture.get(DOWNLOAD_TIMEOUT, TimeUnit.MILLISECONDS);
                    if (!rpSuccess) {
                        resourcePackFileName = null;
                    } else {
                        // IMPORTANT: Forcer Minecraft à détecter le nouveau resource pack
                        this.minecraft.execute(() -> {
                            try {
                                System.out.println("[ZombieRool] Reloading pack repository to detect new RP...");
                                Minecraft.getInstance().getResourcePackRepository().reload();
                                System.out.println("[ZombieRool] Pack repository reloaded");
                            } catch (Exception e) {
                                System.err.println("[ZombieRool] Error reloading pack repository: " + e.getMessage());
                            }
                        });
                    }
                } catch (TimeoutException e) {
                    rpFuture.cancel(true);
                    resourcePackFileName = null;
                } finally {
                    executor.shutdownNow();
                }
            }

            checkDownloadedMaps();
            
            final String finalRpFileName = resourcePackFileName;
            this.minecraft.execute(() -> {
                statusMessage = null;
                downloadProgress = 0.0f;
                isDownloading = false;
                updateButtonStates();
                
                if (showNotification) {
                    String message = "Installed: " + map.name;
                    if (finalRpFileName != null) {
                        message += " + RP";
                    }
                    showSuccessNotification(message);
                }
            });

        } catch (Exception e) {
            consecutiveFailures++;
            e.printStackTrace();
            this.minecraft.execute(() -> {
                statusMessage = "Error: " + e.getMessage();
                isDownloading = false;
                updateButtonStates();
            });
        }
    }
    
    private boolean downloadFile(String fileUrl, String name, boolean isMap, String fileName, String sha256Hash) {
        System.setProperty("java.net.preferIPv4Stack", "true");
        
        int maxRetries = 2; // Réduit à 2 tentatives pour détecter les problèmes plus vite
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                System.out.println("[ZombieRool] Downloading " + (isMap ? "map" : "resource pack") + ": " + name);
                System.out.println("[ZombieRool] URL: " + fileUrl);
                
                URL url = new URL(fileUrl);
                
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Accept", "*/*");
                conn.setRequestProperty("Connection", "close"); // Important pour 4G
                
                conn.connect();
                
                int responseCode = conn.getResponseCode();
                
                // Gestion des redirections
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = conn.getHeaderField("Location");
                    System.out.println("[ZombieRool] Following redirect to: " + newUrl);
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
                
                System.out.println("[ZombieRool] Downloading to temp file: " + zipFile.getAbsolutePath());
                
                long contentLength = conn.getContentLengthLong();
                
                // Sécurité: vérifier la taille du fichier
                if (contentLength > MAX_FILE_SIZE) {
                    conn.disconnect();
                    this.minecraft.execute(() -> {
                        statusMessage = "File too large: " + (contentLength / 1024 / 1024) + " MB";
                    });
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
                        
                        // Sécurité: vérifier qu'on ne dépasse pas la limite même si contentLength est faux
                        if (totalRead > MAX_FILE_SIZE) {
                            throw new IOException("Download exceeded size limit");
                        }
                        
                        // Update progress max toutes les 500ms pour éviter trop d'updates
                        long now = System.currentTimeMillis();
                        if (contentLength > 0 && now - lastUpdate > 500) {
                            final float progress = (float) totalRead / contentLength;
                            this.minecraft.execute(() -> downloadProgress = progress);
                            lastUpdate = now;
                        }
                    }
                }
                
                conn.disconnect();
                
                System.out.println("[ZombieRool] Download complete. File size: " + zipFile.length() + " bytes");

                if (isMap) {
                    this.minecraft.execute(() -> statusMessage = "Installing " + name + "...");
                    File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
                    File targetDir = new File(savesDir, name);
                    
                    int i = 1;
                    while (targetDir.exists()) {
                        targetDir = new File(savesDir, name + " (" + i + ")");
                        i++;
                    }
                    
                    System.out.println("[ZombieRool] Extracting map to: " + targetDir.getAbsolutePath());
                    extractZip(zipFile, targetDir);
                    
                    // Vérifier le hash SHA256 si disponible
                    if (sha256Hash != null && !sha256Hash.isEmpty()) {
                        this.minecraft.execute(() -> statusMessage = "Verifying integrity...");
                        String actualHash = calculateSHA256(zipFile);
                        if (actualHash != null && !actualHash.equalsIgnoreCase(sha256Hash)) {
                            zipFile.delete();
                            this.minecraft.execute(() -> {
                                statusMessage = "Integrity check failed - file may be corrupted";
                            });
                            return false;
                        }
                    }
                    
                    zipFile.delete();
                } else {
				    this.minecraft.execute(() -> statusMessage = "Installing RP...");
				    File resourcePacksDir = new File(Minecraft.getInstance().gameDirectory, "resourcepacks");
				    resourcePacksDir.mkdirs();
				    
				    System.out.println("[ZombieRool] Resource packs directory: " + resourcePacksDir.getAbsolutePath());
				    System.out.println("[ZombieRool] Target filename: " + fileName);
				    
				    File targetFile = new File(resourcePacksDir, fileName);
				    
				    // Vérifier si le fichier existe déjà
				    if (targetFile.exists()) {
				        System.out.println("[ZombieRool] Resource pack already exists, skipping: " + targetFile.getName());
				        zipFile.delete(); // Supprimer le fichier temporaire
				        return true;
				    }
				    
				    // Chercher si une version avec suffixe existe déjà
				    int i = 1;
				    String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
				    String extension = fileName.substring(fileName.lastIndexOf('.'));
				    File existingFile = new File(resourcePacksDir, baseName + "_" + i + extension);
				    
				    if (existingFile.exists()) {
				        System.out.println("[ZombieRool] Similar resource pack already exists, skipping: " + existingFile.getName());
				        zipFile.delete();
				        return true;
				    }
				    
				    System.out.println("[ZombieRool] Moving resource pack to: " + targetFile.getAbsolutePath());
				    Files.move(zipFile.toPath(), targetFile.toPath());
				    System.out.println("[ZombieRool] Resource pack installed successfully");
				    System.out.println("[ZombieRool] Final file exists: " + targetFile.exists());
				    System.out.println("[ZombieRool] Final file size: " + targetFile.length() + " bytes");
				}
                
                return true;

            } catch (Exception e) {
                retryCount++;
                System.err.println("[ZombieRool] Download attempt " + retryCount + " failed: " + e.getMessage());
                e.printStackTrace();
                
                if (retryCount >= maxRetries) {
                    return false;
                }
                
                final int currentRetry = retryCount;
                this.minecraft.execute(() -> {
                    statusMessage = "Retry " + currentRetry + "/" + maxRetries + "...";
                });
                
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Vérifie si une URL provient d'un domaine autorisé
     */
    private boolean isUrlSafe(String urlString) {
        try {
            URL url = new URL(urlString);
            String host = url.getHost().toLowerCase();
            return ALLOWED_DOMAINS.stream().anyMatch(host::contains);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Calcule le SHA-256 d'un fichier
     */
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
            return bytesToHex(hash);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Convertit un tableau de bytes en string hexadécimal
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
    
    private void showSuccessNotification(String message) {
        this.minecraft.setScreen(new CopyNotificationScreen(Component.literal(message), 0xFF00FF00));
        this.minecraft.execute(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(2000);
                    this.minecraft.execute(() -> this.minecraft.setScreen(this));
                } catch (InterruptedException ignored) {}
            }).start();
        });
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Protection Zip Slip
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
            downloadButton.active = selected != null && !downloadedMaps.contains(selected) && !isDownloading;
        }
        if (downloadAllButton != null) {
            downloadAllButton.active = !isDownloading;
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
	    
	    if (loading) {
	        graphics.drawCenteredString(this.font, "Loading maps...", this.width / 2, this.height / 2, 0xFFFFFF);
	    } else if (errorMessage != null) {
	        graphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2 - 10, 0xFF5555);
	        if (networkIssuesDetected) {
	            graphics.drawCenteredString(this.font, "Use 'Open GitHub' button for manual download", 
	                this.width / 2, this.height / 2 + 10, 0xFFAA00);
	        }
	    } else {
	        this.mapList.render(graphics, mouseX, mouseY, partialTick);
	    }
	
	    graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);
	    
	    // Warning adapté pour 4G
	    String warning = networkIssuesDetected ? 
	        "⚠ Network issues - Use 'Open GitHub' for manual download" :
	        "Requires stable connection (WiFi recommended)";
	    int warningColor = networkIssuesDetected ? 0xFF5555 : 0xFFAA00;
	    graphics.drawCenteredString(this.font, warning, this.width / 2, 22, warningColor);
	    
	    // Discord link
	    discordLinkWidth = this.font.width(DISCORD_MESSAGE);
	    discordLinkX = (this.width - discordLinkWidth) / 2;
	    discordLinkY = this.height - 70;
	    
	    boolean hovering = mouseX >= discordLinkX && mouseX <= discordLinkX + discordLinkWidth &&
	                      mouseY >= discordLinkY && mouseY <= discordLinkY + 9;
	    int color = hovering ? 0x5555FF : 0xAAAAAA;
	    graphics.drawCenteredString(this.font, DISCORD_MESSAGE, this.width / 2, discordLinkY, color);
	    
	    if (hovering) {
	        graphics.fill(discordLinkX, discordLinkY + 9, discordLinkX + discordLinkWidth, discordLinkY + 10, 0xFF5555FF);
	    }
	    
	    // Status
	    if (statusMessage != null) {
	        graphics.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 85, 0xFFFFFF);
	    }
	    
	    // Progress bar
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
    }

    private class MapEntry extends ObjectSelectionList.Entry<MapEntry> {
	    public final String id;
	    public final String name;
	    public final String description;
	    public final String downloadUrl;
	    public final String resourcePackUrl;
	    public final String resourcePackName;
	    public final String sha256;
	
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
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof MapEntry)) return false;
            MapEntry other = (MapEntry) obj;
            return id != null && id.equals(other.id);
        }
        
        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, 
                          int mouseX, int mouseY, boolean hovering, float partialTick) {
            boolean installed = downloadedMaps.contains(this);
            int color = installed ? 0x00FF00 : 0xFFFFFF;
            String status = installed ? " [Installed]" : "";
            String rpIndicator = hasResourcePack() ? " [RP]" : "";
            
            graphics.drawString(font, name + status + rpIndicator, left + 5, top + 2, color);
            graphics.drawString(font, description, left + 5, top + 14, 0xAAAAAA);
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
            int totalWidth = (buttonWidth * 2) + spacing;
            int startX = (this.width - totalWidth) / 2;
            int buttonY = this.height / 2 + 20;

            Button yesButton = Button.builder(Component.literal("Yes"), btn -> {
                playSound();
                minecraft.setScreen(parent);
                parent.downloadMap(map, true, true);
            }).bounds(startX, buttonY, buttonWidth, buttonHeight).build();

            Button noButton = Button.builder(Component.literal("No"), btn -> {
                playSound();
                minecraft.setScreen(parent);
                parent.downloadMap(map, true, false);
            }).bounds(startX + buttonWidth + spacing, buttonY, buttonWidth, buttonHeight).build();

            this.addRenderableWidget(yesButton);
            this.addRenderableWidget(noButton);
        }

        private void playSound() {
            minecraft.getSoundManager().play(
                SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F)
            );
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics);
            
            graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);
            graphics.drawCenteredString(this.font, "This map includes a custom resource pack.", 
                this.width / 2, this.height / 2 - 10, 0xAAAAAA);
            graphics.drawCenteredString(this.font, "Would you like to download it?", 
                this.width / 2, this.height / 2 + 5, 0xAAAAAA);

            super.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public void onClose() {
            this.minecraft.setScreen(parent);
        }
    }
}