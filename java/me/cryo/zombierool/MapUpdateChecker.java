package me.cryo.zombierool.client;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Mod.EventBusSubscriber(modid = "zombierool", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class MapUpdateChecker {
    public static boolean hasUpdates = false;
    private static boolean checked = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!checked && event.phase == TickEvent.Phase.END) {
            checked = true;
            checkForUpdates();
        }
    }

    private static void checkForUpdates() {
        new Thread(() -> {
            try {
                URL url = new URL("https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/maps.json");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) response.append(line);
                reader.close();
                
                Gson gson = new Gson();
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                JsonArray mapsArray = json.getAsJsonArray("maps");

                File savesDir = new File(Minecraft.getInstance().gameDirectory, "saves");
                
                for (int i = 0; i < mapsArray.size(); i++) {
                    JsonObject mapObj = mapsArray.get(i).getAsJsonObject();
                    String name = mapObj.get("name").getAsString();
                    String sha256 = mapObj.has("sha256") ? mapObj.get("sha256").getAsString() : null;
                    
                    File mapDir = new File(savesDir, name);
                    if (!mapDir.exists()) {
                        hasUpdates = true;
                        return;
                    }
                    
                    File newVFile = new File(mapDir, "zombierool_map.json");
                    File oldVFile = new File(mapDir, "zombierool_version.json");
                    File targetFile = newVFile.exists() ? newVFile : (oldVFile.exists() ? oldVFile : null);

                    if (targetFile == null && sha256 != null && !sha256.isEmpty()) {
                        hasUpdates = true;
                        return;
                    }
                    
                    if (targetFile != null) {
                        try (FileReader vReader = new FileReader(targetFile)) {
                            JsonObject vJson = gson.fromJson(vReader, JsonObject.class);
                            String localSha = vJson.has("sha256") ? vJson.get("sha256").getAsString() : "";
                            if (sha256 != null && !sha256.isEmpty() && !localSha.equalsIgnoreCase(sha256)) {
                                hasUpdates = true;
                                return;
                            }
                        }
                    }
                }
            } catch (Exception e) {}
        }).start();
    }

    @SubscribeEvent
    public static void onRenderScreen(ScreenEvent.Render.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (hasUpdates && mc.level == null) {
            Screen screen = event.getScreen();
            if (!(screen instanceof me.cryo.zombierool.MapDownloaderScreen)) {
                GuiGraphics g = event.getGuiGraphics();
                int width = screen.width;
                String text = "§e(!) New Map / Update Available!";
                g.drawString(mc.font, text, width - mc.font.width(text) - 5, 5, 0xFFFFFF);
            }
        }
    }
}