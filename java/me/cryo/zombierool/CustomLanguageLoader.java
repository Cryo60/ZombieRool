package me.cryo.zombierool.client;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(modid = "zombierool", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class CustomLanguageLoader extends SimplePreparableReloadListener<Map<String, String>> {
    private static final Map<String, String> CUSTOM_TRANSLATIONS = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    @SubscribeEvent
    public static void onRegisterReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new CustomLanguageLoader());
    }

    public static String getCustomTranslation(String key) {
        return CUSTOM_TRANSLATIONS.get(key);
    }

    public static boolean hasCustomTranslation(String key) {
        return CUSTOM_TRANSLATIONS.containsKey(key);
    }

    @Override
    protected Map<String, String> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<String, String> map = new ConcurrentHashMap<>();
        loadLangFile("en_us", map, resourceManager);

        String currentLang = Minecraft.getInstance().options.languageCode;
        if (currentLang != null && !currentLang.equals("en_us")) {
            loadLangFile(currentLang, map, resourceManager);
        }

        return map;
    }

    private void loadLangFile(String langCode, Map<String, String> map, ResourceManager resourceManager) {
        ResourceLocation path = new ResourceLocation("zombierool", "custom_lang/" + langCode + ".json");
        try {
            Optional<Resource> resource = resourceManager.getResource(path);
            if (resource.isPresent()) {
                try (InputStream is = resource.get().open();
                     InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                            map.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[ZombieRool] Error reading language file: " + path.toString());
            e.printStackTrace();
        }
    }

    @Override
    protected void apply(Map<String, String> preparedMap, ResourceManager resourceManager, ProfilerFiller profiler) {
        CUSTOM_TRANSLATIONS.clear();
        CUSTOM_TRANSLATIONS.putAll(preparedMap);
        System.out.println("[ZombieRool] " + CUSTOM_TRANSLATIONS.size() + " custom translations loaded from custom_lang!");
    }
}