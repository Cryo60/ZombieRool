package me.cryo.zombierool.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraftforge.fml.ModList;
import me.cryo.zombierool.init.ZombieroolModSounds;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TacZPackDownloaderScreen extends Screen {
    private static final String DEPS_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/dependencies.json";
    private static final long MAX_FILE_SIZE = 1000L * 1024L * 1024L; 
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 60000;

    private final Screen lastScreen;
    private DependencyList depList;
    
    private Button directDownloadButton;
    private Button manualDownloadButton;
    private Button downloadAllButton;
    private Button backButton;

    private List<DependencyEntry> dependencies = new ArrayList<>();
    private boolean loading = true;
    private String errorMessage = null;
    private boolean isDownloading = false;
    private boolean requiresRestart = false;

    private int currentDownloadIndex = 0;
    private int totalDownloads = 0;
    private String currentDownloadName = "";
    private float currentFileProgress = 0.0f;
    private String statusMessage = null;

    public TacZPackDownloaderScreen(Screen lastScreen) {
        super(Component.literal("TacZ & Gunpacks Downloader"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        loadDependencies();

        int listTop = 45;
        
        int buttonsPerRow = (this.width > 450) ? 4 : 2;
        int spacing = 5;
        int buttonWidth = Math.min(100, (this.width - 20) / buttonsPerRow - spacing);
        int buttonHeight = 20;
        
        int rows = (int) Math.ceil(4.0 / buttonsPerRow);
        int totalButtonAreaHeight = rows * (buttonHeight + spacing);
        int listHeight = this.height - listTop - totalButtonAreaHeight - 30;
        
        this.depList = new DependencyList(this.minecraft, this.width, listHeight, listTop, listTop + listHeight, 20);
        this.addWidget(this.depList);

        int startX = (this.width - (buttonsPerRow * buttonWidth + (buttonsPerRow - 1) * spacing)) / 2;
        int startY = this.height - totalButtonAreaHeight - 20;

        this.directDownloadButton = Button.builder(Component.literal("Direct DL"), btn -> {
            playSound();
            downloadSelectedDirect();
        }).bounds(startX, startY, buttonWidth, buttonHeight).build();

        this.manualDownloadButton = Button.builder(Component.literal("Manual DL"), btn -> {
            playSound();
            openSelectedManualLink();
        }).bounds(startX + buttonWidth + spacing, startY, buttonWidth, buttonHeight).build();

        int row2Y = (buttonsPerRow == 2) ? startY + buttonHeight + spacing : startY;
        int row2X = (buttonsPerRow == 2) ? startX : startX + (buttonWidth + spacing) * 2;

        this.downloadAllButton = Button.builder(Component.literal("DL All (Direct)"), btn -> {
            playSound();
            downloadAllDirect();
        }).bounds(row2X, row2Y, buttonWidth, buttonHeight).build();

        this.backButton = Button.builder(Component.literal("Back"), btn -> {
            playSound();
            this.minecraft.setScreen(lastScreen);
        }).bounds(row2X + buttonWidth + spacing, row2Y, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(directDownloadButton);
        this.addRenderableWidget(manualDownloadButton);
        this.addRenderableWidget(downloadAllButton);
        this.addRenderableWidget(backButton);

        updateButtonStates();
    }

    private void loadDependencies() {
        new Thread(() -> {
            try {
                URL url = new URL(DEPS_JSON_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.setRequestProperty("Accept", "application/json, text/plain, */*");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = conn.getHeaderField("Location");
                    conn.disconnect();
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.setConnectTimeout(CONNECT_TIMEOUT);
                    conn.setReadTimeout(READ_TIMEOUT);
                    conn.connect();
                    responseCode = conn.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new IOException("HTTP Error " + responseCode);
                }

                InputStreamReader reader = new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8);
                JsonElement rootElement = JsonParser.parseReader(reader);
                reader.close();
                conn.disconnect();

                JsonArray depsArray = null;
                if (rootElement.isJsonObject()) {
                    JsonObject json = rootElement.getAsJsonObject();
                    if (json.has("dependencies")) depsArray = json.getAsJsonArray("dependencies");
                    else if (json.has("dependancies")) depsArray = json.getAsJsonArray("dependancies");
                } else if (rootElement.isJsonArray()) {
                    depsArray = rootElement.getAsJsonArray();
                }

                if (depsArray == null) throw new RuntimeException("No dependencies array found.");

                dependencies.clear();
                for (int i = 0; i < depsArray.size(); i++) {
                    JsonObject obj = depsArray.get(i).getAsJsonObject();
                    String dId = obj.has("id") ? obj.get("id").getAsString() : "unknown_" + i;
                    String dName = obj.has("name") ? obj.get("name").getAsString() : "Unknown Mod/Pack";
                    String pageUrl = obj.has("page_url") ? obj.get("page_url").getAsString() : "";
                    String downloadUrl = obj.has("download_url") ? obj.get("download_url").getAsString() : "";

                    if (pageUrl.isEmpty() && downloadUrl.contains("curseforge.com")) {
                        pageUrl = downloadUrl;
                        downloadUrl = "";
                    }

                    String dType = obj.has("type") ? obj.get("type").getAsString() : "pack";
                    String dFilename = obj.has("filename") ? obj.get("filename").getAsString() : (dId + (dType.equalsIgnoreCase("mod") ? ".jar" : ".zip"));

                    DependencyEntry entry = new DependencyEntry(dId, dName, pageUrl, downloadUrl, dFilename, dType);
                    dependencies.add(entry);
                }

                checkInstalledStatus();
                loading = false;
                this.minecraft.execute(() -> {
                    if (depList != null) {
                        depList.refreshList();
                        updateButtonStates();
                    }
                });

            } catch (Exception e) {
                errorMessage = "Failed to load list. Check your connection.";
                loading = false;
                e.printStackTrace();
            }
        }).start();
    }

    private void checkInstalledStatus() {
        File gameDir = Minecraft.getInstance().gameDirectory;
        File modsDir = new File(gameDir, "mods");
        File taczDir = new File(gameDir, "tacz");

        for (DependencyEntry entry : dependencies) {
            File targetDir = entry.type.equalsIgnoreCase("mod") ? modsDir : taczDir;
            File targetFile = new File(targetDir, entry.filename);
            entry.isInstalled = targetFile.exists() && targetFile.isFile();
        }
    }

    private void downloadSelectedDirect() {
        DependencyEntry selected = depList.getSelected();
        if (selected == null || isDownloading || selected.isInstalled) return;

        if (selected.downloadUrl == null || selected.downloadUrl.isEmpty()) {
            this.minecraft.execute(() -> statusMessage = "No direct link. Use Manual DL.");
            return;
        }

        List<DependencyEntry> queue = new ArrayList<>();
        queue.add(selected);
        startBatchDownload(queue);
    }

    private void openSelectedManualLink() {
        DependencyEntry selected = depList.getSelected();
        if (selected == null) return;
        
        if (selected.pageUrl != null && !selected.pageUrl.isEmpty()) {
            net.minecraft.Util.getPlatform().openUri(selected.pageUrl);
        } else {
            this.minecraft.execute(() -> statusMessage = "No manual link available.");
        }
    }

    private void downloadAllDirect() {
        if (isDownloading) return;

        List<DependencyEntry> queue = new ArrayList<>();
        for (DependencyEntry entry : dependencies) {
            if (!entry.isInstalled && entry.downloadUrl != null && !entry.downloadUrl.isEmpty()) {
                queue.add(entry);
            }
        }

        if (!queue.isEmpty()) {
            startBatchDownload(queue);
        } else {
            this.minecraft.execute(() -> statusMessage = "All available direct mods are already installed.");
        }
    }

    private void startBatchDownload(List<DependencyEntry> queue) {
        isDownloading = true;
        totalDownloads = queue.size();
        currentDownloadIndex = 0;
        updateButtonStates();
        statusMessage = null;

        new Thread(() -> {
            for (int i = 0; i < queue.size(); i++) {
                DependencyEntry entry = queue.get(i);
                currentDownloadIndex = i + 1;
                currentDownloadName = entry.name;
                currentFileProgress = 0.0f;

                boolean success = downloadFile(entry);
                if (success) {
                    entry.isInstalled = true;
                    if (entry.type.equalsIgnoreCase("mod")) requiresRestart = true;
                }
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
            
            isDownloading = false;
            currentDownloadName = "";
            this.minecraft.execute(() -> {
                depList.refreshList();
                updateButtonStates();
            });
        }).start();
    }

    private boolean downloadFile(DependencyEntry entry) {
        try {
            URL url = new URL(entry.downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(READ_TIMEOUT);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            conn.setRequestProperty("Accept", "*/*");
            conn.connect();

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                String newUrl = conn.getHeaderField("Location");
                conn = (HttpURLConnection) new URL(newUrl).openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                conn.connect();
                responseCode = conn.getResponseCode();
            }

            final int finalResponseCode = responseCode; 
            if (finalResponseCode != HttpURLConnection.HTTP_OK) {
                this.minecraft.execute(() -> statusMessage = "HTTP Error " + finalResponseCode + " for " + entry.name);
                return false;
            }

            String contentType = conn.getContentType();
            if (contentType != null && contentType.toLowerCase().contains("text/html")) {
                conn.disconnect();
                this.minecraft.execute(() -> statusMessage = "Invalid link (HTML). Use Manual DL.");
                return false; 
            }

            File gameDir = Minecraft.getInstance().gameDirectory;
            File targetDir = entry.type.equalsIgnoreCase("mod") ? new File(gameDir, "mods") : new File(gameDir, "tacz");
            targetDir.mkdirs();
            File targetFile = new File(targetDir, entry.filename);

            long contentLength = conn.getContentLengthLong();
            if (contentLength > MAX_FILE_SIZE) return false;

            try (InputStream in = conn.getInputStream();
                 FileOutputStream out = new FileOutputStream(targetFile)) {
                
                byte[] buffer = new byte[8192];
                int bytesRead;
                long totalRead = 0;
                long lastUpdate = System.currentTimeMillis();

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    totalRead += bytesRead;

                    long now = System.currentTimeMillis();
                    if (contentLength > 0 && now - lastUpdate > 100) {
                        currentFileProgress = (float) totalRead / contentLength;
                        lastUpdate = now;
                    }
                }
            }
            conn.disconnect();
            return true;

        } catch (Exception e) {
            this.minecraft.execute(() -> statusMessage = "Download Error: " + e.getClass().getSimpleName());
            e.printStackTrace();
            return false;
        }
    }

    private void updateButtonStates() {
        DependencyEntry selected = depList != null ? depList.getSelected() : null;

        if (directDownloadButton != null) {
            directDownloadButton.active = selected != null && !selected.isInstalled && !isDownloading && selected.downloadUrl != null && !selected.downloadUrl.isEmpty();
        }
        if (manualDownloadButton != null) {
            manualDownloadButton.active = selected != null && !isDownloading && selected.pageUrl != null && !selected.pageUrl.isEmpty();
        }
        if (downloadAllButton != null) {
            boolean hasMissingDirect = false;
            for (DependencyEntry entry : dependencies) {
                if (!entry.isInstalled && entry.downloadUrl != null && !entry.downloadUrl.isEmpty()) {
                    hasMissingDirect = true;
                    break;
                }
            }
            downloadAllButton.active = !isDownloading && hasMissingDirect;
        }
        if (backButton != null) {
            backButton.active = !isDownloading;
        }
    }

    private void playSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (!ModList.get().isLoaded("tacz") && !loading) {
            graphics.drawCenteredString(this.font, "Warning: TacZ mod is required to load gunpacks!", this.width / 2, 25, 0xFF5555);
        }

        if (loading) {
            graphics.drawCenteredString(this.font, "Loading list...", this.width / 2, this.height / 2, 0xAAAAAA);
        } else if (errorMessage != null) {
            graphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2, 0xFF5555);
        } else {
            this.depList.render(graphics, mouseX, mouseY, partialTick);
        }

        if (statusMessage != null) {
            int color = isDownloading ? 0xFFAA00 : 0xFF5555;
            graphics.drawCenteredString(this.font, statusMessage, this.width / 2, this.height - 45, color);
        }

        if (isDownloading) {
            int barWidth = 200;
            int barHeight = 10;
            int barX = (this.width - barWidth) / 2;
            int barY = this.height - 60;
            
            String status = String.format("Downloading (%d/%d) : %s", currentDownloadIndex, totalDownloads, currentDownloadName);
            graphics.drawCenteredString(this.font, status, this.width / 2, barY - 12, 0xFFFFFF);
            
            graphics.fill(barX, barY, barX + barWidth, barY + barHeight, 0xFF333333);
            graphics.fill(barX, barY, barX + (int)(barWidth * currentFileProgress), barY + barHeight, 0xFF00AA00);
            graphics.renderOutline(barX, barY, barWidth, barHeight, 0xFFFFFFFF);
        } else if (requiresRestart) {
            graphics.drawCenteredString(this.font, "⚠ Files installed! Please restart the game.", this.width / 2, this.height - 55, 0xFFFFAA00);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private class DependencyList extends ObjectSelectionList<DependencyEntry> {
        public DependencyList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
            super(mc, width, height, top, bottom, itemHeight);
        }

        public void refreshList() {
            this.clearEntries();
            for (DependencyEntry entry : dependencies) {
                this.addEntry(entry);
            }
        }

        @Override
        public void setSelected(DependencyEntry entry) {
            super.setSelected(entry);
            updateButtonStates();
        }
    }

    private class DependencyEntry extends ObjectSelectionList.Entry<DependencyEntry> {
        public final String id, name, pageUrl, downloadUrl, filename, type;
        public boolean isInstalled = false;

        public DependencyEntry(String id, String name, String pageUrl, String downloadUrl, String filename, String type) {
            this.id = id; this.name = name; this.pageUrl = pageUrl;
            this.downloadUrl = downloadUrl; this.filename = filename; this.type = type;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int color = isInstalled ? 0x55FF55 : 0xFFFFFF;
            String typePrefix = type.equalsIgnoreCase("mod") ? "[MOD] " : "[PACK] ";
            String status = isInstalled ? "§a[Installed]" : "§c[Missing]";
            
            graphics.drawString(font, typePrefix + name, left + 5, top + 5, color);
            graphics.drawString(font, status, left + width - font.width(status) - 10, top + 5, 0xFFFFFF);
            
            graphics.renderOutline(left, top, width, height - 2, 0xFF444444);
        }

        @Override
        public Component getNarration() { return Component.literal(name); }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            depList.setSelected(this);
            return true;
        }
    }
}