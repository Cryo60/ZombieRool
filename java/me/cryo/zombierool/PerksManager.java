package me.cryo.zombierool;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; 
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

import me.cryo.zombierool.potion.PerksEffectPHDFlopperMobEffect;
import me.cryo.zombierool.potion.PerksEffectCherryMobEffect;
import me.cryo.zombierool.potion.PerksEffectMastodonteMobEffect;
import me.cryo.zombierool.potion.PerksEffectSpeedColaMobEffect;
import me.cryo.zombierool.potion.PerksEffectDoubleTapeMobEffect;
import me.cryo.zombierool.potion.PerksEffectBloodRageMobEffect;
import me.cryo.zombierool.potion.PerksEffectQuickReviveMobEffect;
import me.cryo.zombierool.potion.PerksEffectRoyalBeerMobEffect;
import me.cryo.zombierool.potion.PerksEffectVultureMobEffect;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class PerksManager {
    public static final DeferredRegister<MobEffect> REGISTRY = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, "zombierool");
    
    public static final RegistryObject<MobEffect> PERKS_EFFECT_PHD_FLOPPER = REGISTRY.register("perks_effect_phd_flopper", () -> new PerksEffectPHDFlopperMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_CHERRY = REGISTRY.register("perks_effect_cherry", () -> new PerksEffectCherryMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_MASTODONTE = REGISTRY.register("perks_effect_mastodonte", () -> new PerksEffectMastodonteMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_SPEED_COLA = REGISTRY.register("perks_effect_speed_cola", () -> new PerksEffectSpeedColaMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_DOUBLE_TAPE = REGISTRY.register("perks_effect_double_tape", () -> new PerksEffectDoubleTapeMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_BLOOD_RAGE = REGISTRY.register("perks_effect_blood_rage", () -> new PerksEffectBloodRageMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_QUICK_REVIVE = REGISTRY.register("perks_effect_quick_revive", () -> new PerksEffectQuickReviveMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_ROYAL_BEER = REGISTRY.register("perks_effect_royal_beer", () -> new PerksEffectRoyalBeerMobEffect());
    public static final RegistryObject<MobEffect> PERKS_EFFECT_VULTURE = REGISTRY.register("perks_effect_vulture", () -> new PerksEffectVultureMobEffect());
    
    // Utilisation de TreeMap pour garantir un ordre alphabétique constant (utile pour la Wunderfizz et le menu)
    public static final Map<String, Perk> ALL_PERKS = new TreeMap<>();
    
    public static final int MAX_PERKS_LIMIT = 4;
    private static final Map<UUID, Integer> playerQuickRevivePurchases = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerRoyalBeerPurchases = new ConcurrentHashMap<>();
    public static final int QUICK_REVIVE_SOLO_LIMIT = 3;
    public static final int ROYAL_BEER_LIMIT = 5;

    private static final Gson GSON = new GsonBuilder().setLenient().create();

    public PerksManager() {}

    public static class PerkDefinition {
        public String id;
        public String name;
        public String texture_path;
        public String description_fr;
        public String description_en;
        public String skin_texture;
        public String sign_texture;
        public String effect_id;
        public int duration = Integer.MAX_VALUE;
        public int amplifier = 0;
    }

    public static class Perk {
        private final PerkDefinition def;

        public Perk(PerkDefinition def) {
            this.def = def;
        }

        public String getId() { return def.id; }
        public String getName() { return def.name; }
        public String getTexturePath() { return def.texture_path; }
        public Component getDescription() { return getTranslatedComponent(null, def.description_fr, def.description_en); }
        public String getSkinTexture() { return def.skin_texture; }
        public String getSignTexture() { return def.sign_texture; }
        
        public MobEffect getAssociatedEffect() {
            if (def.effect_id == null || def.effect_id.isEmpty()) return null;
            return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(def.effect_id));
        }

        public void applyEffect(Player player) {
            var mobEffect = getAssociatedEffect();
            if (mobEffect == null) return;

            if (!player.hasEffect(mobEffect)) {
                player.addEffect(new MobEffectInstance(mobEffect, def.duration, def.amplifier, false, false, true));
            }
        }
    }

    private static boolean isEnglishClient(Player player) {
        if (FMLLoader.getDist().isClient()) {
            return isEnglishClientSide();
        }
        return true; 
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean isEnglishClientSide() {
        if (net.minecraft.client.Minecraft.getInstance() == null) return false;
        return net.minecraft.client.Minecraft.getInstance().options.languageCode.startsWith("en");
    }

    private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        return Component.literal(frenchMessage); 
    }

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(PerksManager::loadPerks);
    }

    public static void loadPerks() {
        ALL_PERKS.clear();
        String[] builtinPerks = {"mastodonte", "speed_cola", "double_tape", "royal_beer", "blood_rage", "phd_flopper", "cherry", "quick_revive", "vulture"};

        for (String perkId : builtinPerks) {
            String path = "data/zombierool/gameplay/perks/" + perkId + ".json";
            try (InputStream stream = PerksManager.class.getClassLoader().getResourceAsStream(path)) {
                if (stream != null) {
                    try (InputStreamReader reader = new InputStreamReader(stream)) {
                        PerkDefinition def = GSON.fromJson(reader, PerkDefinition.class);
                        if (def.id == null) def.id = perkId;
                        ALL_PERKS.put(def.id, new Perk(def));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        File externalFolder = FMLPaths.GAMEDIR.get().resolve("data/zombierool/gameplay/perks/").toFile();
        if (externalFolder.exists() && externalFolder.isDirectory()) {
            File[] files = externalFolder.listFiles((dir, name) -> name.endsWith(".json"));
            if (files != null) {
                for (File file : files) {
                    try (FileReader reader = new FileReader(file)) {
                        PerkDefinition def = GSON.fromJson(reader, PerkDefinition.class);
                        if (def.id == null) def.id = file.getName().replace(".json", "");
                        ALL_PERKS.put(def.id, new Perk(def));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public static MobEffect getEffectInstance(String perkId) {
        Perk perk = ALL_PERKS.get(perkId);
        if (perk != null && perk.def.effect_id != null) {
            return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation(perk.def.effect_id));
        }
        return null;
    }

    public static int getPerkCount(Player player) {
        int count = 0;
        for (Perk perk : ALL_PERKS.values()) {
            MobEffect associatedEffect = getEffectInstance(perk.getId());
            if (associatedEffect != null && player.hasEffect(associatedEffect)) {
                count++;
            }
        }
        return count;
    }

    public static int getQuickRevivePurchases(Player player) {
        return playerQuickRevivePurchases.getOrDefault(player.getUUID(), 0);
    }

    public static void incrementQuickRevivePurchases(Player player) {
        playerQuickRevivePurchases.put(player.getUUID(), getQuickRevivePurchases(player) + 1);
    }

    public static void resetQuickRevivePurchases(Player player) {
        playerQuickRevivePurchases.put(player.getUUID(), 0);
    }

    public static int getRoyalBeerPurchases(Player player) {
        return playerRoyalBeerPurchases.getOrDefault(player.getUUID(), 0);
    }

    public static void incrementRoyalBeerPurchases(Player player) {
        playerRoyalBeerPurchases.put(player.getUUID(), getRoyalBeerPurchases(player) + 1);
    }

    public static void resetRoyalBeerPurchases(Player player) {
        playerRoyalBeerPurchases.put(player.getUUID(), 0);
    }

    public static boolean isPerkLimited(String perkId, Player player) {
        if ("quick_revive".equals(perkId)) {
            return player.level().players().size() == 1;
        }
        if ("royal_beer".equals(perkId)) {
            return true;
        }
        return false;
    }

    public static int getPerkLimit(String perkId, Player player) {
        if ("quick_revive".equals(perkId) && player.level().players().size() == 1) {
            return QUICK_REVIVE_SOLO_LIMIT;
        }
        if ("royal_beer".equals(perkId)) {
            return ROYAL_BEER_LIMIT;
        }
        return Integer.MAX_VALUE;
    }

    public static int getCurrentPerkPurchases(String perkId, Player player) {
        if ("quick_revive".equals(perkId)) {
            return getQuickRevivePurchases(player);
        }
        if ("royal_beer".equals(perkId)) {
            return getRoyalBeerPurchases(player);
        }
        return 0;
    }

    public static void incrementPerkPurchases(String perkId, Player player) {
        if ("quick_revive".equals(perkId)) {
            incrementQuickRevivePurchases(player);
        }
        if ("royal_beer".equals(perkId)) {
            incrementRoyalBeerPurchases(player);
        }
    }

    @Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.FORGE)
    private static class ForgeBusEvents {
        @SubscribeEvent
        public static void serverLoad(ServerStartingEvent event) {}

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void clientLoad(FMLClientSetupEvent event) {}

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            UUID playerUUID = event.getEntity().getUUID();
            playerQuickRevivePurchases.remove(playerUUID);
            playerRoyalBeerPurchases.remove(playerUUID);
        }
    }
}