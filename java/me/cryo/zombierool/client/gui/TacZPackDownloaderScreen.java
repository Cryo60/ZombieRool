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
import net.minecraft.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class TacZPackDownloaderScreen extends Screen {

    private static final String DEPS_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/refs/heads/main/dependencies.json";
    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 60000;

    private final Screen lastScreen;
    private DependencyList depList;
    private Button openBrowserButton;
    private Button backButton;

    private List<DependencyEntry> dependencies = new ArrayList<>();
    private boolean loading = true;
    private String errorMessage = null;

    public TacZPackDownloaderScreen(Screen lastScreen) {
        super(Component.translatable("gui.zombierool.tacz.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        if (!me.cryo.zombierool.configuration.ZRClientConfig.allowNetworkRequests()) {
            this.errorMessage = Component.translatable("gui.zombierool.network.disabled").getString();
            this.loading = false;
        } else {
            loadDependencies();
        }

        int listTop = 45;
        int buttonsPerRow = 2;
        int spacing = 5;
        int buttonWidth = 150;
        int buttonHeight = 20;
        int listHeight = this.height - listTop - buttonHeight - 30;

        this.depList = new DependencyList(this.minecraft, this.width, listHeight, listTop, listTop + listHeight, 20);
        this.addWidget(this.depList);

        int startX = (this.width - (buttonsPerRow * buttonWidth + (buttonsPerRow - 1) * spacing)) / 2;
        int startY = this.height - buttonHeight - 20;

        // Bouton pour ouvrir le lien du mod/pack sélectionné
        this.openBrowserButton = Button.builder(Component.translatable("gui.zombierool.tacz.manual"), btn -> {
            playSound();
            openSelectedLink();
        }).bounds(startX, startY, buttonWidth, buttonHeight).build();

        this.backButton = Button.builder(Component.translatable("gui.zombierool.downloader.back"), btn -> {
            playSound();
            this.minecraft.setScreen(lastScreen);
        }).bounds(startX + buttonWidth + spacing, startY, buttonWidth, buttonHeight).build();

        this.addRenderableWidget(openBrowserButton);
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
                conn.setRequestProperty("User-Agent", "ZombieRool-Mod/1.0 (Minecraft Forge)");
                conn.setRequestProperty("Accept", "application/json");
                conn.connect();

                int responseCode = conn.getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = conn.getHeaderField("Location");
                    conn = (HttpURLConnection) new URL(newUrl).openConnection();
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                    conn.connect();
                    responseCode = conn.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    throw new java.io.IOException("HTTP " + responseCode);
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // L'objet racine du nouveau JSON est un objet qui contient un array "dependencies"
                JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
                JsonArray array = root.getAsJsonArray("dependencies");
                
                dependencies.clear();

                for (JsonElement elem : array) {
                    JsonObject obj = elem.getAsJsonObject();
                    String id = obj.get("id").getAsString();
                    String name = obj.get("name").getAsString();
                    String pageUrl = obj.has("page_url") ? obj.get("page_url").getAsString() : "";
                    String type = obj.get("type").getAsString();
                    
                    DependencyEntry entry = new DependencyEntry(id, name, pageUrl, type);
                    boolean installed = false;

                    File gameDir = Minecraft.getInstance().gameDirectory;

                    if (entry.type.equalsIgnoreCase("mod") && entry.id.equalsIgnoreCase("tacz")) {
                        installed = ModList.get().isLoaded("tacz");
                    } else if (entry.type.equalsIgnoreCase("mod")) {
                        File modsDir = new File(gameDir, "mods");
                        File[] files = modsDir.listFiles();
                        if (files != null) {
                            for (File f : files) {
                                if (f.getName().toLowerCase().contains(entry.id.toLowerCase())) {
                                    installed = true;
                                    break;
                                }
                            }
                        }
                    } else {
                        // Pour les gunpacks, on vérifie leur présence dans les dossiers tacz
                        File[] dirsToCheck = {
                            new File(gameDir, "tacz"),
                            new File(gameDir, "tacz/custom"),
                            new File(gameDir, "config/tacz/custom")
                        };
                        for (File d : dirsToCheck) {
                            if (installed) break;
                            File[] files = d.listFiles();
                            if (files != null) {
                                for (File f : files) {
                                    if (f.getName().toLowerCase().contains(entry.id.toLowerCase())) {
                                        installed = true;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    entry.isInstalled = installed;
                    dependencies.add(entry);
                }
                
                loading = false;
                this.minecraft.execute(() -> {
                    if (depList != null) {
                        depList.refreshList();
                        updateButtonStates();
                    }
                });

            } catch (Exception e) {
                errorMessage = "Failed to load dependencies. Check your connection.";
                loading = false;
                e.printStackTrace();
            }
        }).start();
    }

    private void updateButtonStates() {
        DependencyEntry selected = depList != null ? depList.getSelected() : null;
        if (openBrowserButton != null) {
            openBrowserButton.active = selected != null && selected.pageUrl != null && !selected.pageUrl.isEmpty();
            if (selected != null && selected.isInstalled) {
                openBrowserButton.setMessage(Component.literal("Already Installed (Open Link)"));
            } else {
                openBrowserButton.setMessage(Component.literal("Open CurseForge Link"));
            }
        }
    }

    private void playSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ZombieroolModSounds.UI_CHOOSE.get(), 1.0F));
    }

    private void openSelectedLink() {
        DependencyEntry selected = depList.getSelected();
        if (selected != null && selected.pageUrl != null && !selected.pageUrl.isEmpty()) {
            Util.getPlatform().openUri(selected.pageUrl);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFFF);

        if (!ModList.get().isLoaded("tacz") && !loading) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.tacz.warning"), this.width / 2, 25, 0xFF5555);
        }

        if (loading) {
            graphics.drawCenteredString(this.font, Component.translatable("gui.zombierool.downloader.loading"), this.width / 2, this.height / 2, 0xAAAAAA);
        } else if (errorMessage != null) {
            graphics.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2, 0xFF5555);
        } else {
            this.depList.render(graphics, mouseX, mouseY, partialTick);
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
        public final String id, name, pageUrl, type;
        public boolean isInstalled = false;

        public DependencyEntry(String id, String name, String pageUrl, String type) {
            this.id = id; 
            this.name = name; 
            this.pageUrl = pageUrl;
            this.type = type;
        }

        @Override
        public void render(GuiGraphics graphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
            int color = isInstalled ? 0x55FF55 : 0xFFFFFF;
            String typePrefix = type.equalsIgnoreCase("mod") ? "[MOD] " : "[PACK] ";
            String status = isInstalled ? "Installed" : "Click to get";
            
            graphics.drawString(font, typePrefix + name, left + 5, top + 5, color);
            graphics.drawString(font, status, left + width - font.width(status) - 10, top + 5, 0xAAAAAA);
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