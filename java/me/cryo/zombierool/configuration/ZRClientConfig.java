package me.cryo.zombierool.configuration;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
public class ZRClientConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<HalloweenMode> HALLOWEEN_MODE;
    public static final ForgeConfigSpec.BooleanValue PREFER_ZR_WEAPONS;
    public static final ForgeConfigSpec.BooleanValue ANIMATE_WEAPON_PREVIEW;
    public static final ForgeConfigSpec.BooleanValue HIDE_XP_NOTIFICATIONS;
    public static final ForgeConfigSpec.BooleanValue REDUCED_GORE;
    public static final ForgeConfigSpec.BooleanValue SHOW_ENEMIES_REMAINING;
    public static final ForgeConfigSpec.BooleanValue ALLOW_NETWORK_REQUESTS;
    public static final ForgeConfigSpec.BooleanValue HAS_ANSWERED_NETWORK_PROMPT;
    public static final ForgeConfigSpec.BooleanValue DISCORD_PRESENCE;
    public static final ForgeConfigSpec.ConfigValue<String> DISCORD_APPLICATION_ID;
    public enum HalloweenMode {
        AUTO,      
        FORCE_ON,  
        FORCE_OFF  
    }
    static {
        BUILDER.push("ZombieRool Client Settings");
        HALLOWEEN_MODE = BUILDER
            .comment("Halloween visual mode (client only):",
                     "AUTO - Active only during Halloween period (Oct 20 - Nov 5)",
                     "FORCE_ON - Always active regardless of date",
                     "FORCE_OFF - Always disabled, even during Halloween period")
            .translation("zombierool.config.halloween_mode")
            .defineEnum("halloweenMode", HalloweenMode.AUTO);
        PREFER_ZR_WEAPONS = BUILDER
            .comment("Prefer Classic ZombieRool weapons over TacZ weapons.")
            .translation("zombierool.config.prefer_zr_weapons")
            .define("preferZrWeapons", false);
        ANIMATE_WEAPON_PREVIEW = BUILDER
            .comment("Animate weapon rotation in the Career arsenal preview.")
            .translation("zombierool.config.animate_weapon_preview")
            .define("animateWeaponPreview", true);
        HIDE_XP_NOTIFICATIONS = BUILDER
            .comment("Hide XP gain notifications in Career Screen / HUD.")
            .translation("zombierool.config.hide_xp")
            .define("hideXpNotifications", false);
        SHOW_ENEMIES_REMAINING = BUILDER.comment("Show remaining wave enemies beside the hotbar.").define("showEnemiesRemaining",false);
        REDUCED_GORE = BUILDER
            .comment("Drastically reduces blood, dismemberment and corpses.")
            .translation("zombierool.config.gore")
            .define("reducedGore", false);
        ALLOW_NETWORK_REQUESTS = BUILDER
            .comment("Allow ZombieRool to make external HTTP requests (Map updates, Redeem codes, etc).")
            .translation("zombierool.config.allow_network")
            .define("allowNetworkRequests", false);
        HAS_ANSWERED_NETWORK_PROMPT = BUILDER
            .comment("Internal flag. True if the user has seen the network prompt.")
            .define("hasAnsweredNetworkPrompt", false);
        DISCORD_PRESENCE = BUILDER
            .comment("Show the current map, round and player count on Discord.")
            .define("discordPresence", true);
        DISCORD_APPLICATION_ID = BUILDER
            .comment("Discord application ID. Create an application named ZombieRool at",
                     "https://discord.com/developers/applications and paste its Application ID here.",
                     "Empty disables Rich Presence. The name shown on Discord is the application name.")
            .define("discordApplicationId", "1485989369902006352");
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "zombierool-client.toml");
    }
    public static boolean isLoaded() {
        return SPEC.isLoaded();
    }

    public static HalloweenMode getHalloweenMode() {
        return SPEC.isLoaded() ? HALLOWEEN_MODE.get() : HalloweenMode.AUTO;
    }
    public static void setHalloweenMode(HalloweenMode mode) {
        if (HALLOWEEN_MODE.get() != mode) {
            HALLOWEEN_MODE.set(mode);
            SPEC.save();
        }
    }
    public static boolean prefersZrWeapons() { return PREFER_ZR_WEAPONS.get(); }
    public static void setPreferZrWeapons(boolean prefer) {
        if (PREFER_ZR_WEAPONS.get() != prefer) {
            PREFER_ZR_WEAPONS.set(prefer);
            SPEC.save();
        }
    }
    public static boolean animateWeaponPreview() { return ANIMATE_WEAPON_PREVIEW.get(); }
    public static void setAnimateWeaponPreview(boolean animate) {
        if (ANIMATE_WEAPON_PREVIEW.get() != animate) {
            ANIMATE_WEAPON_PREVIEW.set(animate);
            SPEC.save();
        }
    }
    public static boolean hideXpNotifications() { return HIDE_XP_NOTIFICATIONS.get(); }
    public static void setHideXpNotifications(boolean hide) {
        if (HIDE_XP_NOTIFICATIONS.get() != hide) {
            HIDE_XP_NOTIFICATIONS.set(hide);
            SPEC.save();
        }
    }
    public static boolean showEnemiesRemaining(){return SPEC.isLoaded()&&SHOW_ENEMIES_REMAINING.get();}
    public static void setShowEnemiesRemaining(boolean show){SHOW_ENEMIES_REMAINING.set(show);SPEC.save();}
    public static boolean isGoreReduced() {
        return SPEC.isLoaded() && REDUCED_GORE.get();
    }
    public static void setGoreReduced(boolean reduced) {
        if (REDUCED_GORE.get() != reduced) {
            REDUCED_GORE.set(reduced);
            SPEC.save();
        }
    }
    public static boolean allowNetworkRequests() { return ALLOW_NETWORK_REQUESTS.get(); }
    public static void setAllowNetworkRequests(boolean allow) {
        ALLOW_NETWORK_REQUESTS.set(allow);
        SPEC.save();
    }
    public static boolean hasAnsweredNetworkPrompt() { return HAS_ANSWERED_NETWORK_PROMPT.get(); }
    public static void setHasAnsweredNetworkPrompt(boolean answered) {
        HAS_ANSWERED_NETWORK_PROMPT.set(answered);
        SPEC.save();
    }
    public static boolean discordPresence() {
        return !SPEC.isLoaded() || DISCORD_PRESENCE.get();
    }
    public static String discordApplicationId() {
        return SPEC.isLoaded() ? DISCORD_APPLICATION_ID.get() : "1485989369902006352";
    }
}