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
    public static final ForgeConfigSpec.BooleanValue ALLOW_NETWORK_REQUESTS;
    public static final ForgeConfigSpec.BooleanValue HAS_ANSWERED_NETWORK_PROMPT;
    public enum HalloweenMode {
        AUTO,      
        FORCE_ON,  
        FORCE_OFF  
    }
    static {
        BUILDER.push("ZombieRool Client Settings");
        HALLOWEEN_MODE = BUILDER
            .comment("Halloween mode control (Server Operators only):",
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
        ALLOW_NETWORK_REQUESTS = BUILDER
            .comment("Allow ZombieRool to make external HTTP requests (Map updates, Redeem codes, etc).")
            .translation("zombierool.config.allow_network")
            .define("allowNetworkRequests", false);
        HAS_ANSWERED_NETWORK_PROMPT = BUILDER
            .comment("Internal flag. True if the user has seen the network prompt.")
            .define("hasAnsweredNetworkPrompt", false);
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    public static void register() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SPEC, "zombierool-client.toml");
    }
    public static HalloweenMode getHalloweenMode() { return HALLOWEEN_MODE.get(); }
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
}