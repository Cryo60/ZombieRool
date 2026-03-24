package me.cryo.zombierool;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.cryo.zombierool.core.capability.ZombieCapabilitySystem;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent; 
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

@Mod.EventBusSubscriber(bus = Mod.EventBusSubscriber.Bus.MOD)
public class PerksManager {
    public static final Map<String, Perk> ALL_PERKS = new TreeMap<>();
    public static final int MAX_PERKS_LIMIT = 4;
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
        public String icon_path; 
        public int duration = Integer.MAX_VALUE;
        public int amplifier = 0;
    }

    public static class Perk {
        private final PerkDefinition def;

        public Perk(PerkDefinition def) {
            this.def = def;
        }

        public String getId() { return def.id; }
        
        public Component getNameComponent() {
            return Component.translatable("perk.zombierool." + def.id + ".name");
        }
        
        public String getName() { 
            return getNameComponent().getString(); 
        }
        
        public String getTexturePath() { return def.texture_path; }
        
        public Component getDescription() { 
            return Component.translatable("perk.zombierool." + def.id + ".desc"); 
        }
        
        public String getSkinTexture() { return def.skin_texture; }
        public String getSignTexture() { return def.sign_texture; }
        
        public String getIconPath() {
            if (def.icon_path != null && !def.icon_path.isEmpty()) return def.icon_path;
            return "zombierool:textures/mob_effect/perks_effect_" + getId() + ".png";
        }
        
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

    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(PerksManager::loadPerks);
    }

    public static void loadPerks() {
        ALL_PERKS.clear();
        
        try {
            Path basePath = ModList.get().getModFileById(ZombieroolMod.MODID).getFile().findResource("data", ZombieroolMod.MODID, "gameplay", "perks");
            if (Files.exists(basePath)) {
                try (Stream<Path> paths = Files.walk(basePath, 1)) {
                    paths.filter(p -> p.toString().endsWith(".json")).forEach(p -> {
                        try (InputStream is = Files.newInputStream(p);
                             InputStreamReader reader = new InputStreamReader(is)) {
                            PerkDefinition def = GSON.fromJson(reader, PerkDefinition.class);
                            if (def.id == null) def.id = p.getFileName().toString().replace(".json", "");
                            ALL_PERKS.put(def.id, new Perk(def));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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

    public static boolean hasPerk(Player player, String perkId) {
        if (getCurrentPerkPurchases(perkId, player) > 0) return true;
        MobEffect effect = getEffectInstance(perkId);
        return effect != null && player.hasEffect(effect);
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
        return getCurrentPerkPurchases("quick_revive", player);
    }

    public static void incrementQuickRevivePurchases(Player player) {
        incrementPerkPurchases("quick_revive", player);
    }

    public static int getRoyalBeerPurchases(Player player) {
        return getCurrentPerkPurchases("royal_beer", player);
    }

    public static void incrementRoyalBeerPurchases(Player player) {
        incrementPerkPurchases("royal_beer", player);
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
        return player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA)
            .map(cap -> cap.getPerkPurchases(perkId)).orElse(0);
    }

    public static void incrementPerkPurchases(String perkId, Player player) {
        player.getCapability(ZombieCapabilitySystem.Provider.PLAYER_DATA).ifPresent(cap -> {
            cap.incrementPerkPurchases(perkId);
            if (player instanceof ServerPlayer sp) cap.sync(sp);
        });
    }
}