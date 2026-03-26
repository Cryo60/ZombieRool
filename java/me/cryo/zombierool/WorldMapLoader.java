package me.cryo.zombierool;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WorldMapLoader {

    private static final String MAPS_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/refs/heads/main/maps.json";
    private static final String TARGET_MAP_ID = "zr_nacht";
    private static final String TARGET_MAP_NAME = "Nacht der Untoten";

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            downloadAndInstallNacht();
        });
    }

    private static boolean mapExists() {
        Path savesPath = FMLPaths.GAMEDIR.get().resolve("saves").resolve(TARGET_MAP_NAME);
        return Files.exists(savesPath) && Files.isDirectory(savesPath);
    }

    private static HttpURLConnection openConnectionWithRedirects(String urlString) throws Exception {
        urlString = urlString.replace(" ", "%20");
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(30000);
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
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setInstanceFollowRedirects(false);
            status = conn.getResponseCode();
            redirects++;
        }
        
        if (status >= 400) {
            if (status == 404 && urlString.endsWith(".json")) {
                throw new FileNotFoundException("JSON File not found");
            }
            throw new IOException("HTTP Error " + status + " on " + url.getFile());
        }
        
        return conn;
    }

    private static void downloadAndInstallNacht() {
        new Thread(() -> {
            try {
                if (mapExists()) {
                    System.out.println("[ZombieRool] Nacht der Untoten already exists, download skipped.");
                    return;
                }

                System.out.println("[ZombieRool] Nacht der Untoten not found, downloading from GitHub...");
                HttpURLConnection jsonConn = openConnectionWithRedirects(MAPS_JSON_URL);

                BufferedReader reader = new BufferedReader(new InputStreamReader(jsonConn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                jsonConn.disconnect();

                JsonObject json = new Gson().fromJson(response.toString(), JsonObject.class);
                JsonArray mapsArray = json.getAsJsonArray("maps");

                String downloadUrl = null;
                JsonObject targetMapObj = null;
                for (int i = 0; i < mapsArray.size(); i++) {
                    JsonObject mapObj = mapsArray.get(i).getAsJsonObject();
                    if (TARGET_MAP_ID.equals(mapObj.get("id").getAsString())) {
                        downloadUrl = mapObj.get("download_url").getAsString();
                        targetMapObj = mapObj;
                        break;
                    }
                }

                if (downloadUrl == null || targetMapObj == null) {
                    System.err.println("[ZombieRool] Nacht der Untoten not found in maps.json");
                    return;
                }

                System.out.println("[ZombieRool] Downloading from: " + downloadUrl);
                HttpURLConnection mapConn = openConnectionWithRedirects(downloadUrl);

                File tempDir = new File(FMLPaths.GAMEDIR.get().toFile(), "temp_downloads");
                tempDir.mkdirs();
                File zipFile = new File(tempDir, TARGET_MAP_NAME + ".zip");

                System.out.println("[ZombieRool] Downloading to: " + zipFile.getAbsolutePath());

                try (InputStream in = mapConn.getInputStream();
                     FileOutputStream out = new FileOutputStream(zipFile)) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    long totalRead = 0;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        totalRead += bytesRead;
                        if (totalRead % 1000000 == 0) {
                            System.out.println("[ZombieRool] Downloaded: " + (totalRead / 1024) + " KB");
                        }
                    }
                }
                mapConn.disconnect();

                System.out.println("[ZombieRool] Download complete, extracting...");

                Path savesPath = FMLPaths.GAMEDIR.get().resolve("saves");
                if (!Files.exists(savesPath)) {
                    Files.createDirectories(savesPath);
                }

                File targetDir = new File(savesPath.toFile(), TARGET_MAP_NAME);
                extractZip(zipFile, targetDir);
                
                File metaFile = new File(targetDir, "zombierool_map.json");
                JsonObject metaJson = new JsonObject();
                metaJson.addProperty("id", targetMapObj.get("id").getAsString());
                if (targetMapObj.has("version")) metaJson.addProperty("version", targetMapObj.get("version").getAsString());
                if (targetMapObj.has("sha256")) metaJson.addProperty("sha256", targetMapObj.get("sha256").getAsString());
                if (targetMapObj.has("resource_pack")) {
                    JsonObject rp = targetMapObj.getAsJsonObject("resource_pack");
                    if (rp.has("url")) metaJson.addProperty("resource_pack_url", rp.get("url").getAsString());
                    if (rp.has("name")) metaJson.addProperty("resource_pack_name", rp.get("name").getAsString());
                }
                try (FileWriter writer = new FileWriter(metaFile)) {
                    new GsonBuilder().setPrettyPrinting().create().toJson(metaJson, writer);
                }

                zipFile.delete();
                tempDir.delete();
                System.out.println("[ZombieRool] Nacht der Untoten installed successfully!");

            } catch (Exception e) {
                System.err.println("[ZombieRool] Error downloading Nacht der Untoten: " + e.getMessage());
            }
        }).start();
    }

    private static void extractZip(File zipFile, File destDir) throws IOException {
        if (!destDir.exists()) {
            destDir.mkdirs();
        }
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
}