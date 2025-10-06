package net.mcreator.zombierool;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.loading.FMLPaths;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WorldMapLoader {

    private static final String MAPS_JSON_URL = "https://raw.githubusercontent.com/Cryo60/zombierool-maps/main/maps.json";
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

    private static void downloadAndInstallNacht() {
        new Thread(() -> {
            try {
                if (mapExists()) {
                    System.out.println("[ZombieRool] Nacht der Untoten already exists, download skipped.");
                    return;
                }

                System.out.println("[ZombieRool] Nacht der Untoten not found, downloading from GitHub...");

                // Load maps.json
                URL jsonUrl = new URL(MAPS_JSON_URL);
                HttpURLConnection jsonConn = (HttpURLConnection) jsonUrl.openConnection();
                jsonConn.setRequestMethod("GET");
                jsonConn.setConnectTimeout(10000);
                jsonConn.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(new InputStreamReader(jsonConn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                Gson gson = new Gson();
                JsonObject json = gson.fromJson(response.toString(), JsonObject.class);
                JsonArray mapsArray = json.getAsJsonArray("maps");

                // Find Nacht der Untoten
                String downloadUrl = null;
                for (int i = 0; i < mapsArray.size(); i++) {
                    JsonObject mapObj = mapsArray.get(i).getAsJsonObject();
                    if (TARGET_MAP_ID.equals(mapObj.get("id").getAsString())) {
                        downloadUrl = mapObj.get("download_url").getAsString();
                        break;
                    }
                }

                if (downloadUrl == null) {
                    System.err.println("[ZombieRool] Nacht der Untoten not found in maps.json");
                    return;
                }

                System.out.println("[ZombieRool] Downloading from: " + downloadUrl);

                // Download the map
                URL mapUrl = new URL(downloadUrl);
                HttpURLConnection mapConn = (HttpURLConnection) mapUrl.openConnection();
                mapConn.setRequestMethod("GET");
                mapConn.setInstanceFollowRedirects(true);
                mapConn.setConnectTimeout(30000);
                mapConn.setReadTimeout(30000);
                mapConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                mapConn.setRequestProperty("Accept", "*/*");

                mapConn.connect();

                int responseCode = mapConn.getResponseCode();
                
                if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP || 
                    responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
                    responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                    String newUrl = mapConn.getHeaderField("Location");
                    mapConn = (HttpURLConnection) new URL(newUrl).openConnection();
                    mapConn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                    mapConn.connect();
                    responseCode = mapConn.getResponseCode();
                }

                if (responseCode != HttpURLConnection.HTTP_OK) {
                    System.err.println("[ZombieRool] Download failed: HTTP " + responseCode);
                    return;
                }

                // Save to temp file
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
                        if (totalRead % 500000 == 0) {
                            System.out.println("[ZombieRool] Downloaded: " + (totalRead / 1024) + " KB");
                        }
                    }
                }

                System.out.println("[ZombieRool] Download complete, extracting...");

                // Extract to saves
                Path savesPath = FMLPaths.GAMEDIR.get().resolve("saves");
                if (!Files.exists(savesPath)) {
                    Files.createDirectories(savesPath);
                }

                File targetDir = new File(savesPath.toFile(), TARGET_MAP_NAME);
                extractZip(zipFile, targetDir);

                // Cleanup
                zipFile.delete();
                tempDir.delete();

                System.out.println("[ZombieRool] Nacht der Untoten installed successfully!");

            } catch (java.net.SocketTimeoutException e) {
                System.err.println("[ZombieRool] Connection timeout - Nacht der Untoten download failed");
                System.err.println("[ZombieRool] You can download it manually from the Official Maps menu");
            } catch (Exception e) {
                System.err.println("[ZombieRool] Error downloading Nacht der Untoten: " + e.getMessage());
                System.err.println("[ZombieRool] You can download it manually from the Official Maps menu");
                e.printStackTrace();
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