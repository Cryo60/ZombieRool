package net.mcreator.zombierool;

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
import net.minecraft.network.chat.MutableComponent; // Import for MutableComponent
import net.minecraft.world.level.Level; 
import net.minecraftforge.event.entity.player.PlayerEvent;

import net.mcreator.zombierool.potion.PerksEffectPHDFlopperMobEffect;
import net.mcreator.zombierool.potion.PerksEffectCherryMobEffect;
import net.mcreator.zombierool.potion.PerksEffectMastodonteMobEffect;
import net.mcreator.zombierool.potion.PerksEffectSpeedColaMobEffect;
import net.mcreator.zombierool.potion.PerksEffectDoubleTapeMobEffect;
import net.mcreator.zombierool.potion.PerksEffectBloodRageMobEffect;
import net.mcreator.zombierool.potion.PerksEffectQuickReviveMobEffect;
import net.mcreator.zombierool.potion.PerksEffectRoyalBeerMobEffect;
import net.mcreator.zombierool.potion.PerksEffectVultureMobEffect;


import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
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


    public static final Map<String, Perk> ALL_PERKS = new HashMap<>();
    private static final UUID SPEED_COLA_SPEED_UUID = UUID.fromString("6a29e4b6-a66c-4f7d-b8d4-5b4a7d3c0b1f");

    public static final int MAX_PERKS_LIMIT = 4;
    private static final Map<UUID, Integer> playerQuickRevivePurchases = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> playerRoyalBeerPurchases = new ConcurrentHashMap<>();

    public static final int QUICK_REVIVE_SOLO_LIMIT = 3;
    public static final int ROYAL_BEER_LIMIT = 5;

    public PerksManager() {}

    // Helper method to check if the client's language is English
    // For static contexts like Perk definitions, we cannot use a specific player.
    // This will default to English based on the current helper implementation (always returns true).
    private static boolean isEnglishClient(Player player) {
        // This is a simplified check for server-side. In a real scenario, you'd need client-server sync
        // to pass the player's language preference to the server.
        // For now, we'll assume English if a player context is available.
        // If player is null, we default to English for static Component creation.
        return true; 
    }

    // Helper method for dynamic translation
    private static MutableComponent getTranslatedComponent(Player player, String frenchMessage, String englishMessage) {
        if (player != null && isEnglishClient(player)) {
            return Component.literal(englishMessage);
        }
        // If player is null, or not English client (based on placeholder), return French.
        // For static definitions, 'player' will be null, so it will always return English based on isEnglishClient(null) returning true.
        return Component.literal(englishMessage); // Changed to return English for static definitions if isEnglishClient is true.
    }


    @SubscribeEvent
    public static void init(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ALL_PERKS.put("mastodonte", new Perk("mastodonte", "Juggernog", "zombierool:textures/block/masto_perks_lower_texture.png",
                getTranslatedComponent(null, "Résiste à la horde, +4 coeurs de vie en plus.", "Resist the horde, +4 extra hearts."), player -> {})); // Added player -> {}

            ALL_PERKS.put("speed_cola", new Perk("speed_cola", "Speed Cola", "zombierool:textures/block/speedcola_perks_lower_texture.png",
                getTranslatedComponent(null, "Recharge rapide et +20% en vitesse.", "Fast reload and +20% speed."), player -> {})); // Added player -> {}

            ALL_PERKS.put("double_tape", new Perk("double_tape", "Double Tape", "zombierool:textures/block/dt_perks_lower_texture.png",
                getTranslatedComponent(null, "Chaque tir inflige deux fois plus.", "Each shot deals double damage."), player -> {})); // Added player -> {}

            ALL_PERKS.put("royal_beer", new Perk("royal_beer", "Royal Beer", "zombierool:textures/block/royalbeer_perks_lower_texture.png",
                getTranslatedComponent(null, "Invoque un Chevalier pour vous protéger pendant 3 minutes.", "Summon a Knight to protect you for 3 minutes."), player -> {})); // Added player -> {}

            ALL_PERKS.put("blood_rage", new Perk("blood_rage", "Blood Rage", "zombierool:textures/block/blood_rage_perks_lower_texture.png",
                getTranslatedComponent(null, "Quand vous faites des dégâts, vous avez une chance de vous régénérer et d'obtenir des coeurs d'absorption.", "When you deal damage, you have a chance to regenerate and gain absorption hearts."), player -> {})); // Added player -> {}

            ALL_PERKS.put("phd_flopper", new Perk("phd_flopper", "PHD Flopper", "zombierool:textures/block/phd_flopper_perks_lower_texture.png",
                getTranslatedComponent(null, "Résiste aux dégâts de chute, d'explosion et de feu. Les chutes créent une explosion sans briser de blocs.", "Resist fall, explosion, and fire damage. Falls create an explosion without breaking blocks."), player -> {})); // Added player -> {}

            ALL_PERKS.put("cherry", new Perk("cherry", "Cherry", "zombierool:textures/block/cherry_perks_lower_texture.png",
                getTranslatedComponent(null, "Produit une décharge éléctrique autour du joueur lorsqu'il recharge son arme", "Produces an electrical discharge around the player when reloading their weapon."), player -> {})); // Added player -> {}

            ALL_PERKS.put("quick_revive", new Perk("quick_revive", "Quick Revive", "zombierool:textures/block/quick_revive_perks_lower_texture.png",
                getTranslatedComponent(null, "Se ranime plus vite.", "Revives faster."), player -> {})); // Added player -> {}
            
            ALL_PERKS.put("vulture", new Perk("vulture", "Vulture Aid", "zombierool:textures/block/vulture_perks_lower_texture.png",
                getTranslatedComponent(null, "Donne des points extra, et a plus de chance de loot certains bonus.", "Gives extra points, and has a higher chance to loot certain bonuses."), player -> {})); // Added player -> {}
        });
    }

    public static class Perk {
        private final String id;
        private final String name;
        private final String texturePath;
        private final Component description; // Keep as Component, as it's already a MutableComponent from getTranslatedComponent
        private final Consumer<Player> effectConsumer;

        public Perk(String id, String name, String texturePath, Component description, Consumer<Player> effectConsumer) {
            this.id = id;
            this.name = name;
            this.texturePath = texturePath;
            this.description = description;
            this.effectConsumer = effectConsumer;
        }

        public String getId() { return id; }
        public String getName() { return name; }
        public String getTexturePath() { return texturePath; }
        public Component getDescription() { return description; }

        public MobEffect getAssociatedEffect() {
            return PerksManager.getEffectInstance(this.id);
        }

        public void applyEffect(Player player) {
            var mobEffect = getAssociatedEffect();
            if (mobEffect == null) {
                return;
            }

            if (!player.hasEffect(mobEffect)) {
                int duration = Integer.MAX_VALUE;
                if (this.id.equals("royal_beer")) duration = 20 * 60 * 3;

                player.addEffect(new MobEffectInstance(mobEffect, duration, 0, false, false, true));
            }

            if (effectConsumer != null) effectConsumer.accept(player);
        }
    }

    public static MobEffect getEffectInstance(String perkId) {
        switch (perkId) {
            case "mastodonte": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_mastodonte"));
            case "speed_cola": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_speed_cola"));
            case "double_tape": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_double_tape"));
            case "royal_beer": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_royal_beer"));
            case "blood_rage": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_blood_rage"));
            case "phd_flopper": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_phd_flopper"));
            case "cherry": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_cherry"));
            case "quick_revive": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_quick_revive"));
            case "vulture": return ForgeRegistries.MOB_EFFECTS.getValue(new ResourceLocation("zombierool:perks_effect_vulture"));
            default: return null;
        }
    }

    public static int getPerkCount(Player player) {
        int count = 0;
        for (Perk perk : ALL_PERKS.values()) {
            MobEffect associatedEffect = PerksManager.getEffectInstance(perk.getId());
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
        public static void serverLoad(ServerStartingEvent event) {
            // It's generally better to clear per-player data on player logout,
            // but you could clear all data here if you want a complete server-side reset on startup.
            // For now, we'll rely on player logout for per-player data.
        }

        @OnlyIn(Dist.CLIENT)
        @SubscribeEvent
        public static void clientLoad(FMLClientSetupEvent event) {}

        @SubscribeEvent
        public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
            UUID playerUUID = event.getEntity().getUUID();
            playerQuickRevivePurchases.remove(playerUUID);
            playerRoyalBeerPurchases.remove(playerUUID);
            // Optionally, remove other player-specific data that should reset per game
        }
    }
}
