package me.cryo.zombierool;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MapDownloaderScreen extends Screen {

    private static final String OFFICIAL_JSON_URL  = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/maps.json";
    private static final String COMMUNITY_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-community-hub/main/maps.json";
    private static final String FEATURED_JSON_URL  = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/featured.json";
    private static final String GITHUB_OFFICIAL_URL = "https://github.com/Cryo60/zombierool-maps";
    private static final String PUBLISH_APP_URL    = "https://zombierool-community.onrender.com";
    private static final String DISCORD_MESSAGE    = "Join our Discord for community-made maps!";
    private static final String DISCORD_INVITE     = "https://discord.gg/QxsqH2FhFq";

    private static final int  CONNECT_TIMEOUT  = 15000;
    private static final int  READ_TIMEOUT     = 30000;

    private static final int C_BG_DARK      = 0xCC000000;
    private static final int C_BG_CARD      = 0x88000000;
    private static final int C_BG_CARD_SEL  = 0x0AFFCC00;
    private static final int C_BG_GRID      = 0x07006480;
    private static final int C_BORDER_CYAN_DIM  = 0x26006480;
    private static final int C_BORDER_GOLD      = 0x59FFCC00;
    private static final int C_BORDER_CARD      = 0x12FFFFFF;
    private static final int C_BORDER_CARD_SEL  = 0x66FFCC00;
    private static final int C_ACCENT_INSTALLED = 0xFF44FF88;
    private static final int C_ACCENT_UPDATE    = 0xFFFFAA00;

    private static final int C_TEXT_WHITE    = 0xFFFFFFFF;
    private static final int C_TEXT_MED      = 0xFF888888;
    private static final int C_TEXT_DARK     = 0xFF444444;
    private static final int C_TEXT_CYAN     = 0xFF00CCFF;
    private static final int C_TEXT_GOLD     = 0xFFFFCC00;
    private static final int C_TEXT_GREEN    = 0xFF44FF88;
    private static final int C_TEXT_ORANGE   = 0xFFFFAA00;
    private static final int C_TEXT_STARS    = 0xFFFFCC00;
    private static final int C_TEXT_DL       = 0xFF555555;
    private static final int C_TEXT_VERSION  = 0xFF3A3A3A;
    private static final int C_TEXT_DISCORD  = 0xFF5566FF;

    private static final int C_BTN_INSTALL   = 0xCC22C55E;
    private static final int C_BTN_UPDATE    = 0xCCD97706;
    private static final int C_BTN_DONE      = 0xCC50505A;
    private static final int C_BTN_TXT       = 0xFFFFFFFF;
    private static final int C_BTN_TXT_DONE  = 0xFFBBBBBB;

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

    private int bodyY, listY, listBottom, progressY, bottomY, contentX, contentW;

    private final Screen lastScreen;
    private MapList mapList;
    private Button officialTabButton, communityTabButton, sortButton, openGithubButton, publishButton, backButton;
    private EditBox searchBox;

    private List<MapEntry> allLoadedMaps  = new ArrayList<>();
    private List<MapEntry> filteredMaps   = new ArrayList<>();
    private MapEntry featuredMap          = null;

    private String officialFeaturedId     = "";
    private String communityFeaturedId    = "";

    private boolean loading              = true;
    private boolean loadingFeatured      = true;
    private String  errorMessage         = null;

    private int discordLinkX, discordLinkY, discordLinkWidth;
    private boolean isOfficialTab = true;
    private int currentSortIndex = 0;

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
        if (!me.cryo.zombierool.configuration.ZRClientConfig.allowNetworkRequests()) {
            this.errorMessage = Component.translatable("gui.zombierool.network.disabled").getString();
            this.loading = false;
            this.loadingFeatured = false;
        }

        bodyY      = H_TOPBAR + H_TABS;
        bottomY    = this.height - H_BOTTOMBAR;
        progressY  = bottomY - H_PROGRESS;
        listBottom = progressY - H_BODY_PAD;
        listY      = bodyY + H_BODY_PAD + H_FEATURED + CARD_GAP;

        int pad    = Math.max(8, Math.min(20, (int)(this.width * 0.02f)));
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

        int btnCount = 3;
        int btnW     = Math.min(100, (contentW - (btnCount - 1) * 6) / btnCount);
        int bY       = bottomY + 5;

        this.openGithubButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.github"),
                btn -> { playSound(); Util.getPlatform().openUri(GITHUB_OFFICIAL_URL); })
                .bounds(contentX, bY, btnW, 20).build());

        this.publishButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.publish"),
                btn -> { playSound(); Util.getPlatform().openUri(PUBLISH_APP_URL); })
                .bounds(contentX + btnW + 6, bY, btnW, 20).build());

        this.backButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.zombierool.downloader.back"),
                btn -> { playSound(); this.minecraft.setScreen(lastScreen); })
                .bounds(contentX + (btnW + 6) * 2, bY, btnW, 20).build());

        if (me.cryo.zombierool.configuration.ZRClientConfig.allowNetworkRequests()) {
            loadFeaturedData();
            switchTab(isOfficialTab);
        } else {
            updateTabStates();
        }
    }

    private void playSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
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

        if (me.cryo.zombierool.configuration.ZRClientConfig.allowNetworkRequests()) {
            loadMapsFromUrl(isOfficialTab ? OFFICIAL_JSON_URL : COMMUNITY_JSON_URL);
        } else {
            this.loading = false;
            this.errorMessage = Component.translatable("gui.zombierool.network.disabled").getString();
        }
    }

    private void updateTabStates() {
        if (officialTabButton   != null) officialTabButton.active   = !isOfficialTab;
        if (communityTabButton  != null) communityTabButton.active  = isOfficialTab;
        if (openGithubButton    != null) openGithubButton.visible   = isOfficialTab;
        if (publishButton       != null) publishButton.visible      = !isOfficialTab;
    }

    private void loadFeaturedData() {
        new Thread(() -> {
            try {
                String urlWithAntiCache = FEATURED_JSON_URL + "?t=" + System.currentTimeMillis();
                HttpURLConnection conn = (HttpURLConnection) new URL(urlWithAntiCache).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                
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
    }

    private void loadMapsFromUrl(String jsonUrl) {
        new Thread(() -> {
            try {
                System.setProperty("java.net.preferIPv4Stack", "true");
                String urlWithAntiCache = jsonUrl + (jsonUrl.contains("?") ? "&t=" : "?t=") + System.currentTimeMillis();
                HttpURLConnection conn = (HttpURLConnection) new URL(urlWithAntiCache).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

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
                    checkDownloadedMaps();
                    resolveFeaturedMap();
                    filterMaps();
                });

            } catch (Exception e) {
                errorMessage = "Failed to load maps: " + e.getMessage();
                loading = false;
            }
        }).start();
    }

    private void downloadImageAsync(MapEntry entry) {
        if (entry.imageUrl == null || entry.imageUrl.isEmpty()) return;
        if (imageCache.containsKey(entry.id)) return;

        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(entry.imageUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
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
        Gson gson = new Gson();
        for (MapEntry map : allLoadedMaps) {
            checkStateForMap(map, savesDir, gson);
        }
        if (featuredMap != null) {
            checkStateForMap(featuredMap, savesDir, gson);
        }
    }

    private void checkStateForMap(MapEntry mapObj, File savesDir, Gson gson) {
        File mapDir = new File(savesDir, mapObj.name);
        if (mapDir.exists() && mapDir.isDirectory()) {
            File vFile = new File(mapDir, "zombierool_map.json");
            if (vFile.exists()) {
                try (FileReader reader = new FileReader(vFile)) {
                    JsonObject vJson = gson.fromJson(reader, JsonObject.class);
                    String localVer = vJson.has("version") ? vJson.get("version").getAsString() : "";
                    
                    if (mapObj.version != null && !mapObj.version.isEmpty() && !localVer.equalsIgnoreCase(mapObj.version)) {
                        mapObj.state = MapInstallState.UPDATE_AVAILABLE;
                    } else {
                        mapObj.state = MapInstallState.INSTALLED;
                    }
                } catch (Exception e) {
                    mapObj.state = MapInstallState.INSTALLED;
                }
            } else {
                mapObj.state = MapInstallState.INSTALLED; // Dossier détecté mais pas de json, on suppose installé
            }
        } else {
            mapObj.state = MapInstallState.NOT_INSTALLED;
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

    private void openMapLinkInBrowser(MapEntry map) {
        if (map.downloadUrl != null && !map.downloadUrl.isEmpty()) {
            // Incrémente le compteur silencieusement via l'API avant d'ouvrir le navigateur
            recordDownloadOnServer(map.id);
            Util.getPlatform().openUri(map.downloadUrl);
        }
    }

    private void recordDownloadOnServer(String mapId) {
        new Thread(() -> {
            try {
                URL url = new URL(PUBLISH_APP_URL + "/api/map/" + mapId + "/download");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET"); // Ajuste en "POST" si ton API l'exige
                conn.setRequestProperty("User-Agent", "ZombieRool-Mod/1.0 (Minecraft Forge)");
                conn.setConnectTimeout(10000);
                conn.getResponseCode(); // Exécute la requête
                conn.disconnect();
            } catch (Exception ignored) {
                // Échec silencieux
            }
        }).start();
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
                && mouseY >= featBtnY && mouseY <= featBtnY + FEAT_BTN_H) {
                playSound();
                openMapLinkInBrowser(featuredMap);
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

        renderProgressRow(g, mouseX, mouseY);
        renderBottomBar(g);
    }

    private void renderBGGrid(GuiGraphics g) {
        for (int gx = 0; gx < this.width;  gx += 40) g.fill(gx, 0, gx + 1, this.height, C_BG_GRID);
        for (int gy = 0; gy < this.height; gy += 40) g.fill(0, gy, this.width, gy + 1,   C_BG_GRID);
    }

    private void renderTopBar(GuiGraphics g) {
        g.fill(0, 0, this.width, H_TOPBAR, C_BG_DARK);
        g.fill(0, H_TOPBAR - 1, this.width, H_TOPBAR, C_BORDER_CYAN_DIM);
        g.drawCenteredString(font, "MAP CATALOG", this.width / 2, H_TOPBAR / 2 - 4, C_TEXT_GOLD);
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
                            : featuredMap.state == MapInstallState.INSTALLED ? " [INSTALLED]" : "";
        int nameColor = featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? C_TEXT_ORANGE
                      : featuredMap.state == MapInstallState.INSTALLED ? C_TEXT_GREEN : C_TEXT_WHITE;
                      
        String displayName = featuredMap.name + statusSuffix;
        
        while (font.width(displayName) > tW && displayName.length() > 4)
            displayName = displayName.substring(0, displayName.length() - 1);
            
        g.drawString(font, displayName, tX, tY, nameColor);
        tY += 12;

        String desc = font.plainSubstrByWidth(featuredMap.description, tW);
        g.drawString(font, desc, tX, tY, C_TEXT_MED);
        tY += 11;

        String meta = "MC: " + featuredMap.gameVersion + "  ·  v" + featuredMap.version + "  ·  ⬇ " + featuredMap.downloads;
        g.drawString(font, meta, tX, tY, C_TEXT_DARK);

        int btnX = fX + fW - FEAT_BTN_W - 10;
        int btnY = fY + fH - FEAT_BTN_H - 10;
        boolean btnHov = mouseX >= btnX && mouseX <= btnX + FEAT_BTN_W
                      && mouseY >= btnY && mouseY <= btnY + FEAT_BTN_H;

        int btnColor = featuredMap.state == MapInstallState.INSTALLED ? C_BTN_DONE : 
                       (featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? (btnHov ? 0xCCD97706 : C_BTN_UPDATE) :
                       (btnHov ? 0xCC16A34A : C_BTN_INSTALL));
                       
        g.fill(btnX, btnY, btnX + FEAT_BTN_W, btnY + FEAT_BTN_H, btnColor);

        String btnTxt = featuredMap.state == MapInstallState.UPDATE_AVAILABLE ? "Get Update" : "Open Link";
        int txtColor = featuredMap.state == MapInstallState.INSTALLED ? C_BTN_TXT_DONE : C_BTN_TXT;
        g.drawCenteredString(font, btnTxt, btnX + FEAT_BTN_W / 2, btnY + FEAT_BTN_H / 2 - 4, txtColor);
    }

    private void renderProgressRow(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, progressY, this.width, progressY + H_PROGRESS, 0x88000000);
        g.fill(0, progressY, this.width, progressY + 1, C_BORDER_CYAN_DIM);
        
        int cy = progressY + H_PROGRESS / 2 - 4;
        
        String infoMsg = "⚠ CurseForge Security: Maps must be downloaded via your Web Browser.";
        g.drawCenteredString(font, infoMsg, this.width / 2, cy - 8, 0xFFFFAA00);

        discordLinkWidth = font.width(DISCORD_MESSAGE);
        discordLinkX     = (this.width - discordLinkWidth) / 2;
        discordLinkY     = cy + 4;
        boolean hov = mouseCheck(discordLinkX, discordLinkY, discordLinkWidth, 9);
                   
        g.drawCenteredString(font, DISCORD_MESSAGE, this.width / 2, cy + 4, hov ? 0xFF7777FF : C_TEXT_DISCORD);
        if (hov) g.fill(discordLinkX, cy + 13, discordLinkX + discordLinkWidth, cy + 14, 0xFF5566FF);
    }

    private int lastMouseX = 0, lastMouseY = 0;
    private boolean mouseCheck(int x, int y, int w, int h) {
        return lastMouseX >= x && lastMouseX <= x + w && lastMouseY >= y && lastMouseY <= y + h;
    }

    private void renderBottomBar(GuiGraphics g) {
        g.fill(0, bottomY, this.width, this.height, C_BG_DARK);
        g.fill(0, bottomY, this.width, bottomY + 1, 0x14FFFFFF);
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

        @Override public int getRowWidth() { return Math.min(contentW, this.width - 12); }
        @Override protected int getScrollbarPosition() { return this.width / 2 + getRowWidth() / 2 + 4; }
        @Override protected void renderBackground(GuiGraphics g) {  }
    }

    private class MapEntry extends ObjectSelectionList.Entry<MapEntry> {
        public final String id, name, description, downloadUrl;
        public final String author, imageUrl;
        public final List<String> tags;
        public final float avgStars;
        public final int   reviewsCount;
        public final String gameVersion, zrVersion, version;
        public final int  downloads;
        public final long timestamp;
        
        public MapInstallState state = MapInstallState.NOT_INSTALLED;

        public MapEntry(String id, String name, String description, String downloadUrl,
                        String author, String imageUrl, List<String> tags,
                        float avgStars, int reviewsCount,
                        String gameVersion, String zrVersion, String version,
                        int downloads, long timestamp) {
            this.id = id; this.name = name; this.description = description;
            this.downloadUrl = downloadUrl; 
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

            int btnColor = state == MapInstallState.INSTALLED ? C_BTN_DONE : 
                          (state == MapInstallState.UPDATE_AVAILABLE ? (btnHov ? 0xCCD97706 : C_BTN_UPDATE) :
                          (btnHov ? 0xCC16A34A : C_BTN_INSTALL));
                          
            g.fill(btnX, btnY, btnX + CARD_BTN_W, btnY + CARD_BTN_H, btnColor);

            String btnTxt = state == MapInstallState.UPDATE_AVAILABLE ? "Get Update" : "Open Link";
            int txtColor = state == MapInstallState.INSTALLED ? C_BTN_TXT_DONE : C_BTN_TXT;
            g.drawCenteredString(font, btnTxt, btnX + CARD_BTN_W / 2, btnY + CARD_BTN_H / 2 - 4, txtColor);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            mapList.setSelected(this);
            int rowW  = mapList.getRowWidth();
            int left  = MapDownloaderScreen.this.width / 2 - rowW / 2;
            int btnX  = left + rowW - CARD_BTN_W - 8;

            if (mouseX >= btnX && mouseX <= btnX + CARD_BTN_W) {
                playSound();
                openMapLinkInBrowser(this);
            }
            return true;
        }

        @Override public Component getNarration() { return Component.literal(name); }
    }
}