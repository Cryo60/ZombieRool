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
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public class RedeemCodeManager {

    private static final String API_REDEEM_URL = "https://zombierool-community.onrender.com/api/redeem";

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

        new Thread(() -> {
            try {
                URL url = new URL(API_REDEEM_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                String jsonInputString = "{\"code\": \"" + cleanCode + "\"}";
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                if (conn.getResponseCode() != 200) {
                    Minecraft.getInstance().execute(() -> callback.accept(Component.translatable("message.zombierool.redeem.invalid")));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JsonObject root = JsonParser.parseString(response.toString()).getAsJsonObject();
                if (!root.has("success") || !root.get("success").getAsBoolean()) {
                    Minecraft.getInstance().execute(() -> callback.accept(Component.translatable("message.zombierool.redeem.invalid")));
                    return;
                }

                JsonObject reward = root.getAsJsonObject("reward");

                Minecraft.getInstance().execute(() -> {
                    int zrfAdded = 0;
                    if (reward.has("zrf")) {
                        zrfAdded = reward.get("zrf").getAsInt();
                        LocalCareerManager.addZRF(zrfAdded, "");
                    }

                    List<String> unlockedItems = new ArrayList<>();
                    
                    if (reward.has("camos")) {
                        JsonArray camosArray = reward.getAsJsonArray("camos");
                        for (int i = 0; i < camosArray.size(); i++) {
                            String camoId = camosArray.get(i).getAsString();
                            LocalCareerManager.forceUnlockCamoGlobal(camoId);
                            unlockedItems.add(camoId);
                        }
                    }

                    if (reward.has("skins")) {
                        JsonArray skinsArray = reward.getAsJsonArray("skins");
                        for (int i = 0; i < skinsArray.size(); i++) {
                            String skinId = skinsArray.get(i).getAsString();
                            if (!LocalCareerManager.getData().unlockedSkins.contains(skinId)) {
                                LocalCareerManager.getData().unlockedSkins.add(skinId);
                            }
                            unlockedItems.add(skinId);
                        }
                    }

                    if (reward.has("random_camo")) {
                        JsonObject randomConfig = reward.getAsJsonObject("random_camo");
                        String rarity = randomConfig.has("rarity") ? randomConfig.get("rarity").getAsString() : "common";
                        int count = randomConfig.has("count") ? randomConfig.get("count").getAsInt() : 1;
                        List<String> randomUnlocks = getRandomCamos(rarity, count);
                        for (String rCamo : randomUnlocks) {
                            LocalCareerManager.forceUnlockCamoGlobal(rCamo);
                            unlockedItems.add(rCamo);
                        }
                    }

                    if (reward.has("random_skin")) {
                        JsonObject randomConfig = reward.getAsJsonObject("random_skin");
                        int count = randomConfig.has("count") ? randomConfig.get("count").getAsInt() : 1;
                        List<String> randomUnlocks = getRandomSkins(count);
                        for (String rSkin : randomUnlocks) {
                            if (!LocalCareerManager.getData().unlockedSkins.contains(rSkin)) {
                                LocalCareerManager.getData().unlockedSkins.add(rSkin);
                            }
                            unlockedItems.add(rSkin);
                        }
                    }

                    LocalCareerManager.getData().redeemedCodes.add(cleanCode);
                    LocalCareerManager.save();

                    callback.accept(Component.translatable("message.zombierool.redeem.success", zrfAdded, unlockedItems.size()));
                });
            } catch (Exception e) {
                e.printStackTrace();
                Minecraft.getInstance().execute(() -> callback.accept(Component.translatable("message.zombierool.redeem.error")));
            }
        }).start();
    }

    private static List<String> getRandomCamos(String rarity, int count) {
        List<String> available = new ArrayList<>();
        for (Map.Entry<String, CareerUnlockables.CamoDef> entry : CareerUnlockables.CAMOS.entrySet()) {
            if (entry.getValue().rarity != null && entry.getValue().rarity.equalsIgnoreCase(rarity) && !entry.getValue().isPrestige) {
                if (!LocalCareerManager.getData().globalUnlockedCamos.contains(entry.getKey())) {
                    available.add(entry.getKey());
                }
            }
        }
        Collections.shuffle(available);
        return available.subList(0, Math.min(count, available.size()));
    }

    private static List<String> getRandomSkins(int count) {
        List<String> available = new ArrayList<>();
        for (Map.Entry<String, CareerUnlockables.SkinDef> entry : CareerUnlockables.SKINS.entrySet()) {
            if ("BUY".equals(entry.getValue().unlockType) && !LocalCareerManager.getData().unlockedSkins.contains(entry.getKey())) {
                available.add(entry.getKey());
            }
        }
        Collections.shuffle(available);
        return available.subList(0, Math.min(count, available.size()));
    }
}