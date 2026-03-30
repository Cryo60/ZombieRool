package me.cryo.zombierool.client.career;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class RedeemCodeManager {

    private static final String GIST_URL = "https://gist.githubusercontent.com/Cryo60/3ee5548979c4da44701c65758bf03505/raw/codes.json";

    public static void redeemCode(String code, Consumer<Component> callback) {
        if (code == null || code.trim().isEmpty()) {
            callback.accept(Component.translatable("message.zombierool.redeem.invalid"));
            return;
        }

        String cleanCode = code.trim().toUpperCase();

        if (LocalCareerManager.getData().redeemedCodes.contains(cleanCode)) {
            callback.accept(Component.translatable("message.zombierool.redeem.already_used"));
            return;
        }

        // Exécution de la requête en arrière-plan
        new Thread(() -> {
            try {
                URL url = new URL(GIST_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");

                if (conn.getResponseCode() != 200) {
                    throw new Exception("HTTP Error: " + conn.getResponseCode());
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();

                if (!root.has(cleanCode)) {
                    Minecraft.getInstance().execute(() -> callback.accept(Component.translatable("message.zombierool.redeem.invalid")));
                    return;
                }

                JsonObject reward = root.getAsJsonObject(cleanCode);

                // FIX CRASH: On repasse sur le Thread Principal (Main Thread) pour modifier le joueur et l'interface !
                Minecraft.getInstance().execute(() -> {
                    // Appliquer les récompenses (ZRF)
                    int zrfAdded = 0;
                    if (reward.has("zrf")) {
                        zrfAdded = reward.get("zrf").getAsInt();
                        LocalCareerManager.addZRF(zrfAdded, "");
                    }

                    // Appliquer les camos spécifiques
                    List<String> unlockedCamos = new ArrayList<>();
                    if (reward.has("camos")) {
                        JsonArray camosArray = reward.getAsJsonArray("camos");
                        for (int i = 0; i < camosArray.size(); i++) {
                            String camoId = camosArray.get(i).getAsString();
                            LocalCareerManager.forceUnlockCamoGlobal(camoId);
                            unlockedCamos.add(camoId);
                        }
                    }

                    // Camos aléatoires par rareté
                    if (reward.has("random_camo")) {
                        JsonObject randomConfig = reward.getAsJsonObject("random_camo");
                        String rarity = randomConfig.has("rarity") ? randomConfig.get("rarity").getAsString() : "common";
                        int count = randomConfig.has("count") ? randomConfig.get("count").getAsInt() : 1;

                        List<String> randomUnlocks = getRandomCamos(rarity, count);
                        for (String rCamo : randomUnlocks) {
                            LocalCareerManager.forceUnlockCamoGlobal(rCamo);
                            unlockedCamos.add(rCamo);
                        }
                    }

                    LocalCareerManager.getData().redeemedCodes.add(cleanCode);
                    LocalCareerManager.save();

                    callback.accept(Component.translatable("message.zombierool.redeem.success", zrfAdded, unlockedCamos.size()));
                });

            } catch (Exception e) {
                e.printStackTrace();
                Minecraft.getInstance().execute(() -> callback.accept(Component.translatable("message.zombierool.redeem.error")));
            }
        }).start();
    }

    private static List<String> getRandomCamos(String rarity, int count) {
        List<String> available = new ArrayList<>();
        
        for (Map.Entry<String, LocalCareerManager.CamoDef> entry : LocalCareerManager.CAMOS.entrySet()) {
            if (entry.getValue().rarity != null && entry.getValue().rarity.equalsIgnoreCase(rarity) && !entry.getValue().isPrestige) {
                if (!LocalCareerManager.getData().globalUnlockedCamos.contains(entry.getKey())) {
                    available.add(entry.getKey());
                }
            }
        }
        
        Collections.shuffle(available);
        return available.subList(0, Math.min(count, available.size()));
    }
}